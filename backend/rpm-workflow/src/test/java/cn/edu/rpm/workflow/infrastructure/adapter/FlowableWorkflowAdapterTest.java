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
import cn.edu.rpm.workflow.infrastructure.entity.WfCallbackLogEntity;
import cn.edu.rpm.workflow.infrastructure.entity.WfInstanceEntity;
import cn.edu.rpm.workflow.infrastructure.entity.WfTaskEntity;
import cn.edu.rpm.workflow.infrastructure.repository.WfCallbackLogRepository;
import cn.edu.rpm.workflow.infrastructure.repository.WfDelegationRepository;
import cn.edu.rpm.workflow.infrastructure.repository.WfInstanceRepository;
import cn.edu.rpm.workflow.infrastructure.repository.WfTaskRepository;
import cn.edu.rpm.workflow.infrastructure.repository.WorkflowTaskRow;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.runtime.ProcessInstanceQuery;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link FlowableWorkflowAdapter} 纯单元测试（T-WF-009 先行测试）。
 *
 * <p>用 Mockito 模拟 Flowable 服务与仓储，验证启动/完成任务/取消实例/待办映射
 * 以及 {@code request_id} 幂等短路；真实引擎行为由 rpm-boot 的
 * {@code WorkflowIntegrationTest} 覆盖。</p>
 */
@ExtendWith(MockitoExtension.class)
class FlowableWorkflowAdapterTest {

    private static final String INSTANCE_ID = "11111111111111111111111111111111";

    private static final String TASK_ID = "22222222222222222222222222222222";

    private static final String ASSIGNEE_ID = "33333333333333333333333333333333";

    private static final String FLOWABLE_INSTANCE_ID = "pi-1";

    @Mock
    private RuntimeService runtimeService;

    @Mock
    private TaskService taskService;

    @Mock
    private WfInstanceRepository instanceRepository;

    @Mock
    private WfTaskRepository taskRepository;

    @Mock
    private WfCallbackLogRepository callbackLogRepository;

    @Mock
    private WfDelegationRepository delegationRepository;

    @Mock
    private ProcessInstance processInstance;

    @Mock
    private Task flowableTask;

    @Mock(answer = Answers.RETURNS_SELF)
    private org.flowable.task.api.TaskQuery taskQuery;

    @Mock(answer = Answers.RETURNS_SELF)
    private ProcessInstanceQuery processInstanceQuery;

    private FlowableWorkflowAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new FlowableWorkflowAdapter(runtimeService, taskService, instanceRepository,
                taskRepository, callbackLogRepository, delegationRepository, new ObjectMapper());
    }

    private WfInstanceEntity instance(String status) {
        WfInstanceEntity entity = new WfInstanceEntity();
        entity.setId(INSTANCE_ID);
        entity.setBizType("APPLICATION");
        entity.setBizId(UuidUtils.formatCompact(UUID.randomUUID()));
        entity.setBizVersionId(UuidUtils.formatCompact(UUID.randomUUID()));
        entity.setTemplateVersionId(UuidUtils.formatCompact(UUID.randomUUID()));
        entity.setInitiatorId(UuidUtils.formatCompact(UUID.randomUUID()));
        entity.setFlowableInstanceId(FLOWABLE_INSTANCE_ID);
        entity.setStatus(status);
        entity.setStartedAt(OffsetDateTime.now());
        entity.setVersion(0);
        return entity;
    }

    private WfTaskEntity task(String status) {
        WfTaskEntity entity = new WfTaskEntity();
        entity.setId(TASK_ID);
        entity.setInstanceId(INSTANCE_ID);
        entity.setNodeKey("formalReview");
        entity.setNodeName("形式审查");
        entity.setAssigneeId(ASSIGNEE_ID);
        entity.setStatus(status);
        entity.setRoundNo(1);
        entity.setVersion(0);
        return entity;
    }

    @Test
    void startCreatesInstanceAndSyncsFirstNodeTask() {
        doAnswer(invocation -> {
            WfInstanceEntity inserted = invocation.getArgument(0);
            inserted.setId(INSTANCE_ID);
            return null;
        }).when(instanceRepository).insert(any());
        when(instanceRepository.findRunningByBiz(anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(runtimeService.startProcessInstanceByKey(eq("rpmTestApproval"), anyString(), anyMap()))
                .thenReturn(processInstance);
        when(processInstance.getId()).thenReturn(FLOWABLE_INSTANCE_ID);
        when(instanceRepository.update(any())).thenReturn(1);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.list()).thenReturn(List.of(flowableTask));
        when(flowableTask.getTaskDefinitionKey()).thenReturn("formalReview");
        when(flowableTask.getName()).thenReturn("形式审查");
        when(flowableTask.getAssignee()).thenReturn(ASSIGNEE_ID);
        when(taskRepository.findByInstance(INSTANCE_ID)).thenReturn(List.of());

        WorkflowInstance result = adapter.start(new StartWorkflowCmd(
                "APPLICATION", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "rpmTestApproval", UUID.randomUUID(), Map.of("assignee", ASSIGNEE_ID)));

        assertThat(result.instanceId()).isNotNull();
        assertThat(result.flowableInstanceId()).isEqualTo(FLOWABLE_INSTANCE_ID);
        assertThat(result.status()).isEqualTo(WfInstanceEntity.STATUS_RUNNING);
        ArgumentCaptor<WfTaskEntity> taskCaptor = ArgumentCaptor.forClass(WfTaskEntity.class);
        verify(taskRepository).insert(taskCaptor.capture());
        assertThat(taskCaptor.getValue().getInstanceId()).isEqualTo(INSTANCE_ID);
        assertThat(taskCaptor.getValue().getNodeKey()).isEqualTo("formalReview");
        assertThat(taskCaptor.getValue().getAssigneeId()).isEqualTo(ASSIGNEE_ID);
        assertThat(taskCaptor.getValue().getStatus()).isEqualTo(WfTaskEntity.STATUS_PENDING);
    }

    @Test
    void startRejectsDuplicateRunningInstanceBeforeCallingEngine() {
        when(instanceRepository.findRunningByBiz(anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(instance(WfInstanceEntity.STATUS_RUNNING)));

        assertThatThrownBy(() -> adapter.start(new StartWorkflowCmd(
                "APPLICATION", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "rpmTestApproval", UUID.randomUUID(), Map.of())))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.COMMON_409));
        verifyNoInteractions(runtimeService);
    }

    @Test
    void tcApp020ApproveCompletesFlowableTaskAndFinishesInstance() {
        WfInstanceEntity instance = instance(WfInstanceEntity.STATUS_RUNNING);
        WfTaskEntity task = task(WfTaskEntity.STATUS_PENDING);
        when(instanceRepository.findById(INSTANCE_ID)).thenReturn(Optional.of(instance));
        when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
        when(callbackLogRepository.findByRequestId(anyString())).thenReturn(Optional.empty());
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.list()).thenReturn(List.of(flowableTask));
        when(flowableTask.getId()).thenReturn("ft-1");
        when(taskRepository.update(any())).thenReturn(1);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(processInstanceQuery);
        when(processInstanceQuery.count()).thenReturn(0L);
        when(instanceRepository.update(any())).thenReturn(1);
        when(taskRepository.findByInstance(INSTANCE_ID)).thenReturn(List.of(task));

        adapter.completeTask(new CompleteTaskCmd(
                UUID.fromString(canonical(INSTANCE_ID)), UUID.fromString(canonical(TASK_ID)),
                UUID.fromString(canonical(ASSIGNEE_ID)), null, ApprovalAction.APPROVED,
                "同意", 0, UUID.randomUUID()));

        verify(taskService).complete(eq("ft-1"), anyMap());
        assertThat(task.getStatus()).isEqualTo(WfTaskEntity.STATUS_APPROVED);
        assertThat(task.getAction()).isEqualTo(WfTaskEntity.ACTION_APPROVED);
        assertThat(instance.getStatus()).isEqualTo(WfInstanceEntity.STATUS_APPROVED);
        assertThat(instance.getEndedAt()).isNotNull();
        ArgumentCaptor<WfCallbackLogEntity> logCaptor =
                ArgumentCaptor.forClass(WfCallbackLogEntity.class);
        verify(callbackLogRepository).insert(logCaptor.capture());
        assertThat(logCaptor.getValue().getAction()).isEqualTo("APPROVED");
        assertThat(logCaptor.getValue().getPayload()).contains("next_task_ids");
    }

    @Test
    void tcApp022RejectTerminatesProcessAndFinishesInstanceRejected() {
        WfInstanceEntity instance = instance(WfInstanceEntity.STATUS_RUNNING);
        WfTaskEntity task = task(WfTaskEntity.STATUS_PENDING);
        when(instanceRepository.findById(INSTANCE_ID)).thenReturn(Optional.of(instance));
        when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
        when(callbackLogRepository.findByRequestId(anyString())).thenReturn(Optional.empty());
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.list()).thenReturn(List.of(flowableTask));
        when(taskRepository.update(any())).thenReturn(1);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(processInstanceQuery);
        when(processInstanceQuery.count()).thenReturn(0L);
        when(instanceRepository.update(any())).thenReturn(1);
        when(taskRepository.findByInstance(INSTANCE_ID)).thenReturn(List.of(task));

        adapter.completeTask(new CompleteTaskCmd(
                UUID.fromString(canonical(INSTANCE_ID)), UUID.fromString(canonical(TASK_ID)),
                UUID.fromString(canonical(ASSIGNEE_ID)), null, ApprovalAction.REJECTED,
                "不符合指南", 0, UUID.randomUUID()));

        verify(runtimeService).deleteProcessInstance(eq(FLOWABLE_INSTANCE_ID), anyString());
        verify(taskService, never()).complete(anyString(), anyMap());
        assertThat(instance.getStatus()).isEqualTo(WfInstanceEntity.STATUS_REJECTED);
        assertThat(task.getStatus()).isEqualTo(WfTaskEntity.STATUS_REJECTED);
    }

    @Test
    void tcApp027CancelInstanceCancelsTasksAndWritesCallbackLog() {
        WfInstanceEntity instance = instance(WfInstanceEntity.STATUS_RUNNING);
        when(instanceRepository.findById(INSTANCE_ID)).thenReturn(Optional.of(instance));
        when(callbackLogRepository.findByRequestId(anyString())).thenReturn(Optional.empty());
        when(taskRepository.cancelPendingTasks(eq(INSTANCE_ID), any())).thenReturn(1);
        when(instanceRepository.update(any())).thenReturn(1);

        adapter.cancelInstance(new CancelWorkflowCmd(
                UUID.fromString(canonical(INSTANCE_ID)),
                UUID.fromString(canonical(ASSIGNEE_ID)), "申报人撤回", UUID.randomUUID()));

        verify(runtimeService).deleteProcessInstance(eq(FLOWABLE_INSTANCE_ID), eq("申报人撤回"));
        verify(taskRepository).cancelPendingTasks(eq(INSTANCE_ID), any());
        assertThat(instance.getStatus()).isEqualTo(WfInstanceEntity.STATUS_CANCELLED);
        ArgumentCaptor<WfCallbackLogEntity> logCaptor =
                ArgumentCaptor.forClass(WfCallbackLogEntity.class);
        verify(callbackLogRepository).insert(logCaptor.capture());
        assertThat(logCaptor.getValue().getAction()).isEqualTo("CANCELLED");
    }

    @Test
    void duplicateRequestIdIsShortCircuited() {
        when(callbackLogRepository.findByRequestId(anyString()))
                .thenReturn(Optional.of(new WfCallbackLogEntity()));

        adapter.completeTask(new CompleteTaskCmd(
                UUID.fromString(canonical(INSTANCE_ID)), UUID.fromString(canonical(TASK_ID)),
                UUID.fromString(canonical(ASSIGNEE_ID)), null, ApprovalAction.APPROVED,
                "同意", 0, UUID.randomUUID()));

        verifyNoInteractions(runtimeService, taskService);
        verifyNoInteractions(instanceRepository, taskRepository);
    }

    @Test
    void findMyTasksMapsJoinedRowAndComputesOffset() {
        WorkflowTaskRow row = new WorkflowTaskRow();
        row.setTaskId(TASK_ID);
        row.setInstanceId(INSTANCE_ID);
        row.setBizType("APPLICATION");
        row.setBizId(UuidUtils.formatCompact(UUID.randomUUID()));
        row.setNodeKey("formalReview");
        row.setNodeName("形式审查");
        row.setAssigneeId(ASSIGNEE_ID);
        row.setStatus(WfTaskEntity.STATUS_PENDING);
        row.setRoundNo(1);
        row.setVersion(0);
        when(taskRepository.findTaskPage(ASSIGNEE_ID, WfTaskEntity.STATUS_PENDING, "APPLICATION",
                10L, 10)).thenReturn(List.of(row));

        List<WorkflowTaskView> tasks = adapter.findMyTasks(
                UUID.fromString(canonical(ASSIGNEE_ID)),
                new TaskQuery("APPLICATION", WfTaskEntity.STATUS_PENDING, 2, 10));

        assertThat(tasks).hasSize(1);
        assertThat(tasks.get(0).taskId()).isEqualTo(UUID.fromString(canonical(TASK_ID)));
        assertThat(tasks.get(0).bizType()).isEqualTo("APPLICATION");
        assertThat(tasks.get(0).nodeName()).isEqualTo("形式审查");
    }

    private static String canonical(String compact) {
        return compact.substring(0, 8) + "-" + compact.substring(8, 12) + "-"
                + compact.substring(12, 16) + "-" + compact.substring(16, 20) + "-"
                + compact.substring(20);
    }
}
