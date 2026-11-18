package cn.edu.rpm.project.domain.service;

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
import cn.edu.rpm.project.application.dto.ProjectStartConditions;
import cn.edu.rpm.project.application.dto.ProjectStateChanged;
import cn.edu.rpm.project.domain.model.ProjectPermissions;
import cn.edu.rpm.project.domain.model.ProjectTransitionAction;
import cn.edu.rpm.project.infrastructure.entity.ProjectEntity;
import cn.edu.rpm.project.infrastructure.entity.ProjectMemberEntity;
import cn.edu.rpm.project.infrastructure.repository.ProjectMemberRepository;
import cn.edu.rpm.project.infrastructure.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 项目状态机领域服务（T-PRJ-012；技术文档 01 §2.4.2、07 §3.1/§3.4）。
 *
 * <p>所有状态迁移都经过：行级锁（{@code SELECT ... FOR UPDATE}）→ 应用服务授权
 * （{@code project:state:change}，非仅 Controller 粗筛）→ 实体显式迁移方法 →
 * 乐观锁更新 → 关键审计 {@code PROJECT_STATE_CHANGED}。非法迁移统一转换为
 * {@code RPM-PROJ-002}，不泄露内部状态机细节。</p>
 *
 * <p>事务边界：本类方法加入调用方事务；禁止在本类内调用外部 HTTP。</p>
 */
@Service
public class ProjectLifecycleService {

    /** 审计动作：项目状态变更。 */
    public static final String AUDIT_STATE_CHANGED = "PROJECT_STATE_CHANGED";

    /** 审计对象类型：项目。 */
    static final String OBJECT_PROJECT = "PROJECT";

    private static final String DEFAULT_COMPLETE_REASON = "验收通过结题";

    private final ProjectRepository projectRepository;

    private final ProjectMemberRepository projectMemberRepository;

    private final AuthorizationService authorizationService;

    private final AuditRecorder auditRecorder;

    private final Clock clock;

    /**
     * 构造项目状态机服务。
     *
     * @param projectRepository       项目仓储
     * @param projectMemberRepository 项目成员仓储（授权判定装载成员身份）
     * @param authorizationService    统一授权服务
     * @param auditRecorder           审计记录器
     * @param clock                   时间基准
     */
    public ProjectLifecycleService(ProjectRepository projectRepository,
                                   ProjectMemberRepository projectMemberRepository,
                                   AuthorizationService authorizationService,
                                   AuditRecorder auditRecorder,
                                   Clock clock) {
        this.projectRepository = Objects.requireNonNull(projectRepository, "projectRepository 不能为空");
        this.projectMemberRepository = Objects.requireNonNull(projectMemberRepository,
                "projectMemberRepository 不能为空");
        this.authorizationService = Objects.requireNonNull(authorizationService,
                "authorizationService 不能为空");
        this.auditRecorder = Objects.requireNonNull(auditRecorder, "auditRecorder 不能为空");
        this.clock = Objects.requireNonNull(clock, "clock 不能为空");
    }

    /**
     * 启动项目：{@code PENDING_START → EXECUTING}（TC-PRJ-005）。
     *
     * @param projectId  项目 ID
     * @param conditions 启动条件（负责人确认 + 启动审批）
     * @param auth       当前请求授权上下文
     * @return 迁移结果
     * @throws BizException 条件不齐备 {@code RPM-PROJ-002}；项目不存在 {@code RPM-PROJ-001}
     */
    @Transactional
    public ProjectStateChanged start(String projectId, ProjectStartConditions conditions, AuthContext auth) {
        if (conditions == null || !conditions.satisfied()) {
            throw new BizException(ErrorCode.PROJ_002, "启动条件不齐备（需负责人确认与启动审批）");
        }
        return change(projectId, auth, "启动", "启动项目", ProjectEntity::start);
    }

    /**
     * 暂停项目：{@code EXECUTING → SUSPENDED}（TC-PRJ-007）。
     *
     * @param projectId 项目 ID
     * @param reason    暂停原因（必填，写入审计可追溯）
     * @param auth      当前请求授权上下文
     * @return 迁移结果
     */
    @Transactional
    public ProjectStateChanged pause(String projectId, String reason, AuthContext auth) {
        String normalized = requireReason(reason, "暂停原因不能为空");
        return change(projectId, auth, "暂停", normalized, ProjectEntity::pause);
    }

    /**
     * 恢复项目：{@code SUSPENDED → EXECUTING}（TC-PRJ-008）。
     *
     * @param projectId 项目 ID
     * @param auth      当前请求授权上下文
     * @return 迁移结果
     */
    @Transactional
    public ProjectStateChanged resume(String projectId, AuthContext auth) {
        return change(projectId, auth, "恢复", "恢复项目", ProjectEntity::resume);
    }

    /**
     * 提交验收：{@code EXECUTING → ACCEPTING}（TC-PRJ-034）。
     *
     * @param projectId 项目 ID
     * @param auth      当前请求授权上下文
     * @return 迁移结果
     */
    @Transactional
    public ProjectStateChanged submitAcceptance(String projectId, AuthContext auth) {
        return change(projectId, auth, "提交验收", "提交验收", ProjectEntity::submitAcceptance);
    }

    /**
     * 验收退回整改：{@code ACCEPTING → EXECUTING}（TC-PRJ-031）。
     *
     * @param projectId 项目 ID
     * @param auth      当前请求授权上下文
     * @return 迁移结果
     */
    @Transactional
    public ProjectStateChanged returnToExecuting(String projectId, AuthContext auth) {
        return change(projectId, auth, "验收退回", "验收退回整改", ProjectEntity::returnToExecuting);
    }

    /**
     * 结题：{@code ACCEPTING → COMPLETED}，写入冻结信息（TC-PRJ-032）。
     *
     * @param projectId    项目 ID
     * @param frozenReason 冻结原因；空白取默认说明
     * @param auth         当前请求授权上下文
     * @return 迁移结果
     */
    @Transactional
    public ProjectStateChanged complete(String projectId, String frozenReason, AuthContext auth) {
        String reason = frozenReason == null || frozenReason.isBlank()
                ? DEFAULT_COMPLETE_REASON : frozenReason.trim();
        OffsetDateTime frozenAt = clock.instant().atZone(clock.getZone()).toOffsetDateTime();
        return change(projectId, auth, "结题", reason,
                project -> project.complete(frozenAt, reason));
    }

    /**
     * 终止项目：非终态 → {@code TERMINATED}（TC-PRJ-009～011、TC-PRJ-033）。
     *
     * @param projectId 项目 ID
     * @param reason    终止原因（必填）
     * @param auth      当前请求授权上下文
     * @return 迁移结果
     */
    @Transactional
    public ProjectStateChanged terminate(String projectId, String reason, AuthContext auth) {
        String normalized = requireReason(reason, "终止原因不能为空");
        return change(projectId, auth, "终止", normalized, project -> project.terminate(normalized));
    }

    /**
     * 统一状态迁移入口（供验收/测试按动作驱动；TC-PRJ-012/013/030/031）。
     *
     * <p>每个动作仍映射到实体显式迁移方法，非法迁移抛 {@code RPM-PROJ-002}。</p>
     *
     * @param projectId 项目 ID
     * @param action    迁移动作
     * @param reason    原因（暂停/终止/结题必填；其余可为空）
     * @param auth      当前请求授权上下文
     * @return 迁移结果
     */
    @Transactional
    public ProjectStateChanged transition(String projectId, ProjectTransitionAction action, String reason,
                                          AuthContext auth) {
        Objects.requireNonNull(action, "action 不能为空");
        return switch (action) {
            case PAUSE -> pause(projectId, reason, auth);
            case RESUME -> resume(projectId, auth);
            case SUBMIT_ACCEPTANCE -> submitAcceptance(projectId, auth);
            case RETURN_TO_EXECUTING -> returnToExecuting(projectId, auth);
            case COMPLETE -> complete(projectId, reason, auth);
            case TERMINATE -> terminate(projectId, reason, auth);
        };
    }

    private ProjectStateChanged change(String projectId, AuthContext auth, String actionLabel,
                                       String reason, Consumer<ProjectEntity> mutator) {
        Objects.requireNonNull(auth, "auth 不能为空");
        ProjectEntity project = projectRepository.selectForUpdate(projectId)
                .orElseThrow(() -> new BizException(ErrorCode.PROJ_001));
        authorize(project, auth);

        String before = project.getStatus();
        try {
            mutator.accept(project);
        } catch (IllegalStateException ex) {
            throw new BizException(ErrorCode.PROJ_002, ex.getMessage());
        }

        int updated = projectRepository.update(project);
        if (updated == 0) {
            throw new BizException(ErrorCode.COMMON_409, "项目已被并发修改，请刷新后重试");
        }

        auditRecorder.record(new AuditEvent(
                TraceIds.currentOrUntracked(),
                auth.userId(),
                null,
                AUDIT_STATE_CHANGED,
                OBJECT_PROJECT,
                UuidUtils.parse(project.getId()),
                project.getVersion(),
                null,
                null,
                before + " → " + project.getStatus() + "：" + actionLabel + "（" + reason + "）",
                null,
                null,
                clock.instant()));
        return new ProjectStateChanged(project.getId(), project.getStatus(), project.getVersion());
    }

    private void authorize(ProjectEntity project, AuthContext auth) {
        ResourceRef resource = new ResourceRef(
                ResourceTypes.PROJECT,
                UuidUtils.parse(project.getId()),
                UuidUtils.parse(project.getPiId()),
                UuidUtils.parse(project.getDeptId()),
                activeMemberIds(project.getId()));
        AuthDecision decision = authorizationService.check(auth,
                Action.of(ProjectPermissions.STATE_CHANGE), resource);
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

    private static String requireReason(String reason, String message) {
        if (reason == null || reason.isBlank()) {
            throw new BizException(ErrorCode.COMMON_400, message);
        }
        return reason.trim();
    }
}
