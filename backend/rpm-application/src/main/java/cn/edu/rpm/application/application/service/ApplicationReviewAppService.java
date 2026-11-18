package cn.edu.rpm.application.application.service;

import cn.edu.rpm.application.application.dto.FormalReviewCmd;
import cn.edu.rpm.application.application.dto.FormalReviewResult;
import cn.edu.rpm.application.domain.model.ApplicationPermissions;
import cn.edu.rpm.application.domain.port.ApplicationApprovalWorkflowPort;
import cn.edu.rpm.application.domain.port.ApplicationWorkflowLauncher;
import cn.edu.rpm.application.domain.port.WorkflowLaunch;
import cn.edu.rpm.application.infrastructure.entity.ApplicationEntity;
import cn.edu.rpm.application.infrastructure.entity.ApplicationReviewEntity;
import cn.edu.rpm.application.infrastructure.repository.ApplicationRepository;
import cn.edu.rpm.application.infrastructure.repository.ApplicationReviewRepository;
import cn.edu.rpm.audit.domain.model.AuditEvent;
import cn.edu.rpm.audit.domain.service.AuditRecorder;
import cn.edu.rpm.common.domain.model.OutboxEvent;
import cn.edu.rpm.common.domain.port.OutboxAppendPort;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.common.util.CanonicalJsonHasher;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 形式审查应用服务（T-APP-017；技术文档 04 §3.3、08 §9.1）。
 *
 * <p>审查动作与状态推进：</p>
 * <ul>
 *   <li>{@code RETURN}：校验问题项非空，写 {@code application_review(FORMAL/RETURN)}，
 *       申报置 {@code SUPPLEMENT}，携带 {@code taskId} 时完成当前任务为 {@code RETURNED}；</li>
 *   <li>{@code PASS}：写审查记录，完成当前任务（{@code APPROVED}），申报版本内容不变；</li>
 *   <li>{@code NOT_ACCEPT}：理由必填 ≥10 字，申报置终态 {@code NOT_ACCEPTED}，
 *       取消审批实例（TC-APP-101）。</li>
 * </ul>
 *
 * <p>授权：应用服务入口统一调用 {@code AuthorizationService.check(application:formal-review)}；
 * 申请人本人审查返回 {@code RPM-WF-003}。版本必须是当前提交版本，否则
 * {@code RPM-WF-005}。所有动作都不修改已提交版本内容。</p>
 */
@Service
public class ApplicationReviewAppService {

    /** 审查结论：通过。 */
    public static final String DECISION_PASS = "PASS";

    /** 审查结论：退回补正。 */
    public static final String DECISION_RETURN = "RETURN";

    /** 审查结论：不予受理。 */
    public static final String DECISION_NOT_ACCEPT = "NOT_ACCEPT";

    /** 审计动作：形式审查通过。 */
    public static final String AUDIT_PASSED = "APPLICATION_FORMAL_REVIEW_PASSED";

    /** 审计动作：形式审查退回。 */
    public static final String AUDIT_RETURNED = "APPLICATION_FORMAL_REVIEW_RETURNED";

    /** 审计动作：形式审查不予受理。 */
    public static final String AUDIT_NOT_ACCEPTED = "APPLICATION_NOT_ACCEPTED";

    private static final String OBJECT_APPLICATION = "APPLICATION";

    private static final int NOT_ACCEPT_COMMENT_MIN_LENGTH = 10;

    private final ApplicationRepository applicationRepository;

    private final ApplicationReviewRepository applicationReviewRepository;

    private final ApplicationWorkflowLauncher workflowLauncher;

    private final ApplicationApprovalWorkflowPort approvalWorkflowPort;

    private final OutboxAppendPort outboxAppendPort;

    private final AuthorizationService authorizationService;

    private final AuditRecorder auditRecorder;

    private final CanonicalJsonHasher jsonHasher;

    private final Clock clock;

    /**
     * 构造形式审查服务。
     *
     * @param applicationRepository       申报主档仓储
     * @param applicationReviewRepository 审查意见仓储
     * @param workflowLauncher            审批启动端口（读取当前实例做撤回）
     * @param approvalWorkflowPort        审批任务操作端口
     * @param outboxAppendPort            Outbox 追加端口
     * @param authorizationService        统一授权服务
     * @param auditRecorder               审计记录器
     * @param jsonHasher                  规范化 JSON 工具（问题项序列化）
     * @param clock                       时间基准
     */
    public ApplicationReviewAppService(ApplicationRepository applicationRepository,
                                       ApplicationReviewRepository applicationReviewRepository,
                                       ApplicationWorkflowLauncher workflowLauncher,
                                       ApplicationApprovalWorkflowPort approvalWorkflowPort,
                                       OutboxAppendPort outboxAppendPort,
                                       AuthorizationService authorizationService,
                                       AuditRecorder auditRecorder,
                                       CanonicalJsonHasher jsonHasher,
                                       Clock clock) {
        this.applicationRepository = Objects.requireNonNull(applicationRepository,
                "applicationRepository 不能为空");
        this.applicationReviewRepository = Objects.requireNonNull(applicationReviewRepository,
                "applicationReviewRepository 不能为空");
        this.workflowLauncher = Objects.requireNonNull(workflowLauncher, "workflowLauncher 不能为空");
        this.approvalWorkflowPort = Objects.requireNonNull(approvalWorkflowPort,
                "approvalWorkflowPort 不能为空");
        this.outboxAppendPort = Objects.requireNonNull(outboxAppendPort, "outboxAppendPort 不能为空");
        this.authorizationService = Objects.requireNonNull(authorizationService,
                "authorizationService 不能为空");
        this.auditRecorder = Objects.requireNonNull(auditRecorder, "auditRecorder 不能为空");
        this.jsonHasher = Objects.requireNonNull(jsonHasher, "jsonHasher 不能为空");
        this.clock = Objects.requireNonNull(clock, "clock 不能为空");
    }

    /**
     * 执行形式审查（TC-APP-010 / TC-APP-011 / TC-APP-013 / TC-APP-014 / TC-APP-101）。
     *
     * @param applicationId 申报 ID
     * @param cmd           审查命令
     * @param auth          当前请求授权上下文
     * @return 审查结果（申报状态与问题项数量）
     * @throws BizException 参数非法 {@code RPM-COMMON-400}；无权限 {@code RPM-COMMON-403}；
     *                      申报不存在 {@code RPM-COMMON-404}；状态不允许 {@code RPM-APPLY-004}；
     *                      版本不一致 {@code RPM-WF-005}；自审 {@code RPM-WF-003}
     */
    @Transactional
    public FormalReviewResult formalReview(String applicationId, FormalReviewCmd cmd, AuthContext auth) {
        Objects.requireNonNull(cmd, "cmd 不能为空");
        Objects.requireNonNull(auth, "auth 不能为空");
        String decision = requireDecision(cmd.decision());
        requireIssueRules(decision, cmd);
        String versionId = requireText(cmd.versionId(), "version_id 不能为空");

        ApplicationEntity application = applicationRepository.selectForUpdate(applicationId)
                .orElseThrow(() -> new BizException(ErrorCode.COMMON_404, "申报不存在"));
        requireReviewPermission(application, auth);
        if (UuidUtils.sameId(application.getApplicantId(), auth.userId())) {
            throw new BizException(ErrorCode.WF_003);
        }
        if (!UuidUtils.sameId(application.getCurrentVersionId(), versionId)) {
            throw new BizException(ErrorCode.WF_005, "审查版本不是当前提交版本");
        }
        if (!ApplicationEntity.STATUS_IN_REVIEW.equals(application.getStatus())) {
            throw new BizException(ErrorCode.APPLY_004, "当前状态不允许形式审查");
        }

        ApplicationReviewEntity review = new ApplicationReviewEntity();
        review.setApplicationVersionId(versionId);
        review.setReviewStage(ApplicationReviewEntity.STAGE_FORMAL);
        review.setReviewerId(UuidUtils.formatCompact(auth.userId()));
        review.setDecision(toReviewDecision(decision));
        review.setIssues(toIssuesJson(cmd.issues()));
        review.setComment(cmd.comment());
        review.setReviewedAt(OffsetDateTime.now(clock));
        applicationReviewRepository.insert(review);

        switch (decision) {
            case DECISION_PASS -> handlePass(application, cmd, auth);
            case DECISION_RETURN -> handleReturn(application, cmd, auth);
            default -> handleNotAccept(application, cmd, auth);
        }

        if (applicationRepository.update(application) == 0) {
            throw new BizException(ErrorCode.COMMON_409, "申报已被并发修改，请刷新后重试");
        }
        return new FormalReviewResult(application.getId(), versionId, decision,
                application.getStatus(), cmd.issues().size());
    }

    private void handlePass(ApplicationEntity application, FormalReviewCmd cmd, AuthContext auth) {
        completeTaskIfPresent(application, cmd, auth, true, "形式审查通过");
        audit(auth, application, AUDIT_PASSED, "形式审查通过");
    }

    private void handleReturn(ApplicationEntity application, FormalReviewCmd cmd, AuthContext auth) {
        application.markSupplement();
        completeTaskIfPresent(application, cmd, auth, false, "形式审查退回");
        appendOutbox(application, "APPLICATION_RETURNED", "SUPPLEMENT");
        audit(auth, application, AUDIT_RETURNED, "形式审查退回补正");
    }

    private void handleNotAccept(ApplicationEntity application, FormalReviewCmd cmd, AuthContext auth) {
        application.markNotAccepted();
        if (cmd.requestId() == null) {
            throw new BizException(ErrorCode.COMMON_400, "不予受理必须提供 request_id");
        }
        Optional<WorkflowLaunch> instance = workflowLauncher.findLatest(
                UuidUtils.parse(application.getId()),
                UuidUtils.parse(application.getCurrentVersionId()));
        instance.ifPresent(launch -> approvalWorkflowPort.cancel(launch.instanceId(), auth.userId(),
                "形式审查不予受理：" + nullToEmpty(cmd.comment()), cmd.requestId()));
        appendOutbox(application, "APPLICATION_NOT_ACCEPTED", "NOT_ACCEPTED");
        audit(auth, application, AUDIT_NOT_ACCEPTED, "形式审查不予受理");
    }

    private void completeTaskIfPresent(ApplicationEntity application, FormalReviewCmd cmd,
                                       AuthContext auth, boolean approved, String reason) {
        if (cmd.taskId() == null || cmd.taskId().isBlank()) {
            return;
        }
        if (cmd.expectedTaskVersion() == null) {
            throw new BizException(ErrorCode.COMMON_400, "携带 task_id 时必须提供 expected_task_version");
        }
        if (cmd.requestId() == null) {
            throw new BizException(ErrorCode.COMMON_400, "携带 task_id 时必须提供 request_id");
        }
        WorkflowLaunch launch = workflowLauncher.findLatest(
                        UuidUtils.parse(application.getId()),
                        UuidUtils.parse(application.getCurrentVersionId()))
                .orElseThrow(() -> new BizException(ErrorCode.COMMON_404, "审批实例不存在"));
        approvalWorkflowPort.completeReview(launch.instanceId(), UuidUtils.parse(cmd.taskId()),
                auth.userId(), approved, reason, cmd.expectedTaskVersion(), cmd.requestId());
    }

    private void appendOutbox(ApplicationEntity application, String eventType, String status) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("application_id", application.getId());
        payload.put("application_no", application.getApplicationNo());
        payload.put("version_id", application.getCurrentVersionId());
        payload.put("status", status);
        outboxAppendPort.append(new OutboxEvent(
                UUID.randomUUID(),
                OBJECT_APPLICATION,
                UuidUtils.parse(application.getId()),
                eventType,
                payload,
                TraceIds.currentOrUntracked(),
                eventType + ":" + application.getId() + ":" + application.getCurrentVersionId()));
    }

    private void audit(AuthContext auth, ApplicationEntity application, String action, String reason) {
        auditRecorder.record(new AuditEvent(
                TraceIds.currentOrUntracked(),
                auth.userId(),
                null,
                action,
                OBJECT_APPLICATION,
                UuidUtils.parse(application.getId()),
                application.getVersion(),
                null,
                null,
                reason,
                null,
                null,
                clock.instant()));
    }

    private void requireReviewPermission(ApplicationEntity application, AuthContext auth) {
        AuthDecision decision = authorizationService.check(auth,
                Action.of(ApplicationPermissions.FORMAL_REVIEW),
                new ResourceRef(ResourceTypes.PROJECT,
                        UuidUtils.parse(application.getId()),
                        parseOptionalUuid(application.getApplicantId()),
                        parseOptionalUuid(application.getDeptId()),
                        Set.of()));
        if (!decision.allowed()) {
            throw new BizException(ErrorCode.COMMON_403);
        }
    }

    private static void requireIssueRules(String decision, FormalReviewCmd cmd) {
        if (DECISION_RETURN.equals(decision) && cmd.issues().isEmpty()) {
            throw new BizException(ErrorCode.COMMON_400, "退回补正必须提供问题项");
        }
        for (FormalReviewCmd.Issue issue : cmd.issues()) {
            if (issue == null || issue.field() == null || issue.field().isBlank()) {
                throw new BizException(ErrorCode.COMMON_400, "问题项字段不能为空");
            }
        }
        if (DECISION_NOT_ACCEPT.equals(decision)) {
            String comment = cmd.comment();
            if (comment == null || comment.isBlank()
                    || comment.codePointCount(0, comment.length()) < NOT_ACCEPT_COMMENT_MIN_LENGTH) {
                throw new BizException(ErrorCode.COMMON_400, "不予受理理由不能少于 10 字");
            }
        }
    }

    private String toIssuesJson(List<FormalReviewCmd.Issue> issues) {
        List<Map<String, Object>> rows = new ArrayList<>(issues.size());
        for (FormalReviewCmd.Issue issue : issues) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("field", issue.field());
            row.put("message", issue.message());
            rows.add(row);
        }
        return jsonHasher.canonicalJson(rows);
    }

    private static String toReviewDecision(String decision) {
        return switch (decision) {
            case DECISION_PASS -> ApplicationReviewEntity.DECISION_PASS;
            case DECISION_RETURN -> ApplicationReviewEntity.DECISION_RETURN;
            default -> ApplicationReviewEntity.DECISION_NOT_ACCEPT;
        };
    }

    private static String requireDecision(String decision) {
        if (decision == null || decision.isBlank()) {
            throw new BizException(ErrorCode.COMMON_400, "审查结论不能为空");
        }
        String normalized = decision.trim().toUpperCase(Locale.ROOT);
        if (!DECISION_PASS.equals(normalized) && !DECISION_RETURN.equals(normalized)
                && !DECISION_NOT_ACCEPT.equals(normalized)) {
            throw new BizException(ErrorCode.COMMON_400, "审查结论必须为 PASS / RETURN / NOT_ACCEPT");
        }
        return normalized;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BizException(ErrorCode.COMMON_400, message);
        }
        return value.trim();
    }

    private static UUID parseOptionalUuid(String value) {
        return value == null || value.isBlank() ? null : UuidUtils.parse(value);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
