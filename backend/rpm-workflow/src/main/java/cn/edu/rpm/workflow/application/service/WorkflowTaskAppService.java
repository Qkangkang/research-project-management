package cn.edu.rpm.workflow.application.service;

import cn.edu.rpm.common.api.PageVO;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.common.util.UuidUtils;
import cn.edu.rpm.iam.application.dto.ApprovalContext;
import cn.edu.rpm.iam.application.dto.AuthContext;
import cn.edu.rpm.iam.domain.service.SeparationOfDutyGuard;
import cn.edu.rpm.workflow.application.dto.ActTaskCmd;
import cn.edu.rpm.workflow.application.dto.WithdrawCmd;
import cn.edu.rpm.workflow.application.registry.ApprovalResultHandlerRegistry;
import cn.edu.rpm.workflow.domain.dto.ApprovalResult;
import cn.edu.rpm.workflow.domain.dto.ApprovalTrail;
import cn.edu.rpm.workflow.domain.dto.CancelWorkflowCmd;
import cn.edu.rpm.workflow.domain.dto.CompleteTaskCmd;
import cn.edu.rpm.workflow.domain.dto.InitiatedInstanceView;
import cn.edu.rpm.workflow.domain.dto.TaskActionResult;
import cn.edu.rpm.workflow.domain.dto.TaskQuery;
import cn.edu.rpm.workflow.domain.dto.WorkflowInstance;
import cn.edu.rpm.workflow.domain.dto.WorkflowTaskView;
import cn.edu.rpm.workflow.domain.port.ApprovalResultHandler;
import cn.edu.rpm.workflow.domain.port.WorkflowPort;
import cn.edu.rpm.workflow.infrastructure.entity.WfCallbackLogEntity;
import cn.edu.rpm.workflow.infrastructure.entity.WfDelegationEntity;
import cn.edu.rpm.workflow.infrastructure.entity.WfInstanceEntity;
import cn.edu.rpm.workflow.infrastructure.entity.WfTaskEntity;
import cn.edu.rpm.workflow.infrastructure.repository.WfCallbackLogRepository;
import cn.edu.rpm.workflow.infrastructure.repository.WfDelegationRepository;
import cn.edu.rpm.workflow.infrastructure.repository.WfInstanceRepository;
import cn.edu.rpm.workflow.infrastructure.repository.WfTaskRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 审批任务应用服务（T-WF-014；技术文档 04 §3.11）。
 *
 * <p>处理链：校验任务归属/代理关系/版本/SOD → {@link WorkflowPort#completeTask} →
 * 按 {@code bizType} 分派 {@link ApprovalResultHandler} 业务副作用 → 返回首个结果。
 * 所有步骤在同一本地事务内完成；处理器抛出的业务异常（如 {@code RPM-WF-005}）
 * 会回滚任务推进与业务写入，保证「业务不变」。事务内禁止外部 HTTP 调用。</p>
 *
 * <p>幂等：重复 {@code requestId} 直接按 {@code wf_callback_log} 载荷重放首次结果，
 * 不再次调用流程引擎、任务版本不增加（TC-APP-023）。</p>
 */
@Service
public class WorkflowTaskAppService {

    /** 管理员重派任务的权限码。 */
    public static final String PERM_TASK_REASSIGN = "workflow:task:reassign";

    /** 终止/撤回审批实例的权限码。 */
    public static final String PERM_INSTANCE_CANCEL = "workflow:instance:cancel";

    /** 跨数据范围查看审批轨迹的权限码。 */
    public static final String PERM_INSTANCE_VIEW = "workflow:instance:view";

    private final WorkflowPort workflowPort;

    private final WfTaskRepository taskRepository;

    private final WfInstanceRepository instanceRepository;

    private final WfCallbackLogRepository callbackLogRepository;

    private final WfDelegationRepository delegationRepository;

    private final ApprovalResultHandlerRegistry handlerRegistry;

    private final SeparationOfDutyGuard separationOfDutyGuard;

    private final ObjectMapper objectMapper;

    /**
     * 构造应用服务。
     *
     * @param workflowPort          工作流端口
     * @param taskRepository        任务仓储
     * @param instanceRepository    实例仓储
     * @param callbackLogRepository 回调幂等日志仓储
     * @param delegationRepository  代理审批仓储
     * @param handlerRegistry       审批结果处理器注册器
     * @param separationOfDutyGuard 职责分离守护
     * @param objectMapper          JSON 解析器（重放首次结果载荷）
     */
    public WorkflowTaskAppService(WorkflowPort workflowPort,
                                  WfTaskRepository taskRepository,
                                  WfInstanceRepository instanceRepository,
                                  WfCallbackLogRepository callbackLogRepository,
                                  WfDelegationRepository delegationRepository,
                                  ApprovalResultHandlerRegistry handlerRegistry,
                                  SeparationOfDutyGuard separationOfDutyGuard,
                                  ObjectMapper objectMapper) {
        this.workflowPort = Objects.requireNonNull(workflowPort, "workflowPort 不能为空");
        this.taskRepository = Objects.requireNonNull(taskRepository, "taskRepository 不能为空");
        this.instanceRepository = Objects.requireNonNull(instanceRepository, "instanceRepository 不能为空");
        this.callbackLogRepository = Objects.requireNonNull(callbackLogRepository,
                "callbackLogRepository 不能为空");
        this.delegationRepository = Objects.requireNonNull(delegationRepository,
                "delegationRepository 不能为空");
        this.handlerRegistry = Objects.requireNonNull(handlerRegistry, "handlerRegistry 不能为空");
        this.separationOfDutyGuard = Objects.requireNonNull(separationOfDutyGuard,
                "separationOfDutyGuard 不能为空");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper 不能为空");
    }

    /**
     * 处理任务：通过/退回/拒绝，或管理员重派超时/异常任务。
     *
     * @param taskId 任务 ID
     * @param cmd    处理命令
     * @param auth   当前请求授权上下文
     * @return 动作后的任务/实例状态与下一节点任务
     * @throws BizException 任务/实例不存在 {@code RPM-COMMON-404}；非处理人 {@code RPM-COMMON-403}；
     *                      任务已处理/版本过期 {@code RPM-WF-004}；自审 {@code RPM-WF-003}；
     *                      处理器抛出的业务错误（如 {@code RPM-WF-005}）
     */
    @Transactional
    public TaskActionResult act(UUID taskId, ActTaskCmd cmd, AuthContext auth) {
        Objects.requireNonNull(taskId, "taskId 不能为空");
        Objects.requireNonNull(cmd, "cmd 不能为空");
        Objects.requireNonNull(auth, "auth 不能为空");
        WfTaskEntity task = requireTask(taskId);
        WfInstanceEntity instance = requireInstance(task.getInstanceId());

        String requestId = UuidUtils.formatCompact(cmd.requestId());
        Optional<WfCallbackLogEntity> processed = callbackLogRepository.findByRequestId(requestId);
        if (processed.isPresent()) {
            return replay(processed.get(), task, instance);
        }
        if (cmd.reassignTo() != null) {
            return reassign(task, instance, cmd, auth);
        }

        checkActor(task, cmd.onBehalfOf(), auth);
        requireActionable(task);
        if (!Objects.equals(task.getVersion(), cmd.expectedVersion())) {
            throw new BizException(ErrorCode.WF_004, "任务已被处理，版本不一致");
        }
        separationOfDutyGuard.checkApproval(new ApprovalContext(
                UuidUtils.parse(instance.getInitiatorId()), auth.userId(), null, null));

        workflowPort.completeTask(new CompleteTaskCmd(
                UuidUtils.parse(instance.getId()),
                UuidUtils.parse(task.getId()),
                auth.userId(),
                cmd.onBehalfOf(),
                cmd.action(),
                cmd.comment(),
                cmd.expectedVersion(),
                cmd.requestId()));

        WfTaskEntity completed = requireTask(taskId);
        WfInstanceEntity current = requireInstance(instance.getId());
        List<UUID> nextTaskIds = pendingTaskIds(instance.getId());
        ApprovalResult result = new ApprovalResult(
                UuidUtils.parse(instance.getId()),
                current.getBizType(),
                UuidUtils.parse(current.getBizId()),
                UuidUtils.parse(current.getBizVersionId()),
                cmd.action().name(),
                auth.userId(),
                cmd.onBehalfOf(),
                cmd.comment(),
                OffsetDateTime.now(),
                cmd.requestId(),
                nextTaskIds);
        ApprovalResultHandler handler = handlerRegistry.get(current.getBizType());
        switch (cmd.action()) {
            case APPROVED -> handler.onApproved(result);
            case RETURNED -> handler.onReturned(result);
            case REJECTED -> handler.onRejected(result);
        }
        return new TaskActionResult(taskId, UuidUtils.parse(current.getId()),
                completed.getStatus(), current.getStatus(), nextTaskIds);
    }

    /**
     * 认领任务：PENDING → PROCESSING（TC-APP-019）。
     *
     * @param taskId 任务 ID
     * @param auth   当前请求授权上下文
     * @return 认领后的结果（无下一节点）
     * @throws BizException 任务不存在 {@code RPM-COMMON-404}；非处理人 {@code RPM-COMMON-403}；
     *                      任务不可认领 {@code RPM-WF-004}
     */
    @Transactional
    public TaskActionResult claim(UUID taskId, AuthContext auth) {
        Objects.requireNonNull(taskId, "taskId 不能为空");
        Objects.requireNonNull(auth, "auth 不能为空");
        WfTaskEntity task = requireTask(taskId);
        if (!UuidUtils.sameId(task.getAssigneeId(), auth.userId())) {
            throw new BizException(ErrorCode.COMMON_403, "非任务处理人");
        }
        if (!WfTaskEntity.STATUS_PENDING.equals(task.getStatus())) {
            throw new BizException(ErrorCode.WF_004, "任务已被处理");
        }
        task.claim(OffsetDateTime.now());
        if (taskRepository.update(task) == 0) {
            throw new BizException(ErrorCode.WF_004, "任务已被他人处理");
        }
        WfInstanceEntity instance = requireInstance(task.getInstanceId());
        return new TaskActionResult(taskId, UuidUtils.parse(instance.getId()),
                task.getStatus(), instance.getStatus(), List.of());
    }

    /**
     * 撤回/终止审批实例（TC-APP-027）。
     *
     * @param cmd  撤回命令
     * @param auth 当前请求授权上下文
     * @throws BizException 实例不存在 {@code RPM-COMMON-404}；无权限 {@code RPM-COMMON-403}
     */
    @Transactional
    public void withdraw(WithdrawCmd cmd, AuthContext auth) {
        Objects.requireNonNull(cmd, "cmd 不能为空");
        Objects.requireNonNull(auth, "auth 不能为空");
        WfInstanceEntity instance = requireInstance(UuidUtils.formatCompact(cmd.instanceId()));
        boolean initiator = UuidUtils.sameId(instance.getInitiatorId(), auth.userId());
        if (!initiator && !auth.hasPermission(PERM_INSTANCE_CANCEL)) {
            throw new BizException(ErrorCode.COMMON_403, "仅申请人或管理员可撤回审批");
        }
        String requestId = UuidUtils.formatCompact(cmd.requestId());
        if (callbackLogRepository.findByRequestId(requestId).isPresent()) {
            return;
        }
        if (!WfInstanceEntity.STATUS_RUNNING.equals(instance.getStatus())) {
            if (WfInstanceEntity.STATUS_CANCELLED.equals(instance.getStatus())) {
                return;
            }
            throw new BizException(ErrorCode.COMMON_422, "审批已结束，不可撤回");
        }
        workflowPort.cancelInstance(new CancelWorkflowCmd(
                cmd.instanceId(), auth.userId(), cmd.reason(), cmd.requestId()));

        WfInstanceEntity current = requireInstance(instance.getId());
        ApprovalResult result = new ApprovalResult(
                UuidUtils.parse(current.getId()),
                current.getBizType(),
                UuidUtils.parse(current.getBizId()),
                UuidUtils.parse(current.getBizVersionId()),
                WfTaskEntity.ACTION_CANCELLED,
                auth.userId(),
                null,
                cmd.reason(),
                OffsetDateTime.now(),
                cmd.requestId(),
                List.of());
        handlerRegistry.get(current.getBizType()).onWithdrawn(result);
    }

    /**
     * 查询审批轨迹（实例 + 全部任务）。
     *
     * @param instanceId 审批实例 ID
     * @param auth       当前请求授权上下文
     * @return 审批轨迹
     * @throws BizException 实例不存在 {@code RPM-COMMON-404}；非参与人且无查看权限 {@code RPM-COMMON-403}
     */
    @Transactional(readOnly = true)
    public ApprovalTrail findTrail(UUID instanceId, AuthContext auth) {
        Objects.requireNonNull(instanceId, "instanceId 不能为空");
        Objects.requireNonNull(auth, "auth 不能为空");
        WfInstanceEntity instance = requireInstance(UuidUtils.formatCompact(instanceId));
        List<WfTaskEntity> tasks = taskRepository.findByInstance(instance.getId());
        boolean participant = UuidUtils.sameId(instance.getInitiatorId(), auth.userId())
                || tasks.stream().anyMatch(task ->
                        UuidUtils.sameId(task.getAssigneeId(), auth.userId())
                                || UuidUtils.sameId(task.getDelegatedFrom(), auth.userId()));
        if (!participant && !auth.hasPermission(PERM_INSTANCE_VIEW)) {
            throw new BizException(ErrorCode.COMMON_403, "无权查看该审批轨迹");
        }
        return new ApprovalTrail(toView(instance),
                tasks.stream().map(task -> toView(task, instance)).toList());
    }

    /**
     * 我的待办分页（当前用户作为处理人）。
     *
     * @param query 查询条件
     * @param auth  当前请求授权上下文
     * @return 分页待办
     */
    @Transactional(readOnly = true)
    public PageVO<WorkflowTaskView> findMyTasks(TaskQuery query, AuthContext auth) {
        Objects.requireNonNull(query, "query 不能为空");
        Objects.requireNonNull(auth, "auth 不能为空");
        List<WorkflowTaskView> items = workflowPort.findMyTasks(auth.userId(), query);
        long total = taskRepository.countTasks(UuidUtils.formatCompact(auth.userId()),
                query.status(), query.bizType());
        return PageVO.of(items, query.pageNo(), query.pageSize(), total);
    }

    /**
     * 按任务 ID 查询任务详情（审批中心详情页/已办轨迹入口）。
     *
     * <p>可见性：任务处理人、委托人、实例发起人，或持有 {@code workflow:instance:view}
     * 权限的管理员；避免前端为拼详情而拉取整页列表反查。</p>
     *
     * @param taskId 任务 ID
     * @param auth   当前请求授权上下文
     * @return 任务视图
     */
    @Transactional(readOnly = true)
    public WorkflowTaskView findTask(UUID taskId, AuthContext auth) {
        Objects.requireNonNull(taskId, "taskId 不能为空");
        Objects.requireNonNull(auth, "auth 不能为空");
        WfTaskEntity task = requireTask(taskId);
        WfInstanceEntity instance = requireInstance(task.getInstanceId());
        boolean participant = UuidUtils.sameId(task.getAssigneeId(), auth.userId())
                || UuidUtils.sameId(task.getDelegatedFrom(), auth.userId())
                || UuidUtils.sameId(instance.getInitiatorId(), auth.userId());
        if (!participant && !auth.hasPermission(PERM_INSTANCE_VIEW)) {
            throw new BizException(ErrorCode.COMMON_403, "无权查看该审批任务");
        }
        return toView(task, instance);
    }

    /**
     * 我发起的审批实例分页（审批中心「我发起的」；数据范围天然收敛为本人发起）。
     *
     * @param page 页码（从 1 开始）
     * @param size 每页条数（1～100）
     * @param auth 当前请求授权上下文
     * @return 分页实例
     */
    @Transactional(readOnly = true)
    public PageVO<InitiatedInstanceView> findMyInitiated(int page, int size, AuthContext auth) {
        Objects.requireNonNull(auth, "auth 不能为空");
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        String initiatorId = UuidUtils.formatCompact(auth.userId());
        List<InitiatedInstanceView> items = instanceRepository
                .findInitiatedPage(initiatorId, (long) (safePage - 1) * safeSize, safeSize)
                .stream()
                .map(instance -> new InitiatedInstanceView(
                        UuidUtils.parse(instance.getId()),
                        instance.getBizType(),
                        UuidUtils.parse(instance.getBizId()),
                        instance.getStatus(),
                        instance.getStartedAt(),
                        instance.getEndedAt()))
                .toList();
        long total = instanceRepository.countInitiated(initiatorId);
        return PageVO.of(items, safePage, safeSize, total);
    }

    // ---------------------------------------------------------------------
    // 内部方法
    // ---------------------------------------------------------------------

    private WfTaskEntity requireTask(UUID taskId) {
        return taskRepository.findById(UuidUtils.formatCompact(taskId))
                .orElseThrow(() -> new BizException(ErrorCode.COMMON_404, "审批任务不存在"));
    }

    private WfInstanceEntity requireInstance(String instanceId) {
        return instanceRepository.findById(instanceId)
                .orElseThrow(() -> new BizException(ErrorCode.COMMON_404, "审批实例不存在"));
    }

    private void requireActionable(WfTaskEntity task) {
        if (!WfTaskEntity.STATUS_PENDING.equals(task.getStatus())
                && !WfTaskEntity.STATUS_PROCESSING.equals(task.getStatus())) {
            throw new BizException(ErrorCode.WF_004, "任务已被处理");
        }
    }

    private void checkActor(WfTaskEntity task, UUID onBehalfOf, AuthContext auth) {
        if (onBehalfOf == null) {
            if (!UuidUtils.sameId(task.getAssigneeId(), auth.userId())) {
                throw new BizException(ErrorCode.COMMON_403, "非任务处理人");
            }
            return;
        }
        String delegatorId = UuidUtils.formatCompact(onBehalfOf);
        if (!UuidUtils.sameId(task.getAssigneeId(), onBehalfOf)) {
            throw new BizException(ErrorCode.COMMON_403, "代理委托人与任务处理人不一致");
        }
        if (!auth.delegatedFromUserIds().contains(onBehalfOf)) {
            throw new BizException(ErrorCode.COMMON_403, "当前用户无该委托人的有效委托");
        }
        boolean active = delegationRepository
                .findValidByDelegate(UuidUtils.formatCompact(auth.userId()), OffsetDateTime.now())
                .stream()
                .map(WfDelegationEntity::getDelegatorId)
                .anyMatch(id -> UuidUtils.sameId(id, delegatorId));
        if (!active) {
            throw new BizException(ErrorCode.COMMON_403, "委托已失效或不存在");
        }
    }

    private TaskActionResult reassign(WfTaskEntity task, WfInstanceEntity instance,
                                      ActTaskCmd cmd, AuthContext auth) {
        if (!auth.hasPermission(PERM_TASK_REASSIGN)) {
            throw new BizException(ErrorCode.COMMON_403, "无权限重派任务");
        }
        if (!Objects.equals(task.getVersion(), cmd.expectedVersion())) {
            throw new BizException(ErrorCode.WF_004, "任务已被处理，版本不一致");
        }
        if (!WfTaskEntity.STATUS_TIMEOUT.equals(task.getStatus())
                && !WfTaskEntity.STATUS_EXCEPTION.equals(task.getStatus())) {
            throw new BizException(ErrorCode.COMMON_422, "仅超时或异常任务可重派");
        }
        task.reassign(UuidUtils.formatCompact(cmd.reassignTo()), OffsetDateTime.now());
        if (taskRepository.update(task) == 0) {
            throw new BizException(ErrorCode.WF_004, "任务已被他人处理");
        }
        return new TaskActionResult(UuidUtils.parse(task.getId()), UuidUtils.parse(instance.getId()),
                task.getStatus(), instance.getStatus(), List.of());
    }

    private TaskActionResult replay(WfCallbackLogEntity log, WfTaskEntity task,
                                    WfInstanceEntity instance) {
        if (log.getTaskId() != null && !log.getTaskId().equals(task.getId())) {
            throw new BizException(ErrorCode.COMMON_409, "request_id 已被其他任务使用");
        }
        return new TaskActionResult(UuidUtils.parse(task.getId()), UuidUtils.parse(instance.getId()),
                task.getStatus(), instance.getStatus(), readNextTaskIds(log.getPayload()));
    }

    private List<UUID> readNextTaskIds(String payload) {
        if (payload == null || payload.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode ids = root.get("next_task_ids");
            if (ids == null || !ids.isArray()) {
                return List.of();
            }
            List<UUID> result = new ArrayList<>(ids.size());
            for (JsonNode id : ids) {
                result.add(UuidUtils.parse(id.asText()));
            }
            return result;
        } catch (Exception ex) {
            throw new IllegalStateException("审批回调载荷不可解析", ex);
        }
    }

    private List<UUID> pendingTaskIds(String instanceId) {
        return taskRepository.findByInstance(instanceId).stream()
                .filter(task -> WfTaskEntity.STATUS_PENDING.equals(task.getStatus()))
                .map(task -> UuidUtils.parse(task.getId()))
                .toList();
    }

    private WorkflowInstance toView(WfInstanceEntity instance) {
        return new WorkflowInstance(
                UuidUtils.parse(instance.getId()),
                instance.getFlowableInstanceId(),
                instance.getBizType(),
                UuidUtils.parse(instance.getBizId()),
                UuidUtils.parse(instance.getBizVersionId()),
                UuidUtils.parse(instance.getTemplateVersionId()),
                UuidUtils.parse(instance.getInitiatorId()),
                instance.getStatus(),
                instance.getStartedAt(),
                instance.getEndedAt());
    }

    private WorkflowTaskView toView(WfTaskEntity task, WfInstanceEntity instance) {
        return new WorkflowTaskView(
                UuidUtils.parse(task.getId()),
                UuidUtils.parse(task.getInstanceId()),
                instance.getBizType(),
                UuidUtils.parse(instance.getBizId()),
                task.getNodeKey(),
                task.getNodeName(),
                task.getAssigneeId() == null ? null : UuidUtils.parse(task.getAssigneeId()),
                task.getDelegatedFrom() == null ? null : UuidUtils.parse(task.getDelegatedFrom()),
                task.getStatus(),
                task.getDueAt(),
                task.getRoundNo(),
                task.getVersion());
    }
}
