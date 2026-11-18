package cn.edu.rpm.project.domain.service;

import cn.edu.rpm.application.domain.model.ProjectCreationCommand;
import cn.edu.rpm.audit.domain.model.AuditEvent;
import cn.edu.rpm.audit.domain.service.AuditRecorder;
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
import cn.edu.rpm.project.domain.model.ProjectPermissions;
import cn.edu.rpm.project.domain.port.MilestoneEvidencePort;
import cn.edu.rpm.project.infrastructure.entity.MilestoneEntity;
import cn.edu.rpm.project.infrastructure.entity.ProjectBaselineEntity;
import cn.edu.rpm.project.infrastructure.entity.ProjectEntity;
import cn.edu.rpm.project.infrastructure.entity.ProjectMemberEntity;
import cn.edu.rpm.project.infrastructure.repository.MilestoneRepository;
import cn.edu.rpm.project.infrastructure.repository.ProjectBaselineRepository;
import cn.edu.rpm.project.infrastructure.repository.ProjectMemberRepository;
import cn.edu.rpm.project.infrastructure.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 里程碑领域服务（T-PRJ-014；技术文档 05 §5.1、07 §3.2）。
 *
 * <p>立项时按批准基线批量生成里程碑（同一 {@code baseline_id}）；完成里程碑前
 * 必须存在完成判定（{@code completion_criteria}，缺失抛 {@code RPM-EXEC-003}）
 * 与有效证据（缺失抛 {@code RPM-EXEC-004}），完成后写入 {@code actual_date}。</p>
 *
 * <p>本服务不修改 {@code plan_date}；延期只由 {@code MilestoneDelayAppService}
 * 在审批批准后更新 {@code current_plan_date}（08 §6）。</p>
 */
@Service
public class MilestoneService {

    /** 审计动作：里程碑完成。 */
    public static final String AUDIT_MILESTONE_COMPLETED = "MILESTONE_COMPLETED";

    /** 审计对象类型：里程碑。 */
    static final String OBJECT_MILESTONE = "MILESTONE";

    private static final int CODE_MAX_LENGTH = 64;

    private static final int TITLE_MAX_LENGTH = 512;

    private final MilestoneRepository milestoneRepository;

    private final ProjectRepository projectRepository;

    private final ProjectBaselineRepository projectBaselineRepository;

    private final ProjectMemberRepository projectMemberRepository;

    private final MilestoneEvidencePort milestoneEvidencePort;

    private final AuthorizationService authorizationService;

    private final AuditRecorder auditRecorder;

    private final Clock clock;

    /**
     * 构造里程碑服务。
     *
     * @param milestoneRepository      里程碑仓储
     * @param projectRepository        项目仓储
     * @param projectBaselineRepository 基线仓储
     * @param projectMemberRepository  成员仓储（授权装载）
     * @param milestoneEvidencePort    完成证据端口
     * @param authorizationService     统一授权服务
     * @param auditRecorder            审计记录器
     * @param clock                    时间基准
     */
    public MilestoneService(MilestoneRepository milestoneRepository,
                            ProjectRepository projectRepository,
                            ProjectBaselineRepository projectBaselineRepository,
                            ProjectMemberRepository projectMemberRepository,
                            MilestoneEvidencePort milestoneEvidencePort,
                            AuthorizationService authorizationService,
                            AuditRecorder auditRecorder,
                            Clock clock) {
        this.milestoneRepository = Objects.requireNonNull(milestoneRepository,
                "milestoneRepository 不能为空");
        this.projectRepository = Objects.requireNonNull(projectRepository, "projectRepository 不能为空");
        this.projectBaselineRepository = Objects.requireNonNull(projectBaselineRepository,
                "projectBaselineRepository 不能为空");
        this.projectMemberRepository = Objects.requireNonNull(projectMemberRepository,
                "projectMemberRepository 不能为空");
        this.milestoneEvidencePort = Objects.requireNonNull(milestoneEvidencePort,
                "milestoneEvidencePort 不能为空");
        this.authorizationService = Objects.requireNonNull(authorizationService,
                "authorizationService 不能为空");
        this.auditRecorder = Objects.requireNonNull(auditRecorder, "auditRecorder 不能为空");
        this.clock = Objects.requireNonNull(clock, "clock 不能为空");
    }

    /**
     * 按基线批量生成里程碑（TC-EXEC-001）。
     *
     * <p>调用方为立项创建链路（已完成授权与状态校验），本方法只做数据一致性校验：
     * 项目与基线存在且归属一致；编码在基线内唯一；计划日期、名称、编码必填。</p>
     *
     * @param projectId  项目 ID
     * @param baselineId 基线 ID
     * @param plans      里程碑计划（可为空，空则生成 0 条）
     * @return 已插入的里程碑（含生成的主键）
     * @throws BizException 项目不存在 {@code RPM-PROJ-001}；基线缺失或不属于项目
     *                      {@code RPM-PROJ-004}；计划非法 {@code RPM-COMMON-400}
     */
    @Transactional
    public List<MilestoneEntity> createFromBaseline(String projectId, String baselineId,
                                                    List<ProjectCreationCommand.MilestonePlan> plans) {
        projectRepository.findById(projectId).orElseThrow(() -> new BizException(ErrorCode.PROJ_001));
        ProjectBaselineEntity baseline = projectBaselineRepository.findById(baselineId)
                .filter(item -> UuidUtils.sameId(projectId, item.getProjectId()))
                .orElseThrow(() -> new BizException(ErrorCode.PROJ_004, "项目基线缺失"));

        if (plans == null || plans.isEmpty()) {
            return List.of();
        }

        Set<String> codes = new HashSet<>();
        List<MilestoneEntity> milestones = new ArrayList<>(plans.size());
        for (ProjectCreationCommand.MilestonePlan plan : plans) {
            if (plan == null) {
                throw new BizException(ErrorCode.COMMON_400, "里程碑计划不能为空");
            }
            String code = requireText(plan.code(), "里程碑编码不能为空");
            if (code.length() > CODE_MAX_LENGTH) {
                throw new BizException(ErrorCode.COMMON_400, "里程碑编码长度不能超过 64");
            }
            if (!codes.add(code)) {
                throw new BizException(ErrorCode.COMMON_400, "同一基线内里程碑编码重复：" + code);
            }
            String title = requireText(plan.title(), "里程碑名称不能为空");
            if (title.length() > TITLE_MAX_LENGTH) {
                throw new BizException(ErrorCode.COMMON_400, "里程碑名称长度不能超过 512");
            }
            if (plan.planDate() == null) {
                throw new BizException(ErrorCode.COMMON_400, "里程碑计划日期不能为空");
            }

            MilestoneEntity milestone = new MilestoneEntity();
            milestone.setProjectId(projectId);
            milestone.setBaselineId(baseline.getId());
            milestone.setCode(code);
            milestone.setTitle(title);
            milestone.setPlanDate(plan.planDate());
            milestone.setCurrentPlanDate(plan.planDate());
            milestone.setOwnerId(plan.ownerId());
            milestone.setCompletionCriteria(plan.completionCriteria());
            milestone.setStatus(MilestoneEntity.STATUS_PLANNED);
            milestones.add(milestone);
        }
        milestoneRepository.insertBatch(milestones);
        return milestones;
    }

    /**
     * 标记里程碑完成（TC-EXEC-002 / TC-EXEC-003）。
     *
     * @param milestoneId 里程碑 ID
     * @param actualDate  实际完成日期；为空取当前日期
     * @param auth        当前请求授权上下文
     * @return 完成后的里程碑
     * @throws BizException 里程碑不存在 {@code RPM-COMMON-404}；无权限 {@code RPM-COMMON-403}；
     *                      缺少完成判定 {@code RPM-EXEC-003}；缺少证据 {@code RPM-EXEC-004}；
     *                      状态不允许 {@code RPM-COMMON-422}
     */
    @Transactional
    public MilestoneEntity complete(String milestoneId, LocalDate actualDate, AuthContext auth) {
        Objects.requireNonNull(auth, "auth 不能为空");
        MilestoneEntity milestone = milestoneRepository.selectForUpdate(milestoneId)
                .orElseThrow(() -> new BizException(ErrorCode.COMMON_404, "里程碑不存在"));
        ProjectEntity project = projectRepository.findById(milestone.getProjectId())
                .orElseThrow(() -> new BizException(ErrorCode.PROJ_001));
        authorize(project, auth, ProjectPermissions.MILESTONE_MANAGE);

        if (milestone.getCompletionCriteria() == null
                || milestone.getCompletionCriteria().isBlank()) {
            throw new BizException(ErrorCode.EXEC_003, "里程碑缺少完成判定，不能标记完成");
        }
        if (!milestoneEvidencePort.hasEvidence(milestone.getProjectId(), milestone.getId())) {
            throw new BizException(ErrorCode.EXEC_004, "里程碑缺少完成证据，请先上传交付物");
        }

        LocalDate date = actualDate == null ? LocalDate.now(clock) : actualDate;
        try {
            milestone.complete(date);
        } catch (IllegalStateException ex) {
            throw new BizException(ErrorCode.COMMON_422, ex.getMessage());
        }
        int updated = milestoneRepository.update(milestone);
        if (updated == 0) {
            throw new BizException(ErrorCode.COMMON_409, "里程碑已被并发修改，请刷新后重试");
        }

        auditRecorder.record(new AuditEvent(
                TraceIds.currentOrUntracked(),
                auth.userId(),
                null,
                AUDIT_MILESTONE_COMPLETED,
                OBJECT_MILESTONE,
                UuidUtils.parse(milestone.getId()),
                milestone.getVersion(),
                null,
                null,
                "里程碑完成 actualDate=" + date,
                null,
                null,
                clock.instant()));
        return milestone;
    }

    /**
     * 查询项目当前生效基线的里程碑（项目详情页签）。
     *
     * @param projectId 项目 ID
     * @param auth      当前请求授权上下文
     * @return 里程碑列表；无生效基线或未配置里程碑返回空列表
     * @throws BizException 项目不存在 {@code RPM-PROJ-001}；无权限 {@code RPM-COMMON-403}
     */
    @Transactional(readOnly = true)
    public List<MilestoneEntity> list(String projectId, AuthContext auth) {
        Objects.requireNonNull(auth, "auth 不能为空");
        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BizException(ErrorCode.PROJ_001));
        authorize(project, auth, ProjectPermissions.VIEW);
        if (project.getCurrentBaselineId() == null) {
            return List.of();
        }
        return milestoneRepository.findByProjectIdAndBaselineId(projectId,
                project.getCurrentBaselineId());
    }

    private void authorize(ProjectEntity project, AuthContext auth, String permissionCode) {
        ResourceRef resource = new ResourceRef(
                ResourceTypes.PROJECT,
                UuidUtils.parse(project.getId()),
                UuidUtils.parse(project.getPiId()),
                UuidUtils.parse(project.getDeptId()),
                activeMemberIds(project.getId()));
        AuthDecision decision = authorizationService.check(auth,
                Action.of(permissionCode), resource);
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
