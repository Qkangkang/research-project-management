package cn.edu.rpm.execution.application.service;

import cn.edu.rpm.audit.domain.model.AuditEvent;
import cn.edu.rpm.audit.domain.service.AuditRecorder;
import cn.edu.rpm.common.domain.model.OutboxEvent;
import cn.edu.rpm.common.domain.port.OutboxAppendPort;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.common.util.TraceIds;
import cn.edu.rpm.common.util.UuidUtils;
import cn.edu.rpm.execution.application.dto.ProgressReportReviewed;
import cn.edu.rpm.execution.application.dto.ReviewProgressReportCmd;
import cn.edu.rpm.execution.domain.model.ExecutionPermissions;
import cn.edu.rpm.execution.infrastructure.entity.ProgressReportEntity;
import cn.edu.rpm.execution.infrastructure.entity.ProgressReportVersionEntity;
import cn.edu.rpm.execution.infrastructure.repository.ProgressReportRepository;
import cn.edu.rpm.execution.infrastructure.repository.ProgressReportVersionRepository;
import cn.edu.rpm.iam.application.dto.Action;
import cn.edu.rpm.iam.application.dto.AuthContext;
import cn.edu.rpm.iam.application.dto.AuthDecision;
import cn.edu.rpm.iam.application.dto.ResourceRef;
import cn.edu.rpm.iam.domain.model.ResourceTypes;
import cn.edu.rpm.iam.domain.service.AuthorizationService;
import cn.edu.rpm.project.infrastructure.entity.ProjectEntity;
import cn.edu.rpm.project.infrastructure.entity.ProjectMemberEntity;
import cn.edu.rpm.project.infrastructure.repository.ProjectMemberRepository;
import cn.edu.rpm.project.infrastructure.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 周期报告审核服务（T-EXEC-007；技术文档 07 §3.2、04 §3.8）。
 *
 * <p>退回：报告置 {@code RETURNED}，当前版本写入 {@code RETURN} 决策与审核意见；
 * 旧版本行保持不可变；通过 outbox 事件 {@code REPORT_RETURNED} 通知通知模块
 * 生成补正待办（不在执行模块直接写 {@code todo_item}，避免跨模块写表）。</p>
 *
 * <p>通过：报告置 {@code APPROVED}，当前版本写入 {@code PASS} 决策并固定，
 * 不新增版本；任何后续修改都以新版本行追加。</p>
 */
@Service
public class ProgressReportReviewService {

    /** 审计动作：退回周期报告。 */
    public static final String AUDIT_REPORT_RETURNED = "PROGRESS_REPORT_RETURNED";

    /** 审计动作：通过周期报告。 */
    public static final String AUDIT_REPORT_APPROVED = "PROGRESS_REPORT_APPROVED";

    /** 审计对象类型：周期报告。 */
    static final String OBJECT_REPORT = "PROGRESS_REPORT";

    /** 事件类型：报告退回（通知模块据此生成补正待办）。 */
    public static final String EVENT_REPORT_RETURNED = "REPORT_RETURNED";

    private final ProgressReportRepository progressReportRepository;

    private final ProgressReportVersionRepository versionRepository;

    private final ProjectRepository projectRepository;

    private final ProjectMemberRepository projectMemberRepository;

    private final OutboxAppendPort outboxAppendPort;

    private final AuthorizationService authorizationService;

    private final AuditRecorder auditRecorder;

    private final Clock clock;

    /**
     * 构造报告审核服务。
     *
     * @param progressReportRepository 报告主档仓储
     * @param versionRepository        报告版本仓储
     * @param projectRepository        项目仓储
     * @param projectMemberRepository  成员仓储（授权装载）
     * @param outboxAppendPort         Outbox 追加端口（补正待办事件）
     * @param authorizationService     统一授权服务
     * @param auditRecorder            审计记录器
     * @param clock                    时间基准
     */
    public ProgressReportReviewService(ProgressReportRepository progressReportRepository,
                                       ProgressReportVersionRepository versionRepository,
                                       ProjectRepository projectRepository,
                                       ProjectMemberRepository projectMemberRepository,
                                       OutboxAppendPort outboxAppendPort,
                                       AuthorizationService authorizationService,
                                       AuditRecorder auditRecorder,
                                       Clock clock) {
        this.progressReportRepository = Objects.requireNonNull(progressReportRepository,
                "progressReportRepository 不能为空");
        this.versionRepository = Objects.requireNonNull(versionRepository,
                "versionRepository 不能为空");
        this.projectRepository = Objects.requireNonNull(projectRepository, "projectRepository 不能为空");
        this.projectMemberRepository = Objects.requireNonNull(projectMemberRepository,
                "projectMemberRepository 不能为空");
        this.outboxAppendPort = Objects.requireNonNull(outboxAppendPort,
                "outboxAppendPort 不能为空");
        this.authorizationService = Objects.requireNonNull(authorizationService,
                "authorizationService 不能为空");
        this.auditRecorder = Objects.requireNonNull(auditRecorder, "auditRecorder 不能为空");
        this.clock = Objects.requireNonNull(clock, "clock 不能为空");
    }

    /**
     * 退回周期报告（TC-EXEC-010）。
     *
     * @param reportId 报告 ID
     * @param cmd      审核命令（{@code comment} 必填）
     * @param auth     当前请求授权上下文
     * @return 报告状态与版本决策
     * @throws BizException 报告不存在 {@code RPM-COMMON-404}；项目不存在 {@code RPM-PROJ-001}；
     *                      无权限 {@code RPM-COMMON-403}；意见缺失 {@code RPM-COMMON-400}；
     *                      状态不允许 {@code RPM-COMMON-422}；版本冲突 {@code RPM-COMMON-409}
     */
    @Transactional
    public ProgressReportReviewed returnReport(String reportId, ReviewProgressReportCmd cmd,
                                               AuthContext auth) {
        Objects.requireNonNull(cmd, "cmd 不能为空");
        ProgressReportEntity report = lockAndAuthorize(reportId, auth);
        requireSubmitted(report);
        if (cmd.comment() == null || cmd.comment().isBlank()) {
            throw new BizException(ErrorCode.COMMON_400, "退回必须填写问题说明");
        }
        String comment = cmd.comment().trim();
        ProgressReportVersionEntity version = currentVersion(report);

        version.decide(ProgressReportVersionEntity.DECISION_RETURN, comment);
        if (versionRepository.update(version) == 0) {
            throw new BizException(ErrorCode.COMMON_409, "报告版本已被并发处理，请重试");
        }
        report.markReturned();
        if (progressReportRepository.update(report) == 0) {
            throw new BizException(ErrorCode.COMMON_409, "报告已被并发处理，请重试");
        }
        appendRevisionTodo(report, version, comment);
        audit(report, version, auth, AUDIT_REPORT_RETURNED, "退回周期报告");
        return new ProgressReportReviewed(reportId, version.getId(), report.getStatus(),
                version.getDecision());
    }

    /**
     * 通过周期报告（TC-EXEC-012）。
     *
     * @param reportId 报告 ID
     * @param cmd      审核命令（{@code comment} 可选）
     * @param auth     当前请求授权上下文
     * @return 报告状态与版本决策
     * @throws BizException 报告不存在 {@code RPM-COMMON-404}；项目不存在 {@code RPM-PROJ-001}；
     *                      无权限 {@code RPM-COMMON-403}；状态不允许 {@code RPM-COMMON-422}；
     *                      版本冲突 {@code RPM-COMMON-409}
     */
    @Transactional
    public ProgressReportReviewed approve(String reportId, ReviewProgressReportCmd cmd,
                                          AuthContext auth) {
        Objects.requireNonNull(cmd, "cmd 不能为空");
        ProgressReportEntity report = lockAndAuthorize(reportId, auth);
        requireSubmitted(report);
        String comment = cmd.comment() == null || cmd.comment().isBlank()
                ? null : cmd.comment().trim();
        ProgressReportVersionEntity version = currentVersion(report);

        version.decide(ProgressReportVersionEntity.DECISION_PASS, comment);
        if (versionRepository.update(version) == 0) {
            throw new BizException(ErrorCode.COMMON_409, "报告版本已被并发处理，请重试");
        }
        report.markApproved();
        if (progressReportRepository.update(report) == 0) {
            throw new BizException(ErrorCode.COMMON_409, "报告已被并发处理，请重试");
        }
        audit(report, version, auth, AUDIT_REPORT_APPROVED, "通过周期报告");
        return new ProgressReportReviewed(reportId, version.getId(), report.getStatus(),
                version.getDecision());
    }

    private ProgressReportEntity lockAndAuthorize(String reportId, AuthContext auth) {
        Objects.requireNonNull(auth, "auth 不能为空");
        ProgressReportEntity report = progressReportRepository.selectForUpdate(reportId)
                .orElseThrow(() -> new BizException(ErrorCode.COMMON_404, "周期报告不存在"));
        ProjectEntity project = projectRepository.findById(report.getProjectId())
                .orElseThrow(() -> new BizException(ErrorCode.PROJ_001));
        ResourceRef resource = new ResourceRef(
                ResourceTypes.PROJECT,
                UuidUtils.parse(project.getId()),
                UuidUtils.parse(project.getPiId()),
                UuidUtils.parse(project.getDeptId()),
                activeMemberIds(project.getId()));
        AuthDecision decision = authorizationService.check(auth,
                Action.of(ExecutionPermissions.REPORT_REVIEW), resource);
        if (!decision.allowed()) {
            throw new BizException(ErrorCode.COMMON_403);
        }
        return report;
    }

    private static void requireSubmitted(ProgressReportEntity report) {
        if (!ProgressReportEntity.STATUS_SUBMITTED.equals(report.getStatus())) {
            throw new BizException(ErrorCode.COMMON_422,
                    "仅已提交的报告可审核，当前状态：" + report.getStatus());
        }
    }

    private ProgressReportVersionEntity currentVersion(ProgressReportEntity report) {
        if (report.getCurrentVersionId() == null) {
            throw new BizException(ErrorCode.COMMON_422, "报告缺少当前版本，无法审核");
        }
        ProgressReportVersionEntity version = versionRepository
                .findById(report.getCurrentVersionId())
                .orElseThrow(() -> new BizException(ErrorCode.COMMON_404, "报告版本不存在"));
        if (!UuidUtils.sameId(report.getId(), version.getReportId())) {
            throw new BizException(ErrorCode.COMMON_422, "报告版本与报告不匹配");
        }
        return version;
    }

    private void appendRevisionTodo(ProgressReportEntity report,
                                    ProgressReportVersionEntity version, String comment) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("report_id", report.getId());
        payload.put("project_id", report.getProjectId());
        payload.put("version_id", version.getId());
        payload.put("assignee_id", version.getSubmittedBy());
        payload.put("comment", comment);
        outboxAppendPort.append(new OutboxEvent(
                UUID.randomUUID(),
                OBJECT_REPORT,
                UuidUtils.parse(report.getId()),
                EVENT_REPORT_RETURNED,
                payload,
                TraceIds.currentOrUntracked(),
                EVENT_REPORT_RETURNED + ":" + version.getId()));
    }

    private void audit(ProgressReportEntity report, ProgressReportVersionEntity version,
                       AuthContext auth, String action, String reason) {
        auditRecorder.record(new AuditEvent(
                TraceIds.currentOrUntracked(),
                auth.userId(),
                null,
                action,
                OBJECT_REPORT,
                UuidUtils.parse(report.getId()),
                version.getVersionNo(),
                null,
                null,
                reason,
                null,
                null,
                clock.instant()));
    }

    private Set<UUID> activeMemberIds(String projectId) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        return projectMemberRepository.findActiveByProjectId(projectId, now).stream()
                .map(ProjectMemberEntity::getUserId)
                .map(UuidUtils::parse)
                .collect(Collectors.toSet());
    }
}
