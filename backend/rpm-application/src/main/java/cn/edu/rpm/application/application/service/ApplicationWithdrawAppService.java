package cn.edu.rpm.application.application.service;

import cn.edu.rpm.application.application.dto.ApplicationWithdrawal;
import cn.edu.rpm.application.application.dto.WithdrawApplicationCmd;
import cn.edu.rpm.application.domain.model.ApplicationPermissions;
import cn.edu.rpm.application.domain.port.ApplicationApprovalWorkflowPort;
import cn.edu.rpm.application.domain.port.ApplicationWorkflowLauncher;
import cn.edu.rpm.application.domain.port.WorkflowLaunch;
import cn.edu.rpm.application.infrastructure.entity.ApplicationEntity;
import cn.edu.rpm.application.infrastructure.repository.ApplicationRepository;
import cn.edu.rpm.audit.domain.model.AuditEvent;
import cn.edu.rpm.audit.domain.service.AuditRecorder;
import cn.edu.rpm.common.domain.model.OutboxEvent;
import cn.edu.rpm.common.domain.port.OutboxAppendPort;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.common.util.TraceIds;
import cn.edu.rpm.common.util.UuidUtils;
import cn.edu.rpm.iam.application.dto.Action;
import cn.edu.rpm.iam.application.dto.AuthContext;
import cn.edu.rpm.iam.application.dto.AuthDecision;
import cn.edu.rpm.iam.application.dto.ResourceRef;
import cn.edu.rpm.iam.domain.model.ResourceTypes;
import cn.edu.rpm.iam.domain.service.AuthorizationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 申报撤回应用服务（T-APP-018；技术文档 04 §2.2、07 §2.3）。
 *
 * <p>仅 {@code IN_REVIEW} 申报可撤回：申报置 {@code WITHDRAWN}，审批实例取消，
 * 已提交版本内容保持不变（不做 version 更新）。撤回为申请人本人动作，
 * 且仍需通过 {@code application:withdraw} 功能权限判定。</p>
 *
 * <p>顺序：行锁 → 授权 → 申请人校验 → 状态校验 → 取消审批实例 → 状态迁移 →
 * 乐观锁更新 → outbox + 审计。任一步失败整体回滚，避免「实例已取消但申报仍在审批」。</p>
 */
@Service
public class ApplicationWithdrawAppService {

    /** 审计动作：申报撤回。 */
    public static final String AUDIT_WITHDRAWN = "APPLICATION_WITHDRAWN";

    private static final String OBJECT_APPLICATION = "APPLICATION";

    private final ApplicationRepository applicationRepository;

    private final ApplicationWorkflowLauncher workflowLauncher;

    private final ApplicationApprovalWorkflowPort approvalWorkflowPort;

    private final OutboxAppendPort outboxAppendPort;

    private final AuthorizationService authorizationService;

    private final AuditRecorder auditRecorder;

    private final Clock clock;

    /**
     * 构造撤回服务。
     *
     * @param applicationRepository 申报主档仓储
     * @param workflowLauncher      审批启动端口（定位当前实例）
     * @param approvalWorkflowPort  审批任务操作端口（取消实例）
     * @param outboxAppendPort      Outbox 追加端口
     * @param authorizationService  统一授权服务
     * @param auditRecorder         审计记录器
     * @param clock                 时间基准
     */
    public ApplicationWithdrawAppService(ApplicationRepository applicationRepository,
                                         ApplicationWorkflowLauncher workflowLauncher,
                                         ApplicationApprovalWorkflowPort approvalWorkflowPort,
                                         OutboxAppendPort outboxAppendPort,
                                         AuthorizationService authorizationService,
                                         AuditRecorder auditRecorder,
                                         Clock clock) {
        this.applicationRepository = Objects.requireNonNull(applicationRepository,
                "applicationRepository 不能为空");
        this.workflowLauncher = Objects.requireNonNull(workflowLauncher, "workflowLauncher 不能为空");
        this.approvalWorkflowPort = Objects.requireNonNull(approvalWorkflowPort,
                "approvalWorkflowPort 不能为空");
        this.outboxAppendPort = Objects.requireNonNull(outboxAppendPort, "outboxAppendPort 不能为空");
        this.authorizationService = Objects.requireNonNull(authorizationService,
                "authorizationService 不能为空");
        this.auditRecorder = Objects.requireNonNull(auditRecorder, "auditRecorder 不能为空");
        this.clock = Objects.requireNonNull(clock, "clock 不能为空");
    }

    /**
     * 撤回申报（TC-APP-036 / TC-APP-037）。
     *
     * @param applicationId 申报 ID
     * @param cmd           撤回命令
     * @param auth          当前请求授权上下文
     * @return 撤回后申报与实例状态
     * @throws BizException 参数非法 {@code RPM-COMMON-400}；非申请人/无权限
     *                      {@code RPM-COMMON-403}；不存在 {@code RPM-COMMON-404}；
     *                      状态不允许 {@code RPM-APPLY-004}；并发冲突 {@code RPM-COMMON-409}
     */
    @Transactional
    public ApplicationWithdrawal withdraw(String applicationId, WithdrawApplicationCmd cmd,
                                          AuthContext auth) {
        Objects.requireNonNull(cmd, "cmd 不能为空");
        Objects.requireNonNull(auth, "auth 不能为空");
        if (cmd.requestId() == null) {
            throw new BizException(ErrorCode.COMMON_400, "缺少 request_id");
        }

        ApplicationEntity application = applicationRepository.selectForUpdate(applicationId)
                .orElseThrow(() -> new BizException(ErrorCode.COMMON_404, "申报不存在"));
        requirePermission(application, auth);
        if (!UuidUtils.sameId(application.getApplicantId(), auth.userId())) {
            throw new BizException(ErrorCode.COMMON_403, "仅申请人可撤回申报");
        }
        if (!ApplicationEntity.STATUS_IN_REVIEW.equals(application.getStatus())) {
            throw new BizException(ErrorCode.APPLY_004, "当前状态不允许撤回");
        }

        String versionId = application.getCurrentVersionId();
        String instanceStatus = null;
        if (versionId != null) {
            Optional<WorkflowLaunch> launch = workflowLauncher.findLatest(
                    UuidUtils.parse(application.getId()), UuidUtils.parse(versionId));
            if (launch.isPresent()) {
                approvalWorkflowPort.cancel(launch.get().instanceId(), auth.userId(),
                        reason(cmd), cmd.requestId());
                instanceStatus = "CANCELLED";
            }
        }

        application.markWithdrawn();
        if (applicationRepository.update(application) == 0) {
            throw new BizException(ErrorCode.COMMON_409, "申报已被并发修改，请刷新后重试");
        }
        appendOutbox(application);
        auditRecorder.record(new AuditEvent(
                TraceIds.currentOrUntracked(),
                auth.userId(),
                null,
                AUDIT_WITHDRAWN,
                OBJECT_APPLICATION,
                UuidUtils.parse(application.getId()),
                application.getVersion(),
                null,
                null,
                reason(cmd),
                null,
                null,
                OffsetDateTime.now(clock).toInstant()));
        return new ApplicationWithdrawal(application.getId(), application.getStatus(), instanceStatus);
    }

    private void appendOutbox(ApplicationEntity application) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("application_id", application.getId());
        payload.put("application_no", application.getApplicationNo());
        payload.put("version_id", application.getCurrentVersionId());
        payload.put("status", application.getStatus());
        outboxAppendPort.append(new OutboxEvent(
                UUID.randomUUID(),
                OBJECT_APPLICATION,
                UuidUtils.parse(application.getId()),
                "APPLICATION_WITHDRAWN",
                payload,
                TraceIds.currentOrUntracked(),
                "APPLICATION_WITHDRAWN:" + application.getId()));
    }

    private void requirePermission(ApplicationEntity application, AuthContext auth) {
        AuthDecision decision = authorizationService.check(auth,
                Action.of(ApplicationPermissions.WITHDRAW),
                new ResourceRef(ResourceTypes.PROJECT,
                        UuidUtils.parse(application.getId()),
                        parseOptionalUuid(application.getApplicantId()),
                        parseOptionalUuid(application.getDeptId()),
                        Set.of()));
        if (!decision.allowed()) {
            throw new BizException(ErrorCode.COMMON_403);
        }
    }

    private static String reason(WithdrawApplicationCmd cmd) {
        return cmd.reason() == null || cmd.reason().isBlank() ? "申请人撤回申报" : cmd.reason().trim();
    }

    private static UUID parseOptionalUuid(String value) {
        return value == null || value.isBlank() ? null : UuidUtils.parse(value);
    }
}
