package cn.edu.rpm.project.application.service;

import cn.edu.rpm.audit.domain.model.AuditEvent;
import cn.edu.rpm.audit.domain.service.AuditRecorder;
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
import cn.edu.rpm.project.application.dto.MilestoneDelayRequestCmd;
import cn.edu.rpm.project.application.dto.MilestoneDelayRequestResult;
import cn.edu.rpm.project.domain.model.ProjectPermissions;
import cn.edu.rpm.project.domain.port.MilestoneDelayWorkflowLauncher;
import cn.edu.rpm.project.domain.port.MilestoneDelayWorkflowStart;
import cn.edu.rpm.project.infrastructure.entity.MilestoneDelayRequestEntity;
import cn.edu.rpm.project.infrastructure.entity.MilestoneEntity;
import cn.edu.rpm.project.infrastructure.entity.ProjectEntity;
import cn.edu.rpm.project.infrastructure.entity.ProjectMemberEntity;
import cn.edu.rpm.project.infrastructure.repository.MilestoneDelayRequestRepository;
import cn.edu.rpm.project.infrastructure.repository.MilestoneRepository;
import cn.edu.rpm.project.infrastructure.repository.ProjectMemberRepository;
import cn.edu.rpm.project.infrastructure.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 里程碑延期应用服务（T-PRJ-016；技术文档 08 §6.1、07 §7）。
 *
 * <p>提交（TC-EXED-001～007）：仅项目 PI 可提交；项目必须 {@code EXECUTING}；
 * 里程碑未完成/取消；无其他 {@code PENDING} 申请；新日期必须晚于原 {@code plan_date}；
 * 理由 ≥10 字。通过后快照 `original_plan_date`、创建审批实例并返回
 * {@code PENDING + instanceId}（Controller 映射 202）。</p>
 *
 * <p>审批批准后由回调服务在同一事务内更新 {@code milestone.current_plan_date}；
 * {@code plan_date}（原计划）永不变更（08 §6.1 第 3 条）。</p>
 */
@Service
public class MilestoneDelayAppService {

    /** 审计动作：提交延期申请。 */
    public static final String AUDIT_DELAY_REQUESTED = "MILESTONE_DELAY_REQUESTED";

    /** 审计动作：撤回延期申请。 */
    public static final String AUDIT_DELAY_CANCELLED = "MILESTONE_DELAY_CANCELLED";

    /** 审计对象类型：里程碑延期申请。 */
    static final String OBJECT_DELAY_REQUEST = "MILESTONE_DELAY_REQUEST";

    private static final int REASON_MIN_LENGTH = 10;

    private final MilestoneRepository milestoneRepository;

    private final ProjectRepository projectRepository;

    private final ProjectMemberRepository projectMemberRepository;

    private final MilestoneDelayRequestRepository delayRequestRepository;

    private final MilestoneDelayWorkflowLauncher workflowLauncher;

    private final AuthorizationService authorizationService;

    private final AuditRecorder auditRecorder;

    private final CanonicalJsonHasher jsonHasher;

    private final Clock clock;

    /**
     * 构造延期应用服务。
     *
     * @param milestoneRepository     里程碑仓储
     * @param projectRepository       项目仓储
     * @param projectMemberRepository 成员仓储（授权装载）
     * @param delayRequestRepository  延期申请仓储
     * @param workflowLauncher        延期审批启动端口
     * @param authorizationService    统一授权服务
     * @param auditRecorder           审计记录器
     * @param jsonHasher              规范化 JSON 工具（证据列表序列化）
     * @param clock                   时间基准
     */
    public MilestoneDelayAppService(MilestoneRepository milestoneRepository,
                                    ProjectRepository projectRepository,
                                    ProjectMemberRepository projectMemberRepository,
                                    MilestoneDelayRequestRepository delayRequestRepository,
                                    MilestoneDelayWorkflowLauncher workflowLauncher,
                                    AuthorizationService authorizationService,
                                    AuditRecorder auditRecorder,
                                    CanonicalJsonHasher jsonHasher,
                                    Clock clock) {
        this.milestoneRepository = Objects.requireNonNull(milestoneRepository,
                "milestoneRepository 不能为空");
        this.projectRepository = Objects.requireNonNull(projectRepository, "projectRepository 不能为空");
        this.projectMemberRepository = Objects.requireNonNull(projectMemberRepository,
                "projectMemberRepository 不能为空");
        this.delayRequestRepository = Objects.requireNonNull(delayRequestRepository,
                "delayRequestRepository 不能为空");
        this.workflowLauncher = Objects.requireNonNull(workflowLauncher, "workflowLauncher 不能为空");
        this.authorizationService = Objects.requireNonNull(authorizationService,
                "authorizationService 不能为空");
        this.auditRecorder = Objects.requireNonNull(auditRecorder, "auditRecorder 不能为空");
        this.jsonHasher = Objects.requireNonNull(jsonHasher, "jsonHasher 不能为空");
        this.clock = Objects.requireNonNull(clock, "clock 不能为空");
    }

    /**
     * 提交里程碑延期申请（TC-EXED-001～007）。
     *
     * @param milestoneId 里程碑 ID
     * @param cmd         申请命令
     * @param auth        当前请求授权上下文
     * @return 申请 ID、状态（PENDING）与审批实例 ID
     * @throws BizException 里程碑不存在 {@code RPM-COMMON-404}；无权限 {@code RPM-COMMON-403}；
     *                      项目状态不允许 {@code RPM-PROJ-002}；已完成/已取消
     *                      {@code RPM-COMMON-422}；重复 PENDING {@code RPM-COMMON-409}；
     *                      日期/理由非法 {@code RPM-COMMON-400}
     */
    @Transactional
    public MilestoneDelayRequestResult requestDelay(String milestoneId, MilestoneDelayRequestCmd cmd,
                                                    AuthContext auth) {
        Objects.requireNonNull(cmd, "cmd 不能为空");
        Objects.requireNonNull(auth, "auth 不能为空");

        MilestoneEntity milestone = milestoneRepository.selectForUpdate(milestoneId)
                .orElseThrow(() -> new BizException(ErrorCode.COMMON_404, "里程碑不存在"));
        ProjectEntity project = projectRepository.findById(milestone.getProjectId())
                .orElseThrow(() -> new BizException(ErrorCode.PROJ_001));

        authorize(project, auth);
        if (!UuidUtils.parse(project.getPiId()).equals(auth.userId())) {
            throw new BizException(ErrorCode.COMMON_403, "仅项目 PI 可提交里程碑延期申请");
        }
        if (!ProjectEntity.STATUS_EXECUTING.equals(project.getStatus())) {
            throw new BizException(ErrorCode.PROJ_002, "项目非执行中，不能提交延期申请");
        }
        if (MilestoneEntity.STATUS_COMPLETED.equals(milestone.getStatus())
                || MilestoneEntity.STATUS_CANCELLED.equals(milestone.getStatus())) {
            throw new BizException(ErrorCode.COMMON_422, "已完成或已取消的里程碑不能申请延期");
        }
        if (delayRequestRepository.findPendingByMilestoneId(milestoneId).isPresent()) {
            throw new BizException(ErrorCode.COMMON_409, "该里程碑已有待审批的延期申请");
        }

        LocalDate requested = cmd.requestedPlanDate();
        if (requested == null || !requested.isAfter(milestone.getPlanDate())) {
            throw new BizException(ErrorCode.COMMON_400, "新计划日期必须晚于原计划日期");
        }
        String reason = cmd.reason() == null ? "" : cmd.reason().trim();
        if (reason.length() < REASON_MIN_LENGTH) {
            throw new BizException(ErrorCode.COMMON_400, "延期理由不少于 10 个字");
        }

        LocalDate original = milestone.getPlanDate();
        MilestoneDelayRequestEntity request = new MilestoneDelayRequestEntity();
        request.setMilestoneId(milestoneId);
        request.setProjectId(project.getId());
        request.setBaselineId(milestone.getBaselineId());
        request.setOriginalPlanDate(original);
        request.setRequestedPlanDate(requested);
        request.setDelayDays((int) ChronoUnit.DAYS.between(original, requested));
        request.setReason(reason);
        request.setEvidenceFileIds(jsonHasher.canonicalJson(cmd.evidenceFileIds()));
        request.setRequestedBy(UuidUtils.formatCompact(auth.userId()));
        request.setStatus(MilestoneDelayRequestEntity.STATUS_PENDING);
        delayRequestRepository.insert(request);

        OffsetDateTime now = clock.instant().atZone(clock.getZone()).toOffsetDateTime();
        String instanceId = workflowLauncher.start(new MilestoneDelayWorkflowStart(
                UuidUtils.parse(request.getId()),
                UuidUtils.parse(milestone.getId()),
                UuidUtils.parse(project.getId()),
                project.getProjectType(),
                auth.userId(),
                now));
        request.linkWorkflow(instanceId);
        int updated = delayRequestRepository.update(request);
        if (updated == 0) {
            throw new BizException(ErrorCode.COMMON_409, "延期申请已被并发修改，请刷新后重试");
        }

        auditRecorder.record(new AuditEvent(
                TraceIds.currentOrUntracked(),
                auth.userId(),
                null,
                AUDIT_DELAY_REQUESTED,
                OBJECT_DELAY_REQUEST,
                UuidUtils.parse(request.getId()),
                request.getVersion(),
                null,
                null,
                "里程碑延期申请：" + original + " → " + requested + "（" + reason + "）",
                null,
                null,
                clock.instant()));
        return new MilestoneDelayRequestResult(request.getId(), request.getStatus(), instanceId);
    }

    /**
     * 撤回延期申请（08 §6.1 第 5 条：申请人、审批完成前）。
     *
     * @param requestId 延期申请 ID
     * @param auth      当前请求授权上下文
     * @throws BizException 申请不存在 {@code RPM-COMMON-404}；非申请人 {@code RPM-COMMON-403}；
     *                      状态不允许 {@code RPM-COMMON-422}
     */
    @Transactional
    public void cancel(String requestId, AuthContext auth) {
        Objects.requireNonNull(auth, "auth 不能为空");
        MilestoneDelayRequestEntity request = delayRequestRepository.findById(requestId)
                .orElseThrow(() -> new BizException(ErrorCode.COMMON_404, "延期申请不存在"));
        if (!UuidUtils.sameId(request.getRequestedBy(), auth.userId())) {
            throw new BizException(ErrorCode.COMMON_403, "仅申请人可撤回延期申请");
        }
        if (!MilestoneDelayRequestEntity.STATUS_PENDING.equals(request.getStatus())) {
            throw new BizException(ErrorCode.COMMON_422, "仅待审批的延期申请可撤回");
        }

        OffsetDateTime now = clock.instant().atZone(clock.getZone()).toOffsetDateTime();
        request.cancel(now);
        int updated = delayRequestRepository.update(request);
        if (updated == 0) {
            throw new BizException(ErrorCode.COMMON_409, "延期申请已被并发修改，请刷新后重试");
        }
        if (request.getWfInstanceId() != null) {
            workflowLauncher.cancel(request.getWfInstanceId(), auth.userId(),
                    "申请人撤回延期申请", UuidUtils.parse(requestId));
        }

        auditRecorder.record(new AuditEvent(
                TraceIds.currentOrUntracked(),
                auth.userId(),
                null,
                AUDIT_DELAY_CANCELLED,
                OBJECT_DELAY_REQUEST,
                UuidUtils.parse(request.getId()),
                request.getVersion(),
                null,
                null,
                "撤回里程碑延期申请",
                null,
                null,
                clock.instant()));
    }

    private void authorize(ProjectEntity project, AuthContext auth) {
        ResourceRef resource = new ResourceRef(
                ResourceTypes.PROJECT,
                UuidUtils.parse(project.getId()),
                UuidUtils.parse(project.getPiId()),
                UuidUtils.parse(project.getDeptId()),
                activeMemberIds(project.getId()));
        AuthDecision decision = authorizationService.check(auth,
                Action.of(ProjectPermissions.MILESTONE_MANAGE), resource);
        if (!decision.allowed()) {
            throw new BizException(ErrorCode.COMMON_403);
        }
    }

    private Set<UUID> activeMemberIds(String projectId) {
        OffsetDateTime now = clock.instant().atZone(clock.getZone()).toOffsetDateTime();
        return projectMemberRepository.findActiveByProjectId(projectId, now).stream()
                .map(ProjectMemberEntity::getUserId)
                .map(UuidUtils::parse)
                .collect(Collectors.toSet());
    }
}
