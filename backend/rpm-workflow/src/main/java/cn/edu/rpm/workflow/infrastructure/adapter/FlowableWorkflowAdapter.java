package cn.edu.rpm.workflow.infrastructure.adapter;

import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.common.util.UuidUtils;
import cn.edu.rpm.workflow.domain.dto.ApprovalAction;
import cn.edu.rpm.workflow.domain.dto.CancelWorkflowCmd;
import cn.edu.rpm.workflow.domain.dto.CompleteTaskCmd;
import cn.edu.rpm.workflow.domain.dto.StartWorkflowCmd;
import cn.edu.rpm.workflow.domain.dto.TaskQuery;
import cn.edu.rpm.workflow.domain.dto.WorkflowInstance;
import cn.edu.rpm.workflow.domain.dto.WorkflowTaskView;
import cn.edu.rpm.workflow.domain.port.WorkflowPort;
import cn.edu.rpm.workflow.infrastructure.entity.WfCallbackLogEntity;
import cn.edu.rpm.workflow.infrastructure.entity.WfDelegationEntity;
import cn.edu.rpm.workflow.infrastructure.entity.WfInstanceEntity;
import cn.edu.rpm.workflow.infrastructure.entity.WfTaskEntity;
import cn.edu.rpm.workflow.infrastructure.repository.WfCallbackLogRepository;
import cn.edu.rpm.workflow.infrastructure.repository.WfDelegationRepository;
import cn.edu.rpm.workflow.infrastructure.repository.WfInstanceRepository;
import cn.edu.rpm.workflow.infrastructure.repository.WfTaskRepository;
import cn.edu.rpm.workflow.infrastructure.repository.WorkflowTaskRow;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flowable.common.engine.api.FlowableObjectNotFoundException;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Flowable 工作流适配器（T-WF-009；技术文档 03 §3.2）。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>只负责节点流转、任务落库与幂等日志；不修改预算余额、项目状态等业务事实；</li>
 *   <li>{@code wf_instance} / {@code wf_task} / {@code wf_callback_log} 与 Flowable
 *       引擎共享同一数据源，方法上的 {@code @Transactional} 使业务表与 ACT_* 表
 *       在同一事务内提交/回滚；</li>
 *   <li>Flowable 任务不保存 {@code wf_task.id}（无 flowable_task_id 列），完成任务时按
 *       {@code processInstanceId + taskDefinitionKey} 定位唯一活动任务；并行多实例节点
 *       由「先到先处理 + 乐观锁」保证同一业务任务不被重复完成。</li>
 * </ul>
 *
 * <p>幂等口径：{@code requestId} 去重写 {@code wf_callback_log}（uk_wf_callback_request）；
 * 业务侧重复提交在应用服务层直接返回首次结果，适配器负责数据库兜底。</p>
 */
@Component
public class FlowableWorkflowAdapter implements WorkflowPort {

    private static final ZoneId ZONE = ZoneId.systemDefault();

    private final RuntimeService runtimeService;

    private final TaskService taskService;

    private final WfInstanceRepository instanceRepository;

    private final WfTaskRepository taskRepository;

    private final WfCallbackLogRepository callbackLogRepository;

    private final WfDelegationRepository delegationRepository;

    private final ObjectMapper objectMapper;

    /**
     * 构造适配器。
     *
     * @param runtimeService       Flowable 运行时服务
     * @param taskService          Flowable 任务服务
     * @param instanceRepository   审批实例仓储
     * @param taskRepository       审批任务仓储
     * @param callbackLogRepository 回调幂等日志仓储
     * @param delegationRepository 代理审批仓储
     * @param objectMapper         JSON 序列化器（回调载荷落库）
     */
    public FlowableWorkflowAdapter(RuntimeService runtimeService,
                                   TaskService taskService,
                                   WfInstanceRepository instanceRepository,
                                   WfTaskRepository taskRepository,
                                   WfCallbackLogRepository callbackLogRepository,
                                   WfDelegationRepository delegationRepository,
                                   ObjectMapper objectMapper) {
        this.runtimeService = Objects.requireNonNull(runtimeService, "runtimeService 不能为空");
        this.taskService = Objects.requireNonNull(taskService, "taskService 不能为空");
        this.instanceRepository = Objects.requireNonNull(instanceRepository, "instanceRepository 不能为空");
        this.taskRepository = Objects.requireNonNull(taskRepository, "taskRepository 不能为空");
        this.callbackLogRepository = Objects.requireNonNull(callbackLogRepository,
                "callbackLogRepository 不能为空");
        this.delegationRepository = Objects.requireNonNull(delegationRepository, "delegationRepository 不能为空");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper 不能为空");
    }

    @Override
    @Transactional
    public WorkflowInstance start(StartWorkflowCmd cmd) {
        Objects.requireNonNull(cmd, "cmd 不能为空");
        String bizId = UuidUtils.formatCompact(cmd.bizId());
        String bizVersionId = UuidUtils.formatCompact(cmd.bizVersionId());
        if (instanceRepository.findRunningByBiz(cmd.bizType(), bizId, bizVersionId).isPresent()) {
            throw new BizException(ErrorCode.COMMON_409, "同一业务版本已有运行中的审批实例");
        }

        OffsetDateTime now = OffsetDateTime.now();
        WfInstanceEntity instance = new WfInstanceEntity();
        instance.setBizType(cmd.bizType());
        instance.setBizId(bizId);
        instance.setBizVersionId(bizVersionId);
        instance.setTemplateVersionId(UuidUtils.formatCompact(cmd.templateVersionId()));
        instance.setInitiatorId(UuidUtils.formatCompact(cmd.initiatorId()));
        instance.setStatus(WfInstanceEntity.STATUS_RUNNING);
        instance.setStartedAt(now);
        // 部分唯一索引 uk_wf_instance_running 是并发重复发起的最终防线
        instanceRepository.insert(instance);

        Map<String, Object> variables = new LinkedHashMap<>(cmd.variables());
        variables.putIfAbsent("initiatorId", instance.getInitiatorId());
        variables.putIfAbsent("bizType", cmd.bizType());
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
                cmd.processDefinitionKey(), instance.getId(), variables);

        instance.attachFlowableInstance(processInstance.getId());
        if (instanceRepository.update(instance) == 0) {
            throw new BizException(ErrorCode.COMMON_409, "审批实例回填流程实例 ID 冲突");
        }
        syncActiveTasks(instance, 1, now);
        return toView(instance);
    }

    @Override
    public Optional<WorkflowInstance> findInstanceByBiz(String bizType, UUID bizId,
                                                       UUID bizVersionId) {
        Objects.requireNonNull(bizId, "bizId 不能为空");
        Objects.requireNonNull(bizVersionId, "bizVersionId 不能为空");
        return instanceRepository.findLatestByBiz(bizType, UuidUtils.formatCompact(bizId),
                        UuidUtils.formatCompact(bizVersionId))
                .map(this::toView);
    }

    @Override
    @Transactional
    public void completeTask(CompleteTaskCmd cmd) {
        Objects.requireNonNull(cmd, "cmd 不能为空");
        String requestId = UuidUtils.formatCompact(cmd.requestId());
        if (callbackLogRepository.findByRequestId(requestId).isPresent()) {
            return;
        }
        WfInstanceEntity instance = requireInstance(cmd.instanceId());
        if (!WfInstanceEntity.STATUS_RUNNING.equals(instance.getStatus())) {
            throw new BizException(ErrorCode.WF_004, "审批实例已结束，任务不可处理");
        }
        WfTaskEntity task = taskRepository.findById(UuidUtils.formatCompact(cmd.taskId()))
                .orElseThrow(() -> new BizException(ErrorCode.COMMON_404, "审批任务不存在"));
        if (!task.getInstanceId().equals(instance.getId())) {
            throw new BizException(ErrorCode.COMMON_404, "审批任务不属于该实例");
        }
        if (!WfTaskEntity.STATUS_PENDING.equals(task.getStatus())
                && !WfTaskEntity.STATUS_PROCESSING.equals(task.getStatus())) {
            throw new BizException(ErrorCode.WF_004, "任务已被处理");
        }
        if (!Objects.equals(task.getVersion(), cmd.expectedVersion())) {
            throw new BizException(ErrorCode.WF_004, "任务已被处理，版本不一致");
        }
        String operatorId = UuidUtils.formatCompact(cmd.operatorId());
        checkActor(task, operatorId, cmd.onBehalfOf());

        Task flowableTask = findActiveFlowableTask(instance, task);
        OffsetDateTime now = OffsetDateTime.now();
        if (cmd.action() == ApprovalAction.REJECTED) {
            terminateProcessInstance(instance, "审批拒绝：" + nullToEmpty(cmd.comment()));
        } else {
            taskService.complete(flowableTask.getId(), Map.of("action", cmd.action().name()));
        }

        if (cmd.onBehalfOf() != null) {
            task.setDelegatedFrom(UuidUtils.formatCompact(cmd.onBehalfOf()));
        }
        task.complete(cmd.action(), cmd.comment(), requestId, now);
        if (taskRepository.update(task) == 0) {
            throw new BizException(ErrorCode.WF_004, "任务已被他人处理");
        }

        if (isProcessEnded(instance)) {
            instance.finish(toTerminalStatus(cmd.action()), now);
            if (instanceRepository.update(instance) == 0) {
                throw new BizException(ErrorCode.COMMON_409, "审批实例状态更新冲突");
            }
        } else {
            syncActiveTasks(instance, task.getRoundNo(), now);
        }
        insertCallbackLog(instance, task, cmd.action().name(), operatorId, cmd.onBehalfOf(),
                cmd.comment(), requestId, pendingTaskIds(instance));
    }

    @Override
    @Transactional
    public void cancelInstance(CancelWorkflowCmd cmd) {
        Objects.requireNonNull(cmd, "cmd 不能为空");
        String requestId = UuidUtils.formatCompact(cmd.requestId());
        if (callbackLogRepository.findByRequestId(requestId).isPresent()) {
            return;
        }
        WfInstanceEntity instance = requireInstance(cmd.instanceId());
        if (!WfInstanceEntity.STATUS_RUNNING.equals(instance.getStatus())) {
            // 已终态：幂等返回，不重复写回调日志
            return;
        }
        OffsetDateTime now = OffsetDateTime.now();
        terminateProcessInstance(instance, cmd.reason());
        taskRepository.cancelPendingTasks(instance.getId(), now);
        instance.finish(WfInstanceEntity.STATUS_CANCELLED, now);
        if (instanceRepository.update(instance) == 0) {
            throw new BizException(ErrorCode.COMMON_409, "审批实例状态更新冲突");
        }
        insertCallbackLog(instance, null, WfTaskEntity.ACTION_CANCELLED,
                UuidUtils.formatCompact(cmd.operatorId()), null, cmd.reason(), requestId, List.of());
    }

    @Override
    public List<WorkflowTaskView> findMyTasks(UUID userId, TaskQuery query) {
        Objects.requireNonNull(userId, "userId 不能为空");
        Objects.requireNonNull(query, "query 不能为空");
        long offset = (long) (query.pageNo() - 1) * query.pageSize();
        List<WorkflowTaskRow> rows = taskRepository.findTaskPage(
                UuidUtils.formatCompact(userId), query.status(), query.bizType(), offset, query.pageSize());
        List<WorkflowTaskView> result = new ArrayList<>(rows.size());
        for (WorkflowTaskRow row : rows) {
            result.add(new WorkflowTaskView(
                    UuidUtils.parse(row.getTaskId()),
                    UuidUtils.parse(row.getInstanceId()),
                    row.getBizType(),
                    UuidUtils.parse(row.getBizId()),
                    row.getNodeKey(),
                    row.getNodeName(),
                    row.getAssigneeId() == null ? null : UuidUtils.parse(row.getAssigneeId()),
                    row.getDelegatedFrom() == null ? null : UuidUtils.parse(row.getDelegatedFrom()),
                    row.getStatus(),
                    row.getDueAt(),
                    row.getRoundNo(),
                    row.getVersion()));
        }
        return result;
    }

    // ---------------------------------------------------------------------
    // 内部方法
    // ---------------------------------------------------------------------

    private WfInstanceEntity requireInstance(UUID instanceId) {
        return instanceRepository.findById(UuidUtils.formatCompact(instanceId))
                .orElseThrow(() -> new BizException(ErrorCode.COMMON_404, "审批实例不存在"));
    }

    /**
     * 校验操作人身份：本人处理，或持有效委托的代理人处理。
     */
    private void checkActor(WfTaskEntity task, String operatorId, UUID onBehalfOf) {
        if (UuidUtils.sameId(task.getAssigneeId(), operatorId)) {
            return;
        }
        if (onBehalfOf == null) {
            throw new BizException(ErrorCode.COMMON_403, "非任务处理人");
        }
        String delegatorId = UuidUtils.formatCompact(onBehalfOf);
        if (!UuidUtils.sameId(task.getAssigneeId(), delegatorId)) {
            throw new BizException(ErrorCode.COMMON_403, "代理委托人与任务处理人不一致");
        }
        boolean valid = delegationRepository
                .findValidByDelegate(operatorId, OffsetDateTime.now())
                .stream()
                .map(WfDelegationEntity::getDelegatorId)
                .anyMatch(id -> UuidUtils.sameId(id, delegatorId));
        if (!valid) {
            throw new BizException(ErrorCode.COMMON_403, "委托已失效或不存在");
        }
    }

    private Task findActiveFlowableTask(WfInstanceEntity instance, WfTaskEntity task) {
        if (instance.getFlowableInstanceId() == null) {
            throw new BizException(ErrorCode.WF_004, "审批实例未关联流程实例");
        }
        List<Task> activeTasks = taskService.createTaskQuery()
                .processInstanceId(instance.getFlowableInstanceId())
                .taskDefinitionKey(task.getNodeKey())
                .active()
                .list();
        if (activeTasks.isEmpty()) {
            throw new BizException(ErrorCode.WF_004, "任务已被处理");
        }
        return activeTasks.get(0);
    }

    private void syncActiveTasks(WfInstanceEntity instance, int roundNo, OffsetDateTime now) {
        if (instance.getFlowableInstanceId() == null) {
            return;
        }
        List<Task> activeTasks = taskService.createTaskQuery()
                .processInstanceId(instance.getFlowableInstanceId())
                .active()
                .list();
        List<WfTaskEntity> known = new ArrayList<>(taskRepository.findByInstance(instance.getId()));
        for (Task flowableTask : activeTasks) {
            boolean present = known.stream().anyMatch(existing ->
                    WfTaskEntity.STATUS_PENDING.equals(existing.getStatus())
                            && Objects.equals(existing.getNodeKey(), flowableTask.getTaskDefinitionKey())
                            && UuidUtils.sameId(existing.getAssigneeId(),
                                    flowableTask.getAssignee()));
            if (present) {
                continue;
            }
            WfTaskEntity task = new WfTaskEntity();
            task.setInstanceId(instance.getId());
            task.setNodeKey(flowableTask.getTaskDefinitionKey());
            task.setNodeName(flowableTask.getName() == null
                    ? flowableTask.getTaskDefinitionKey() : flowableTask.getName());
            task.setAssigneeId(flowableTask.getAssignee());
            task.setDueAt(toOffsetDateTime(flowableTask.getDueDate()));
            task.setRoundNo(roundNo);
            if (flowableTask.getAssignee() == null) {
                // 03 §3.6：运行时无审批人进入 EXCEPTION 待办，由科研管理指派
                task.setStatus(WfTaskEntity.STATUS_EXCEPTION);
                task.setExceptionReason("节点无处理人");
                task.setExceptionAt(now);
            } else {
                task.setStatus(WfTaskEntity.STATUS_PENDING);
            }
            taskRepository.insert(task);
            known.add(task);
        }
    }

    private boolean isProcessEnded(WfInstanceEntity instance) {
        return instance.getFlowableInstanceId() == null
                || runtimeService.createProcessInstanceQuery()
                        .processInstanceId(instance.getFlowableInstanceId())
                        .count() == 0;
    }

    private void terminateProcessInstance(WfInstanceEntity instance, String reason) {
        if (instance.getFlowableInstanceId() == null) {
            return;
        }
        try {
            runtimeService.deleteProcessInstance(instance.getFlowableInstanceId(), reason);
        } catch (FlowableObjectNotFoundException ignored) {
            // 流程实例已自然结束：保持幂等
        }
    }

    private String toTerminalStatus(ApprovalAction action) {
        return switch (action) {
            case APPROVED -> WfInstanceEntity.STATUS_APPROVED;
            case RETURNED -> WfInstanceEntity.STATUS_RETURNED;
            case REJECTED -> WfInstanceEntity.STATUS_REJECTED;
        };
    }

    private List<UUID> pendingTaskIds(WfInstanceEntity instance) {
        return taskRepository.findByInstance(instance.getId()).stream()
                .filter(task -> WfTaskEntity.STATUS_PENDING.equals(task.getStatus()))
                .map(task -> UuidUtils.parse(task.getId()))
                .toList();
    }

    private void insertCallbackLog(WfInstanceEntity instance, WfTaskEntity task, String action,
                                   String operatorId, UUID onBehalfOf, String comment,
                                   String requestId, List<UUID> nextTaskIds) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("instance_id", instance.getId());
        payload.put("task_id", task == null ? null : task.getId());
        payload.put("action", action);
        payload.put("operator_id", operatorId);
        payload.put("on_behalf_of", onBehalfOf == null ? null : UuidUtils.formatCompact(onBehalfOf));
        payload.put("comment", comment);
        payload.put("request_id", requestId);
        payload.put("next_task_ids", nextTaskIds.stream().map(UuidUtils::formatCompact).toList());

        WfCallbackLogEntity log = new WfCallbackLogEntity();
        log.setInstanceId(instance.getId());
        log.setTaskId(task == null ? null : task.getId());
        log.setRequestId(requestId);
        log.setAction(action);
        log.setPayload(toJson(payload));
        log.setStatus(WfCallbackLogEntity.STATUS_PROCESSED);
        log.setProcessedAt(OffsetDateTime.now());
        callbackLogRepository.insert(log);
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("审批回调载荷序列化失败", ex);
        }
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

    private OffsetDateTime toOffsetDateTime(Date date) {
        return date == null ? null : date.toInstant().atZone(ZONE).toOffsetDateTime();
    }

    private String nullToEmpty(String text) {
        return text == null ? "" : text;
    }
}
