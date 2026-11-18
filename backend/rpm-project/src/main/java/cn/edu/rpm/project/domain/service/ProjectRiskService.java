package cn.edu.rpm.project.domain.service;

import cn.edu.rpm.audit.domain.model.AuditEvent;
import cn.edu.rpm.audit.domain.service.AuditRecorder;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.common.util.TraceIds;
import cn.edu.rpm.common.util.UuidUtils;
import cn.edu.rpm.file.domain.model.FileObject;
import cn.edu.rpm.file.domain.port.FileObjectQueryPort;
import cn.edu.rpm.iam.application.dto.Action;
import cn.edu.rpm.iam.application.dto.AuthContext;
import cn.edu.rpm.iam.application.dto.AuthDecision;
import cn.edu.rpm.iam.application.dto.ResourceRef;
import cn.edu.rpm.iam.domain.model.ResourceTypes;
import cn.edu.rpm.iam.domain.service.AuthorizationService;
import cn.edu.rpm.project.application.dto.CloseProjectRiskCmd;
import cn.edu.rpm.project.application.dto.CreateProjectRiskCmd;
import cn.edu.rpm.project.application.dto.ProjectRiskView;
import cn.edu.rpm.project.domain.model.ProjectPermissions;
import cn.edu.rpm.project.infrastructure.entity.ProjectEntity;
import cn.edu.rpm.project.infrastructure.entity.ProjectMemberEntity;
import cn.edu.rpm.project.infrastructure.entity.ProjectRiskEntity;
import cn.edu.rpm.project.infrastructure.repository.ProjectMemberRepository;
import cn.edu.rpm.project.infrastructure.repository.ProjectRepository;
import cn.edu.rpm.project.infrastructure.repository.ProjectRiskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 项目风险领域服务（T-PRJ-015；技术文档 07 §3.3、05 §5.1）。
 *
 * <p>规则：</p>
 * <ul>
 *   <li>登记风险初始状态 {@code OPEN}，责任人必填（TC-EXEC-016）；</li>
 *   <li>关闭必须填写处置结果与可用证据文件，否则 {@code RPM-COMMON-400} /
 *       {@code RPM-FILE-001}（TC-EXEC-017/018）；</li>
 *   <li>逾期升级以截止日 23:59:59 为界：未超过该时刻不升级（TC-EXEC-020），
 *       超过后状态 {@code ESCALATED} 且等级提升一级（TC-EXEC-019）。</li>
 * </ul>
 */
@Service
public class ProjectRiskService {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectRiskService.class);

    /** 风险类型：进度/迟报。 */
    public static final String RISK_TYPE_SCHEDULE = "SCHEDULE";

    /** 迟报风险来源规则前缀（与 reportId 拼接，同时用作幂等键）。 */
    public static final String SOURCE_RULE_LATE_REPORT_PREFIX = "LATE_REPORT:";

    /** 审计动作：迟报自动生成风险。 */
    public static final String AUDIT_RISK_LATE_REPORT = "PROJECT_RISK_LATE_REPORT_RAISED";

    /** 审计动作：登记风险。 */
    public static final String AUDIT_RISK_CREATED = "PROJECT_RISK_CREATED";

    /** 审计动作：关闭风险。 */
    public static final String AUDIT_RISK_CLOSED = "PROJECT_RISK_CLOSED";

    /** 审计动作：逾期升级风险。 */
    public static final String AUDIT_RISK_ESCALATED = "PROJECT_RISK_ESCALATED";

    /** 审计对象类型：项目风险。 */
    static final String OBJECT_RISK = "PROJECT_RISK";

    private static final Set<String> LEVELS = Set.of(
            ProjectRiskEntity.LEVEL_LOW, ProjectRiskEntity.LEVEL_MEDIUM,
            ProjectRiskEntity.LEVEL_HIGH, ProjectRiskEntity.LEVEL_CRITICAL);

    private final ProjectRiskRepository projectRiskRepository;

    private final ProjectRepository projectRepository;

    private final ProjectMemberRepository projectMemberRepository;

    private final FileObjectQueryPort fileObjectQueryPort;

    private final AuthorizationService authorizationService;

    private final AuditRecorder auditRecorder;

    private final Clock clock;

    /**
     * 构造风险服务。
     *
     * @param projectRiskRepository   风险仓储
     * @param projectRepository       项目仓储
     * @param projectMemberRepository 成员仓储（授权装载）
     * @param fileObjectQueryPort     文件查询端口（证据可用性校验）
     * @param authorizationService    统一授权服务
     * @param auditRecorder           审计记录器
     * @param clock                   时间基准
     */
    public ProjectRiskService(ProjectRiskRepository projectRiskRepository,
                              ProjectRepository projectRepository,
                              ProjectMemberRepository projectMemberRepository,
                              FileObjectQueryPort fileObjectQueryPort,
                              AuthorizationService authorizationService,
                              AuditRecorder auditRecorder,
                              Clock clock) {
        this.projectRiskRepository = Objects.requireNonNull(projectRiskRepository,
                "projectRiskRepository 不能为空");
        this.projectRepository = Objects.requireNonNull(projectRepository, "projectRepository 不能为空");
        this.projectMemberRepository = Objects.requireNonNull(projectMemberRepository,
                "projectMemberRepository 不能为空");
        this.fileObjectQueryPort = Objects.requireNonNull(fileObjectQueryPort, "fileObjectQueryPort 不能为空");
        this.authorizationService = Objects.requireNonNull(authorizationService,
                "authorizationService 不能为空");
        this.auditRecorder = Objects.requireNonNull(auditRecorder, "auditRecorder 不能为空");
        this.clock = Objects.requireNonNull(clock, "clock 不能为空");
    }

    /**
     * 登记项目风险（TC-EXEC-016）。
     *
     * @param projectId 项目 ID
     * @param cmd       登记命令
     * @param auth      当前请求授权上下文
     * @return 风险视图（状态 OPEN）
     * @throws BizException 项目不存在 {@code RPM-PROJ-001}；无权限 {@code RPM-COMMON-403}；
     *                      参数非法 {@code RPM-COMMON-400}
     */
    @Transactional
    public ProjectRiskView create(String projectId, CreateProjectRiskCmd cmd, AuthContext auth) {
        Objects.requireNonNull(cmd, "cmd 不能为空");
        Objects.requireNonNull(auth, "auth 不能为空");

        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BizException(ErrorCode.PROJ_001));
        authorize(project, auth, ProjectPermissions.RISK_CREATE);

        String ownerId = requireText(cmd.ownerId(), "风险责任人不能为空");
        String riskType = requireText(cmd.riskType(), "风险类型不能为空");
        String title = requireText(cmd.title(), "风险标题不能为空");
        String level = cmd.riskLevel() == null || cmd.riskLevel().isBlank()
                ? ProjectRiskEntity.LEVEL_MEDIUM : cmd.riskLevel().trim();
        if (!LEVELS.contains(level)) {
            throw new BizException(ErrorCode.COMMON_400, "风险等级非法：" + level);
        }

        ProjectRiskEntity risk = new ProjectRiskEntity();
        risk.setProjectId(projectId);
        risk.setOwnerId(ownerId);
        risk.setRiskType(riskType);
        risk.setTitle(title);
        risk.setDescription(cmd.description());
        risk.setRiskLevel(level);
        risk.setDueDate(cmd.dueDate());
        risk.setStatus(ProjectRiskEntity.STATUS_OPEN);
        risk.setIdempotencyKey(cmd.idempotencyKey());
        projectRiskRepository.insert(risk);

        auditRecorder.record(new AuditEvent(
                TraceIds.currentOrUntracked(),
                auth.userId(),
                null,
                AUDIT_RISK_CREATED,
                OBJECT_RISK,
                UuidUtils.parse(risk.getId()),
                null,
                null,
                null,
                "登记风险：" + title,
                null,
                null,
                clock.instant()));
        return ProjectRiskView.from(risk);
    }

    /**
     * 关闭项目风险（TC-EXEC-017 / TC-EXEC-018）。
     *
     * @param riskId 风险 ID
     * @param cmd    关闭命令（处置结果与证据必填）
     * @param auth   当前请求授权上下文
     * @return 关闭后的风险视图
     * @throws BizException 风险不存在 {@code RPM-COMMON-404}；无权限 {@code RPM-COMMON-403}；
     *                      处置结果/证据缺失 {@code RPM-COMMON-400}；文件不可用
     *                      {@code RPM-FILE-001}；状态不允许 {@code RPM-COMMON-422}
     */
    @Transactional
    public ProjectRiskView close(String riskId, CloseProjectRiskCmd cmd, AuthContext auth) {
        Objects.requireNonNull(cmd, "cmd 不能为空");
        Objects.requireNonNull(auth, "auth 不能为空");

        ProjectRiskEntity risk = projectRiskRepository.findById(riskId)
                .orElseThrow(() -> new BizException(ErrorCode.COMMON_404, "风险不存在"));
        ProjectEntity project = projectRepository.findById(risk.getProjectId())
                .orElseThrow(() -> new BizException(ErrorCode.PROJ_001));
        authorize(project, auth, ProjectPermissions.RISK_CLOSE);

        String resolution = requireText(cmd.resolution(), "处置结果不能为空");
        String evidenceFileId = requireText(cmd.evidenceFileId(), "处置证据文件不能为空");
        FileObject file = fileObjectQueryPort.findById(UuidUtils.parse(evidenceFileId))
                .orElseThrow(() -> new BizException(ErrorCode.FILE_001, "处置证据文件不存在"));
        file.requireAvailable();

        try {
            risk.close(resolution, evidenceFileId, UuidUtils.formatCompact(auth.userId()),
                    clock.instant().atZone(clock.getZone()).toOffsetDateTime());
        } catch (IllegalStateException ex) {
            throw new BizException(ErrorCode.COMMON_422, ex.getMessage());
        }
        int updated = projectRiskRepository.update(risk);
        if (updated == 0) {
            throw new BizException(ErrorCode.COMMON_409, "风险已被并发修改，请刷新后重试");
        }

        auditRecorder.record(new AuditEvent(
                TraceIds.currentOrUntracked(),
                auth.userId(),
                null,
                AUDIT_RISK_CLOSED,
                OBJECT_RISK,
                UuidUtils.parse(risk.getId()),
                risk.getVersion(),
                null,
                null,
                "关闭风险：" + resolution,
                null,
                null,
                clock.instant()));
        return ProjectRiskView.from(risk);
    }

    /**
     * 扫描并升级逾期风险（TC-EXEC-019 / TC-EXEC-020）。
     *
     * <p>逾期边界为截止日 23:59:59（{@code Asia/Shanghai}）：当前时刻不晚于该时刻
     * 不升级；晚于则状态置 {@code ESCALATED} 且等级提升一级（CRITICAL 封顶）。</p>
     *
     * @return 本次升级的风险数量
     */
    @Transactional
    public int escalateOverdue() {
        ZonedDateTime now = clock.instant().atZone(clock.getZone());
        List<ProjectRiskEntity> candidates = projectRiskRepository.findOpenDueBy(now.toLocalDate());

        int escalated = 0;
        for (ProjectRiskEntity risk : candidates) {
            LocalDate dueDate = risk.getDueDate();
            if (dueDate == null) {
                continue;
            }
            ZonedDateTime deadline = dueDate.atTime(23, 59, 59).atZone(clock.getZone());
            if (!now.toInstant().isAfter(deadline.toInstant())) {
                continue;
            }
            String beforeLevel = risk.escalate();
            int updated = projectRiskRepository.update(risk);
            if (updated == 0) {
                throw new BizException(ErrorCode.COMMON_409, "风险已被并发修改，请刷新后重试");
            }
            auditRecorder.record(new AuditEvent(
                    TraceIds.currentOrUntracked(),
                    null,
                    null,
                    AUDIT_RISK_ESCALATED,
                    OBJECT_RISK,
                    UuidUtils.parse(risk.getId()),
                    risk.getVersion(),
                    null,
                    null,
                    "逾期升级：" + beforeLevel + " → " + risk.getRiskLevel(),
                    null,
                    null,
                    clock.instant()));
            escalated++;
        }
        return escalated;
    }

    /**
     * 查询项目风险列表（只读，用于项目详情风险页签）。
     *
     * @param projectId 项目 ID
     * @param auth      当前请求授权上下文
     * @return 风险视图列表；无风险返回空列表
     * @throws BizException 项目不存在 {@code RPM-PROJ-001}；无权限 {@code RPM-COMMON-403}
     */
    @Transactional(readOnly = true)
    public List<ProjectRiskView> list(String projectId, AuthContext auth) {
        Objects.requireNonNull(auth, "auth 不能为空");
        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BizException(ErrorCode.PROJ_001));
        authorize(project, auth, ProjectPermissions.VIEW);
        return projectRiskRepository.findByProjectId(projectId).stream()
                .map(ProjectRiskView::from)
                .toList();
    }

    /**
     * 为周期报告迟报生成逾期风险（T-EXEC-006；技术文档 04 §3.8 步骤 5）。
     *
     * <p>同一报告以 {@code LATE_REPORT:{reportId}} 作为来源规则与幂等键，
     * 重复调用不重复生成。风险责任人为项目 PI，等级 {@code MEDIUM}，
     * 初始状态 {@code OPEN}。</p>
     *
     * @param projectId 项目 ID
     * @param reportId  报告 ID
     * @param versionId 报告版本 ID
     * @param daysLate  逾期天数（必须大于 0）
     * @throws BizException 项目不存在 {@code RPM-PROJ-001}
     */
    @Transactional
    public void raiseLateReportRisk(String projectId, String reportId, String versionId,
                                    long daysLate) {
        Objects.requireNonNull(projectId, "projectId 不能为空");
        Objects.requireNonNull(reportId, "reportId 不能为空");
        Objects.requireNonNull(versionId, "versionId 不能为空");
        if (daysLate <= 0) {
            throw new IllegalArgumentException("daysLate 必须大于 0");
        }
        String sourceRule = SOURCE_RULE_LATE_REPORT_PREFIX + reportId;
        if (projectRiskRepository.findBySourceRule(sourceRule).isPresent()) {
            LOG.info("迟报风险已存在，跳过重复生成 sourceRule={}", sourceRule);
            return;
        }
        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BizException(ErrorCode.PROJ_001));

        ProjectRiskEntity risk = new ProjectRiskEntity();
        risk.setId(UuidUtils.formatCompact(UUID.randomUUID()));
        risk.setProjectId(projectId);
        risk.setSourceRule(sourceRule);
        risk.setRiskType(RISK_TYPE_SCHEDULE);
        risk.setTitle("周期报告迟报 " + daysLate + " 天");
        risk.setDescription("报告版本 " + versionId + " 超过截止日期提交");
        risk.setRiskLevel(ProjectRiskEntity.LEVEL_MEDIUM);
        risk.setOwnerId(project.getPiId());
        risk.setStatus(ProjectRiskEntity.STATUS_OPEN);
        risk.setIdempotencyKey(sourceRule);
        try {
            projectRiskRepository.insert(risk);
        } catch (DuplicateKeyException ex) {
            LOG.info("迟报风险并发重复插入，按幂等处理 sourceRule={}", sourceRule);
            return;
        }

        auditRecorder.record(new AuditEvent(
                TraceIds.currentOrUntracked(),
                null,
                null,
                AUDIT_RISK_LATE_REPORT,
                OBJECT_RISK,
                UuidUtils.parse(risk.getId()),
                null,
                null,
                null,
                "周期报告迟报 " + daysLate + " 天，自动生成风险",
                null,
                null,
                clock.instant()));
    }

    private void authorize(ProjectEntity project, AuthContext auth, String permissionCode) {
        ResourceRef resource = new ResourceRef(
                ResourceTypes.PROJECT,
                UuidUtils.parse(project.getId()),
                UuidUtils.parse(project.getPiId()),
                UuidUtils.parse(project.getDeptId()),
                activeMemberIds(project.getId()));
        AuthDecision decision = authorizationService.check(auth, Action.of(permissionCode), resource);
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

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BizException(ErrorCode.COMMON_400, message);
        }
        return value.trim();
    }
}
