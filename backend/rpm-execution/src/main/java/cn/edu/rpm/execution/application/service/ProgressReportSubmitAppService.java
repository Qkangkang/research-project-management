package cn.edu.rpm.execution.application.service;

import cn.edu.rpm.audit.domain.model.AuditEvent;
import cn.edu.rpm.audit.domain.service.AuditRecorder;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.common.util.CanonicalJsonHasher;
import cn.edu.rpm.common.util.TraceIds;
import cn.edu.rpm.common.util.UuidUtils;
import cn.edu.rpm.execution.application.dto.ProgressReportSubmitted;
import cn.edu.rpm.execution.application.dto.SubmitProgressReportCmd;
import cn.edu.rpm.execution.domain.model.ExecutionPermissions;
import cn.edu.rpm.execution.domain.port.ProgressReportRiskPort;
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
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 周期报告提交应用服务（T-EXEC-006；技术文档 04 §3.8、07 §3.2）。
 *
 * <p>提交时锁定报告主档，校验成员权限与 {@code expectedVersion}，插入不可变版本
 * （{@code version_no = 当前最大版本 + 1}），并原子更新主档为 {@code SUBMITTED}。
 * 迟报事实仅写入版本 {@code late_flag}（主档状态不含 {@code LATE}，修复 #23），
 * 同时通过 {@link ProgressReportRiskPort} 生成逾期风险。</p>
 */
@Service
public class ProgressReportSubmitAppService {

    /** 审计动作：提交周期报告。 */
    public static final String AUDIT_REPORT_SUBMITTED = "PROGRESS_REPORT_SUBMITTED";

    /** 审计对象类型：周期报告。 */
    static final String OBJECT_REPORT = "PROGRESS_REPORT";

    private final ProgressReportRepository progressReportRepository;

    private final ProgressReportVersionRepository versionRepository;

    private final ProjectRepository projectRepository;

    private final ProjectMemberRepository projectMemberRepository;

    private final ProgressReportRiskPort riskPort;

    private final AuthorizationService authorizationService;

    private final AuditRecorder auditRecorder;

    private final CanonicalJsonHasher jsonHasher;

    private final Clock clock;

    /**
     * 构造报告提交服务。
     *
     * @param progressReportRepository 报告主档仓储
     * @param versionRepository        报告版本仓储
     * @param projectRepository        项目仓储
     * @param projectMemberRepository  成员仓储（授权装载）
     * @param riskPort                 逾期风险端口
     * @param authorizationService     统一授权服务
     * @param auditRecorder            审计记录器
     * @param jsonHasher               规范化 JSON 工具
     * @param clock                    时间基准
     */
    public ProgressReportSubmitAppService(ProgressReportRepository progressReportRepository,
                                          ProgressReportVersionRepository versionRepository,
                                          ProjectRepository projectRepository,
                                          ProjectMemberRepository projectMemberRepository,
                                          ProgressReportRiskPort riskPort,
                                          AuthorizationService authorizationService,
                                          AuditRecorder auditRecorder,
                                          CanonicalJsonHasher jsonHasher,
                                          Clock clock) {
        this.progressReportRepository = Objects.requireNonNull(progressReportRepository,
                "progressReportRepository 不能为空");
        this.versionRepository = Objects.requireNonNull(versionRepository,
                "versionRepository 不能为空");
        this.projectRepository = Objects.requireNonNull(projectRepository, "projectRepository 不能为空");
        this.projectMemberRepository = Objects.requireNonNull(projectMemberRepository,
                "projectMemberRepository 不能为空");
        this.riskPort = Objects.requireNonNull(riskPort, "riskPort 不能为空");
        this.authorizationService = Objects.requireNonNull(authorizationService,
                "authorizationService 不能为空");
        this.auditRecorder = Objects.requireNonNull(auditRecorder, "auditRecorder 不能为空");
        this.jsonHasher = Objects.requireNonNull(jsonHasher, "jsonHasher 不能为空");
        this.clock = Objects.requireNonNull(clock, "clock 不能为空");
    }

    /**
     * 提交周期报告（TC-EXEC-008 / TC-EXEC-009 / TC-EXEC-011）。
     *
     * @param reportId 报告 ID
     * @param cmd      提交命令
     * @param auth     当前请求授权上下文
     * @return 新版本号、版本 ID、状态与迟报标记
     * @throws BizException 报告不存在 {@code RPM-COMMON-404}；项目不存在 {@code RPM-PROJ-001}；
     *                      无权限 {@code RPM-COMMON-403}；参数缺失 {@code RPM-COMMON-400}；
     *                      版本冲突 {@code RPM-COMMON-409}；状态不允许 {@code RPM-COMMON-422}
     */
    @Transactional
    public ProgressReportSubmitted submit(String reportId, SubmitProgressReportCmd cmd,
                                          AuthContext auth) {
        Objects.requireNonNull(cmd, "cmd 不能为空");
        Objects.requireNonNull(auth, "auth 不能为空");

        ProgressReportEntity report = progressReportRepository.selectForUpdate(reportId)
                .orElseThrow(() -> new BizException(ErrorCode.COMMON_404, "周期报告不存在"));
        ProjectEntity project = projectRepository.findById(report.getProjectId())
                .orElseThrow(() -> new BizException(ErrorCode.PROJ_001));
        authorize(project, auth);

        if (!ProgressReportEntity.STATUS_DRAFT.equals(report.getStatus())
                && !ProgressReportEntity.STATUS_RETURNED.equals(report.getStatus())) {
            throw new BizException(ErrorCode.COMMON_422,
                    "仅草稿或已退回的报告可提交，当前状态：" + report.getStatus());
        }
        if (cmd.expectedVersion() == null) {
            throw new BizException(ErrorCode.COMMON_400, "expectedVersion 不能为空");
        }
        if (!cmd.expectedVersion().equals(report.getVersion())) {
            throw new BizException(ErrorCode.COMMON_409, "报告已被修改，请刷新后重试");
        }
        requireContent(cmd);

        LocalDate today = LocalDate.now(clock);
        boolean late = report.getDueDate() != null && today.isAfter(report.getDueDate());
        long daysLate = late ? ChronoUnit.DAYS.between(report.getDueDate(), today) : 0;

        String content = jsonHasher.canonicalJson(cmd.content());
        OffsetDateTime now = OffsetDateTime.now(clock);
        int versionNo = versionRepository.findMaxVersionNo(reportId) + 1;

        ProgressReportVersionEntity version = new ProgressReportVersionEntity();
        version.setId(UuidUtils.formatCompact(UUID.randomUUID()));
        version.setReportId(reportId);
        version.setVersionNo(versionNo);
        version.setContent(content);
        version.setContentHash(jsonHasher.sha256(content));
        version.setSubmittedBy(UuidUtils.formatCompact(auth.userId()));
        version.setSubmittedAt(now);
        version.setDecision(ProgressReportVersionEntity.DECISION_PENDING);
        version.setLateFlag(late);
        versionRepository.insert(version);

        report.markSubmitted(version.getId());
        if (progressReportRepository.update(report) == 0) {
            throw new BizException(ErrorCode.COMMON_409, "报告已被并发提交，请重试");
        }
        if (late) {
            riskPort.raiseLateReportRisk(project.getId(), reportId, version.getId(), daysLate);
        }

        auditRecorder.record(new AuditEvent(
                TraceIds.currentOrUntracked(),
                auth.userId(),
                null,
                AUDIT_REPORT_SUBMITTED,
                OBJECT_REPORT,
                UuidUtils.parse(reportId),
                versionNo,
                null,
                version.getContentHash(),
                "提交周期报告" + (late ? "（迟报 " + daysLate + " 天）" : ""),
                null,
                null,
                clock.instant()));

        return new ProgressReportSubmitted(reportId, version.getId(), versionNo,
                report.getStatus(), late);
    }

    private static void requireContent(SubmitProgressReportCmd cmd) {
        if (cmd.content().isEmpty()) {
            throw new BizException(ErrorCode.COMMON_400, "报告内容不能为空");
        }
        Object workDone = cmd.content().containsKey("work_done")
                ? cmd.content().get("work_done") : cmd.content().get("workDone");
        if (!(workDone instanceof CharSequence text) || text.toString().isBlank()) {
            throw new BizException(ErrorCode.COMMON_400, "content.work_done 不能为空");
        }
    }

    private void authorize(ProjectEntity project, AuthContext auth) {
        ResourceRef resource = new ResourceRef(
                ResourceTypes.PROJECT,
                UuidUtils.parse(project.getId()),
                UuidUtils.parse(project.getPiId()),
                UuidUtils.parse(project.getDeptId()),
                activeMemberIds(project.getId()));
        AuthDecision decision = authorizationService.check(auth,
                Action.of(ExecutionPermissions.REPORT_SUBMIT), resource);
        if (!decision.allowed()) {
            throw new BizException(ErrorCode.COMMON_403);
        }
    }

    private Set<UUID> activeMemberIds(String projectId) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        return projectMemberRepository.findActiveByProjectId(projectId, now).stream()
                .map(ProjectMemberEntity::getUserId)
                .map(UuidUtils::parse)
                .collect(Collectors.toSet());
    }
}
