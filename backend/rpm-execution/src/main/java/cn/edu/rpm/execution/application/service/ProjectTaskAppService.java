package cn.edu.rpm.execution.application.service;

import cn.edu.rpm.audit.domain.model.AuditEvent;
import cn.edu.rpm.audit.domain.service.AuditRecorder;
import cn.edu.rpm.common.domain.model.OutboxEvent;
import cn.edu.rpm.common.domain.port.OutboxAppendPort;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.common.util.TraceIds;
import cn.edu.rpm.common.util.UuidUtils;
import cn.edu.rpm.execution.application.dto.CompleteProjectTaskCmd;
import cn.edu.rpm.execution.application.dto.CreateProjectTaskCmd;
import cn.edu.rpm.execution.application.dto.ProjectTaskCreated;
import cn.edu.rpm.execution.application.dto.ProjectTaskStateChanged;
import cn.edu.rpm.execution.application.dto.ProjectTaskView;
import cn.edu.rpm.execution.domain.model.ExecutionPermissions;
import cn.edu.rpm.execution.infrastructure.entity.DeliverableEntity;
import cn.edu.rpm.execution.infrastructure.entity.ProjectTaskEntity;
import cn.edu.rpm.execution.infrastructure.repository.DeliverableRepository;
import cn.edu.rpm.execution.infrastructure.repository.ProjectTaskRepository;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 轻量任务应用服务（T-EXEC-009；技术文档 09 §4）。
 *
 * <p>状态机 {@code TODO → IN_PROGRESS → DONE}，{@code TODO|IN_PROGRESS → CANCELLED}，
 * {@code DONE} 不可逆；迁移只经 {@link ProjectTaskEntity} 显式方法，非法迁移映射为
 * {@code RPM-EXEC-006}。授权口径：创建/分派需 {@code exec:task:assign} + 项目身份，
 * start/complete 仅 assignee 或 PI（按人员 ID），cancel 仅 PI；未授权统一
 * {@code RPM-EXEC-005}（不泄露任务存在性）。完成时关联交付物必须属于同项目且
 * {@code ACTIVE}，否则 {@code RPM-EXEC-004}。乐观锁冲突返回 {@code RPM-COMMON-409}。</p>
 */
@Service
public class ProjectTaskAppService {

    /** Outbox 事件：任务已分派。 */
    public static final String EVENT_TASK_ASSIGNED = "TASK_ASSIGNED";

    /** Outbox 事件：任务已完成。 */
    public static final String EVENT_TASK_COMPLETED = "TASK_COMPLETED";

    /** Outbox 事件：任务已取消。 */
    public static final String EVENT_TASK_CANCELLED = "TASK_CANCELLED";

    /** 审计动作：创建任务。 */
    public static final String AUDIT_TASK_CREATED = "TASK_CREATED";

    /** 审计动作：开始任务。 */
    public static final String AUDIT_TASK_STARTED = "TASK_STARTED";

    /** 审计动作：完成任务。 */
    public static final String AUDIT_TASK_COMPLETED = "TASK_COMPLETED";

    /** 审计动作：取消任务。 */
    public static final String AUDIT_TASK_CANCELLED = "TASK_CANCELLED";

    /** 聚合/对象类型：轻任务。 */
    static final String OBJECT_TASK = "TASK";

    /** 标题最大长度（DDL varchar(256)）。 */
    static final int MAX_TITLE_LENGTH = 256;

    /** 描述最大长度（DDL varchar(2000)）。 */
    static final int MAX_DESCRIPTION_LENGTH = 2000;

    private final ProjectTaskRepository projectTaskRepository;

    private final ProjectRepository projectRepository;

    private final ProjectMemberRepository projectMemberRepository;

    private final DeliverableRepository deliverableRepository;

    private final AuthorizationService authorizationService;

    private final OutboxAppendPort outboxAppendPort;

    private final AuditRecorder auditRecorder;

    private final Clock clock;

    /**
     * 构造轻任务服务。
     *
     * @param projectTaskRepository   任务仓储
     * @param projectRepository       项目仓储
     * @param projectMemberRepository 成员仓储
     * @param deliverableRepository   交付物仓储
     * @param authorizationService    统一授权服务
     * @param outboxAppendPort        Outbox 追加端口
     * @param auditRecorder           审计记录器
     * @param clock                   时间基准
     */
    public ProjectTaskAppService(ProjectTaskRepository projectTaskRepository,
                                 ProjectRepository projectRepository,
                                 ProjectMemberRepository projectMemberRepository,
                                 DeliverableRepository deliverableRepository,
                                 AuthorizationService authorizationService,
                                 OutboxAppendPort outboxAppendPort,
                                 AuditRecorder auditRecorder,
                                 Clock clock) {
        this.projectTaskRepository = Objects.requireNonNull(projectTaskRepository,
                "projectTaskRepository 不能为空");
        this.projectRepository = Objects.requireNonNull(projectRepository, "projectRepository 不能为空");
        this.projectMemberRepository = Objects.requireNonNull(projectMemberRepository,
                "projectMemberRepository 不能为空");
        this.deliverableRepository = Objects.requireNonNull(deliverableRepository,
                "deliverableRepository 不能为空");
        this.authorizationService = Objects.requireNonNull(authorizationService,
                "authorizationService 不能为空");
        this.outboxAppendPort = Objects.requireNonNull(outboxAppendPort, "outboxAppendPort 不能为空");
        this.auditRecorder = Objects.requireNonNull(auditRecorder, "auditRecorder 不能为空");
        this.clock = Objects.requireNonNull(clock, "clock 不能为空");
    }

    /**
     * 创建并分派任务（TC-TSK-001）。
     *
     * @param projectId 项目 ID
     * @param cmd       创建命令
     * @param auth      当前请求授权上下文
     * @return 任务 ID、状态与版本
     * @throws BizException 项目不存在 {@code RPM-PROJ-001}；无权限 {@code RPM-COMMON-403}；
     *                      参数/处理人非法 {@code RPM-COMMON-400}；
     *                      交付物无效 {@code RPM-EXEC-004}
     */
    @Transactional
    public ProjectTaskCreated create(String projectId, CreateProjectTaskCmd cmd, AuthContext auth) {
        Objects.requireNonNull(cmd, "cmd 不能为空");
        Objects.requireNonNull(auth, "auth 不能为空");
        ProjectEntity project = loadProject(projectId);
        Set<UUID> members = activeMemberIds(projectId);
        authorize(project, members, auth, ExecutionPermissions.TASK_ASSIGN);

        String title = requireTitle(cmd.title());
        String description = optionalText(cmd.description(), MAX_DESCRIPTION_LENGTH,
                "任务描述不能超过 " + MAX_DESCRIPTION_LENGTH + " 字符");
        UUID assignee = requireAssignee(cmd.assigneeId(), members);
        DeliverableEntity deliverable = cmd.deliverableId() == null || cmd.deliverableId().isBlank()
                ? null : requireActiveDeliverable(project.getId(), cmd.deliverableId());

        ProjectTaskEntity task = new ProjectTaskEntity();
        task.setId(UuidUtils.formatCompact(UUID.randomUUID()));
        task.setProjectId(project.getId());
        task.setTitle(title);
        task.setDescription(description);
        task.setAssigneeId(UuidUtils.formatCompact(assignee));
        task.setDueDate(cmd.dueDate());
        task.setStatus(ProjectTaskEntity.STATUS_TODO);
        if (deliverable != null) {
            task.setDeliverableId(deliverable.getId());
        }
        projectTaskRepository.insert(task);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("task_id", task.getId());
        payload.put("project_id", project.getId());
        payload.put("assignee_id", task.getAssigneeId());
        payload.put("title", task.getTitle());
        if (task.getDueDate() != null) {
            payload.put("due_date", task.getDueDate().toString());
        }
        outboxAppendPort.append(new OutboxEvent(
                UUID.randomUUID(),
                OBJECT_TASK,
                UuidUtils.parse(task.getId()),
                EVENT_TASK_ASSIGNED,
                payload,
                TraceIds.currentOrUntracked(),
                EVENT_TASK_ASSIGNED + ":" + task.getId()));

        audit(task, auth, AUDIT_TASK_CREATED, "创建并分派任务：" + task.getTitle());
        return new ProjectTaskCreated(task.getId(), task.getStatus(), task.getAssigneeId(),
                task.getVersion());
    }

    /**
     * 开始任务：{@code TODO → IN_PROGRESS}（TC-TSK-005）。
     *
     * @param taskId 任务 ID
     * @param auth   当前请求授权上下文
     * @return 迁移后的状态与版本
     * @throws BizException 任务不存在或非授权人 {@code RPM-EXEC-005}；
     *                      非法迁移 {@code RPM-EXEC-006}；并发冲突 {@code RPM-COMMON-409}
     */
    @Transactional
    public ProjectTaskStateChanged start(String taskId, AuthContext auth) {
        Objects.requireNonNull(auth, "auth 不能为空");
        ProjectTaskEntity task = requireTask(taskId);
        ProjectEntity project = loadProject(task.getProjectId());
        requireAssigneeOrPi(task, project, auth);

        try {
            task.start();
        } catch (IllegalStateException ex) {
            throw new BizException(ErrorCode.EXEC_006, ex.getMessage());
        }
        if (projectTaskRepository.update(task) == 0) {
            throw new BizException(ErrorCode.COMMON_409, "任务已被并发处理，请刷新后重试");
        }
        audit(task, auth, AUDIT_TASK_STARTED, "开始任务：" + task.getTitle());
        return new ProjectTaskStateChanged(task.getId(), task.getStatus(), task.getVersion());
    }

    /**
     * 完成任务：{@code TODO|IN_PROGRESS → DONE}（TC-TSK-003 / TC-TSK-004 / TC-TSK-006）。
     *
     * @param taskId 任务 ID
     * @param cmd    完成命令（可关联同项目 ACTIVE 交付物）
     * @param auth   当前请求授权上下文
     * @return 迁移后的状态与版本
     * @throws BizException 任务不存在或非授权人 {@code RPM-EXEC-005}；
     *                      交付物无效 {@code RPM-EXEC-004}；
     *                      非法迁移 {@code RPM-EXEC-006}；并发冲突 {@code RPM-COMMON-409}
     */
    @Transactional
    public ProjectTaskStateChanged complete(String taskId, CompleteProjectTaskCmd cmd,
                                            AuthContext auth) {
        Objects.requireNonNull(cmd, "cmd 不能为空");
        Objects.requireNonNull(auth, "auth 不能为空");
        ProjectTaskEntity task = requireTask(taskId);
        ProjectEntity project = loadProject(task.getProjectId());
        requireAssigneeOrPi(task, project, auth);

        if (cmd.deliverableId() != null && !cmd.deliverableId().isBlank()) {
            DeliverableEntity deliverable = requireActiveDeliverable(task.getProjectId(),
                    cmd.deliverableId());
            task.setDeliverableId(deliverable.getId());
        }
        try {
            task.complete(OffsetDateTime.now(clock));
        } catch (IllegalStateException ex) {
            throw new BizException(ErrorCode.EXEC_006, ex.getMessage());
        }
        if (projectTaskRepository.update(task) == 0) {
            throw new BizException(ErrorCode.COMMON_409, "任务已被并发完成，请刷新后重试");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("task_id", task.getId());
        payload.put("project_id", task.getProjectId());
        payload.put("pi_id", project.getPiId());
        payload.put("assignee_id", task.getAssigneeId());
        payload.put("title", task.getTitle());
        if (task.getDeliverableId() != null) {
            payload.put("deliverable_id", task.getDeliverableId());
        }
        outboxAppendPort.append(new OutboxEvent(
                UUID.randomUUID(),
                OBJECT_TASK,
                UuidUtils.parse(task.getId()),
                EVENT_TASK_COMPLETED,
                payload,
                TraceIds.currentOrUntracked(),
                EVENT_TASK_COMPLETED + ":" + task.getId()));

        audit(task, auth, AUDIT_TASK_COMPLETED, "完成任务：" + task.getTitle());
        return new ProjectTaskStateChanged(task.getId(), task.getStatus(), task.getVersion());
    }

    /**
     * 取消任务：{@code TODO|IN_PROGRESS → CANCELLED}，仅 PI（TC-TSK-007）。
     *
     * @param taskId 任务 ID
     * @param reason 取消理由（必填）
     * @param auth   当前请求授权上下文
     * @return 迁移后的状态与版本
     * @throws BizException 任务不存在或非 PI {@code RPM-EXEC-005}；
     *                      理由缺失 {@code RPM-COMMON-400}；
     *                      非法迁移 {@code RPM-EXEC-006}；并发冲突 {@code RPM-COMMON-409}
     */
    @Transactional
    public ProjectTaskStateChanged cancel(String taskId, String reason, AuthContext auth) {
        Objects.requireNonNull(auth, "auth 不能为空");
        ProjectTaskEntity task = requireTask(taskId);
        ProjectEntity project = loadProject(task.getProjectId());
        requirePi(project, auth);
        if (reason == null || reason.isBlank()) {
            throw new BizException(ErrorCode.COMMON_400, "取消任务必须填写理由");
        }
        String trimmedReason = reason.trim();

        try {
            task.cancel();
        } catch (IllegalStateException ex) {
            throw new BizException(ErrorCode.EXEC_006, ex.getMessage());
        }
        if (projectTaskRepository.update(task) == 0) {
            throw new BizException(ErrorCode.COMMON_409, "任务已被并发处理，请刷新后重试");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("task_id", task.getId());
        payload.put("project_id", task.getProjectId());
        payload.put("pi_id", project.getPiId());
        payload.put("assignee_id", task.getAssigneeId());
        payload.put("title", task.getTitle());
        payload.put("reason", trimmedReason);
        outboxAppendPort.append(new OutboxEvent(
                UUID.randomUUID(),
                OBJECT_TASK,
                UuidUtils.parse(task.getId()),
                EVENT_TASK_CANCELLED,
                payload,
                TraceIds.currentOrUntracked(),
                EVENT_TASK_CANCELLED + ":" + task.getId()));

        audit(task, auth, AUDIT_TASK_CANCELLED,
                "取消任务：" + task.getTitle() + "；理由：" + trimmedReason);
        return new ProjectTaskStateChanged(task.getId(), task.getStatus(), task.getVersion());
    }

    /**
     * 查询项目任务列表。
     *
     * @param projectId 项目 ID
     * @param status    状态过滤（可空）
     * @param auth      当前请求授权上下文
     * @return 任务视图列表（按截止日期升序）
     * @throws BizException 项目不存在 {@code RPM-PROJ-001}；无权限 {@code RPM-COMMON-403}；
     *                      状态非法 {@code RPM-COMMON-400}
     */
    @Transactional(readOnly = true)
    public List<ProjectTaskView> list(String projectId, String status, AuthContext auth) {
        Objects.requireNonNull(auth, "auth 不能为空");
        ProjectEntity project = loadProject(projectId);
        authorize(project, activeMemberIds(projectId), auth, ExecutionPermissions.VIEW);

        String filter = status == null || status.isBlank() ? null : status.trim();
        if (filter != null && !Set.of(ProjectTaskEntity.STATUS_TODO,
                ProjectTaskEntity.STATUS_IN_PROGRESS, ProjectTaskEntity.STATUS_DONE,
                ProjectTaskEntity.STATUS_CANCELLED).contains(filter)) {
            throw new BizException(ErrorCode.COMMON_400, "任务状态非法：" + filter);
        }
        List<ProjectTaskEntity> rows = filter == null
                ? projectTaskRepository.findByProjectId(project.getId())
                : projectTaskRepository.findByProjectIdAndStatus(project.getId(), filter);
        return rows.stream().map(ProjectTaskAppService::toView).toList();
    }

    private ProjectTaskEntity requireTask(String taskId) {
        return projectTaskRepository.findById(requireUuid(taskId, "taskId"))
                .orElseThrow(() -> new BizException(ErrorCode.EXEC_005));
    }

    private ProjectEntity loadProject(String projectId) {
        return projectRepository.findById(requireUuid(projectId, "projectId"))
                .orElseThrow(() -> new BizException(ErrorCode.PROJ_001));
    }

    private static String requireUuid(String value, String field) {
        try {
            return UuidUtils.formatCompact(UuidUtils.parse(value));
        } catch (IllegalArgumentException ex) {
            throw new BizException(ErrorCode.COMMON_400, field + " 必须是合法 UUID");
        }
    }

    private void requireAssigneeOrPi(ProjectTaskEntity task, ProjectEntity project,
                                     AuthContext auth) {
        UUID actor = auth.userId();
        if (actor == null
                || (!actor.equals(UuidUtils.parse(task.getAssigneeId()))
                && !actor.equals(UuidUtils.parse(project.getPiId())))) {
            throw new BizException(ErrorCode.EXEC_005);
        }
    }

    private static void requirePi(ProjectEntity project, AuthContext auth) {
        if (auth.userId() == null || !auth.userId().equals(UuidUtils.parse(project.getPiId()))) {
            throw new BizException(ErrorCode.EXEC_005);
        }
    }

    private void authorize(ProjectEntity project, Set<UUID> members, AuthContext auth,
                           String permission) {
        ResourceRef resource = new ResourceRef(
                ResourceTypes.PROJECT,
                UuidUtils.parse(project.getId()),
                UuidUtils.parse(project.getPiId()),
                UuidUtils.parse(project.getDeptId()),
                members);
        AuthDecision decision = authorizationService.check(auth, Action.of(permission), resource);
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

    private DeliverableEntity requireActiveDeliverable(String projectId, String deliverableId) {
        UUID uuid;
        try {
            uuid = UuidUtils.parse(deliverableId);
        } catch (IllegalArgumentException ex) {
            throw new BizException(ErrorCode.EXEC_004, "交付物 ID 非法");
        }
        DeliverableEntity deliverable = deliverableRepository
                .findById(UuidUtils.formatCompact(uuid))
                .orElseThrow(() -> new BizException(ErrorCode.EXEC_004, "交付物不存在"));
        if (!DeliverableEntity.STATUS_ACTIVE.equals(deliverable.getStatus())
                || !UuidUtils.sameId(deliverable.getProjectId(), projectId)) {
            throw new BizException(ErrorCode.EXEC_004, "交付物必须属于同项目且有效");
        }
        return deliverable;
    }

    private UUID requireAssignee(String assigneeId, Set<UUID> members) {
        UUID assignee;
        try {
            assignee = UuidUtils.parse(assigneeId);
        } catch (IllegalArgumentException ex) {
            throw new BizException(ErrorCode.COMMON_400, "assigneeId 非法");
        }
        if (!members.contains(assignee)) {
            throw new BizException(ErrorCode.COMMON_400, "任务处理人必须是项目有效成员");
        }
        return assignee;
    }

    private static String requireTitle(String value) {
        if (value == null || value.isBlank()) {
            throw new BizException(ErrorCode.COMMON_400, "任务标题不能为空");
        }
        String title = value.trim();
        if (title.length() > MAX_TITLE_LENGTH) {
            throw new BizException(ErrorCode.COMMON_400,
                    "任务标题不能超过 " + MAX_TITLE_LENGTH + " 字符");
        }
        return title;
    }

    private static String optionalText(String value, int maxLength, String message) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.trim();
        if (text.length() > maxLength) {
            throw new BizException(ErrorCode.COMMON_400, message);
        }
        return text;
    }

    private static ProjectTaskView toView(ProjectTaskEntity task) {
        return new ProjectTaskView(task.getId(), task.getProjectId(), task.getTitle(),
                task.getDescription(), task.getAssigneeId(), task.getDueDate(),
                task.getStatus(), task.getDeliverableId(), task.getCompletedAt(),
                task.getCreatedAt(), task.getVersion());
    }

    private void audit(ProjectTaskEntity task, AuthContext auth, String action, String reason) {
        auditRecorder.record(new AuditEvent(
                TraceIds.currentOrUntracked(),
                auth.userId(),
                null,
                action,
                OBJECT_TASK,
                UuidUtils.parse(task.getId()),
                task.getVersion(),
                null,
                null,
                reason,
                null,
                null,
                clock.instant()));
    }
}
