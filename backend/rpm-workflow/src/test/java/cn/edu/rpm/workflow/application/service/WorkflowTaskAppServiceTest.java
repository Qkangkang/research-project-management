package cn.edu.rpm.workflow.application.service;

import cn.edu.rpm.common.api.PageVO;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.common.util.UuidUtils;
import cn.edu.rpm.iam.application.dto.AuthContext;
import cn.edu.rpm.iam.domain.port.CoiDeclarationQueryPort;
import cn.edu.rpm.iam.domain.service.SeparationOfDutyGuard;
import cn.edu.rpm.workflow.application.dto.ActTaskCmd;
import cn.edu.rpm.workflow.application.dto.WithdrawCmd;
import cn.edu.rpm.workflow.application.registry.ApprovalResultHandlerRegistry;
import cn.edu.rpm.workflow.domain.dto.ApprovalAction;
import cn.edu.rpm.workflow.domain.dto.ApprovalResult;
import cn.edu.rpm.workflow.domain.dto.ApprovalTrail;
import cn.edu.rpm.workflow.domain.dto.CompleteTaskCmd;
import cn.edu.rpm.workflow.domain.dto.InitiatedInstanceView;
import cn.edu.rpm.workflow.domain.dto.TaskActionResult;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link WorkflowTaskAppService} 纯单元测试（T-WF-014 先行测试）。
 *
 * <p>逐条覆盖 TC-APP-019～TC-APP-027：认领、通过（下游任务）、退回、拒绝、
 * request_id 幂等、旧版本冲突、业务版本过期、超时重派、撤回后任务取消；
 * 另覆盖自审阻断（{@code RPM-WF-003}）与代理审批。</p>
 */
@ExtendWith(MockitoExtension.class)
class WorkflowTaskAppServiceTest {

    private static final String BIZ_TYPE = "APPLICATION";

    @Mock
    private WorkflowPort workflowPort;

    @Mock
    private WfTaskRepository taskRepository;

    @Mock
    private WfInstanceRepository instanceRepository;

    @Mock
    private WfCallbackLogRepository callbackLogRepository;

    @Mock
    private WfDelegationRepository delegationRepository;

    @Mock
    private CoiDeclarationQueryPort coiDeclarationQueryPort;

    private RecordingHandler handler;

    private WorkflowTaskAppService service;

    private UUID instanceUuid;

    private UUID taskUuid;

    private UUID initiatorUuid;

    private UUID assigneeUuid;

    private WfInstanceEntity instance;

    private WfTaskEntity task;

    @BeforeEach
    void setUp() {
        handler = new RecordingHandler(BIZ_TYPE);
        service = new WorkflowTaskAppService(workflowPort, taskRepository, instanceRepository,
                callbackLogRepository, delegationRepository,
                new ApprovalResultHandlerRegistry(List.of(handler)),
                new SeparationOfDutyGuard(coiDeclarationQueryPort),
                new ObjectMapper());

        instanceUuid = UUID.randomUUID();
        taskUuid = UUID.randomUUID();
        initiatorUuid = UUID.randomUUID();
        assigneeUuid = UUID.randomUUID();
        instance = instance();
        task = task("formalReview", WfTaskEntity.STATUS_PENDING);
        lenient().when(instanceRepository.findById(instance.getId())).thenReturn(Optional.of(instance));
        lenient().when(taskRepository.findById(task.getId())).thenReturn(Optional.of(task));
        lenient().when(callbackLogRepository.findByRequestId(anyString())).thenReturn(Optional.empty());
    }

    private WfInstanceEntity instance() {
        WfInstanceEntity entity = new WfInstanceEntity();
        entity.setId(UuidUtils.formatCompact(instanceUuid));
        entity.setBizType(BIZ_TYPE);
        entity.setBizId(UuidUtils.formatCompact(UUID.randomUUID()));
        entity.setBizVersionId(UuidUtils.formatCompact(UUID.randomUUID()));
        entity.setTemplateVersionId(UuidUtils.formatCompact(UUID.randomUUID()));
        entity.setInitiatorId(UuidUtils.formatCompact(initiatorUuid));
        entity.setFlowableInstanceId("pi-1");
        entity.setStatus(WfInstanceEntity.STATUS_RUNNING);
        entity.setStartedAt(OffsetDateTime.now());
        entity.setVersion(0);
        return entity;
    }

    private WfTaskEntity task(String nodeKey, String status) {
        WfTaskEntity entity = new WfTaskEntity();
        entity.setId(UuidUtils.formatCompact(taskUuid));
        entity.setInstanceId(instance.getId());
        entity.setNodeKey(nodeKey);
        entity.setNodeName(nodeKey);
        entity.setAssigneeId(UuidUtils.formatCompact(assigneeUuid));
        entity.setStatus(status);
        entity.setRoundNo(1);
        entity.setVersion(0);
        return entity;
    }

    private AuthContext auth(UUID userId, Set<UUID> delegatedFrom, String... permissions) {
        return new AuthContext(userId, null, "emp-001", Set.of(permissions), delegatedFrom,
                Instant.now());
    }

    private AuthContext assigneeAuth() {
        return auth(assigneeUuid, Set.of());
    }

    // ------------------------------------------------------------------
    // TC-APP-019
    // ------------------------------------------------------------------

    @Test
    void tcApp019ClaimMovesPendingTaskToProcessing() {
        when(taskRepository.update(task)).thenReturn(1);

        TaskActionResult result = service.claim(taskUuid, assigneeAuth());

        assertThat(task.getStatus()).isEqualTo(WfTaskEntity.STATUS_PROCESSING);
        assertThat(task.getClaimedAt()).isNotNull();
        assertThat(result.taskStatus()).isEqualTo(WfTaskEntity.STATUS_PROCESSING);
        assertThat(result.instanceStatus()).isEqualTo(WfInstanceEntity.STATUS_RUNNING);
    }

    @Test
    void claimByOtherUserIsForbidden() {
        assertThatThrownBy(() -> service.claim(taskUuid, auth(UUID.randomUUID(), Set.of())))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.COMMON_403));
    }

    // ------------------------------------------------------------------
    // TC-APP-020
    // ------------------------------------------------------------------

    @Test
    void tcApp020ApproveReturnsNextTaskAndKeepsInstanceRunning() {
        WfTaskEntity nextTask = nextTask();
        when(taskRepository.findByInstance(instance.getId())).thenReturn(List.of(task, nextTask));
        doAnswer(invocation -> {
            task.complete(ApprovalAction.APPROVED, "同意", UuidUtils.formatCompact(
                    invocation.<CompleteTaskCmd>getArgument(0).requestId()), OffsetDateTime.now());
            return null;
        }).when(workflowPort).completeTask(any(CompleteTaskCmd.class));
        UUID requestId = UUID.randomUUID();

        TaskActionResult result = service.act(taskUuid,
                new ActTaskCmd(requestId, 0, ApprovalAction.APPROVED, "同意", null, null),
                assigneeAuth());

        assertThat(task.getStatus()).isEqualTo(WfTaskEntity.STATUS_APPROVED);
        assertThat(result.taskStatus()).isEqualTo(WfTaskEntity.STATUS_APPROVED);
        assertThat(result.instanceStatus()).isEqualTo(WfInstanceEntity.STATUS_RUNNING);
        assertThat(result.nextTaskIds()).containsExactly(UuidUtils.parse(nextTask.getId()));
        assertThat(handler.approved).isEqualTo(1);
        assertThat(handler.lastResult.action()).isEqualTo("APPROVED");
        assertThat(handler.lastResult.requestId()).isEqualTo(requestId);
    }

    // ------------------------------------------------------------------
    // TC-APP-021 / TC-APP-022
    // ------------------------------------------------------------------

    @Test
    void tcApp021ReturnedDispatchesReturnedHandler() {
        when(taskRepository.findByInstance(instance.getId())).thenReturn(List.of(task));
        doAnswer(invocation -> {
            task.complete(ApprovalAction.RETURNED, "请补充材料",
                    UuidUtils.formatCompact(invocation.<CompleteTaskCmd>getArgument(0).requestId()),
                    OffsetDateTime.now());
            instance.finish(WfInstanceEntity.STATUS_RETURNED, OffsetDateTime.now());
            return null;
        }).when(workflowPort).completeTask(any(CompleteTaskCmd.class));

        TaskActionResult result = service.act(taskUuid,
                new ActTaskCmd(UUID.randomUUID(), 0, ApprovalAction.RETURNED, "请补充材料", null, null),
                assigneeAuth());

        assertThat(result.taskStatus()).isEqualTo(WfTaskEntity.STATUS_RETURNED);
        assertThat(result.instanceStatus()).isEqualTo(WfInstanceEntity.STATUS_RETURNED);
        assertThat(handler.returned).isEqualTo(1);
        assertThat(handler.lastResult.comment()).isEqualTo("请补充材料");
    }

    @Test
    void tcApp022RejectedDispatchesRejectedHandler() {
        when(taskRepository.findByInstance(instance.getId())).thenReturn(List.of(task));
        doAnswer(invocation -> {
            task.complete(ApprovalAction.REJECTED, "不符合指南",
                    UuidUtils.formatCompact(invocation.<CompleteTaskCmd>getArgument(0).requestId()),
                    OffsetDateTime.now());
            instance.finish(WfInstanceEntity.STATUS_REJECTED, OffsetDateTime.now());
            return null;
        }).when(workflowPort).completeTask(any(CompleteTaskCmd.class));

        TaskActionResult result = service.act(taskUuid,
                new ActTaskCmd(UUID.randomUUID(), 0, ApprovalAction.REJECTED, "不符合指南", null, null),
                assigneeAuth());

        assertThat(result.taskStatus()).isEqualTo(WfTaskEntity.STATUS_REJECTED);
        assertThat(result.instanceStatus()).isEqualTo(WfInstanceEntity.STATUS_REJECTED);
        assertThat(handler.rejected).isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // TC-APP-023
    // ------------------------------------------------------------------

    @Test
    void tcApp023DuplicateRequestIdReplaysFirstResultWithoutSecondApproval() throws Exception {
        UUID requestId = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
        String requestIdText = UuidUtils.formatCompact(requestId);
        task.complete(ApprovalAction.APPROVED, "同意", requestIdText, OffsetDateTime.now());
        UUID nextId = UUID.randomUUID();
        WfCallbackLogEntity log = new WfCallbackLogEntity();
        log.setTaskId(task.getId());
        log.setRequestId(requestIdText);
        log.setPayload(new ObjectMapper().writeValueAsString(
                java.util.Map.of("next_task_ids", List.of(UuidUtils.formatCompact(nextId)))));
        when(callbackLogRepository.findByRequestId(requestIdText)).thenReturn(Optional.of(log));

        TaskActionResult result = service.act(taskUuid,
                new ActTaskCmd(requestId, 0, ApprovalAction.APPROVED, "同意", null, null),
                assigneeAuth());

        assertThat(result.taskStatus()).isEqualTo(WfTaskEntity.STATUS_APPROVED);
        assertThat(result.nextTaskIds()).containsExactly(nextId);
        verifyNoInteractions(workflowPort);
        verify(taskRepository, never()).update(any());
        assertThat(handler.approved).isZero();
    }

    // ------------------------------------------------------------------
    // TC-APP-024 / TC-APP-025
    // ------------------------------------------------------------------

    @Test
    void tcApp024StaleExpectedVersionReturnsWf004() {
        task.setVersion(3);

        assertThatThrownBy(() -> service.act(taskUuid,
                new ActTaskCmd(UUID.randomUUID(), 2, ApprovalAction.APPROVED, "同意", null, null),
                assigneeAuth()))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.WF_004));
        verifyNoInteractions(workflowPort);
    }

    @Test
    void tcApp025BusinessVersionStalePropagatesWf005() {
        when(taskRepository.findByInstance(instance.getId())).thenReturn(List.of(task));
        handler.failureOnApproved = new BizException(ErrorCode.WF_005, "业务版本已被替代");

        assertThatThrownBy(() -> service.act(taskUuid,
                new ActTaskCmd(UUID.randomUUID(), 0, ApprovalAction.APPROVED, "同意", null, null),
                assigneeAuth()))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.WF_005));
        assertThat(handler.approved).isZero();
    }

    // ------------------------------------------------------------------
    // TC-APP-026
    // ------------------------------------------------------------------

    @Test
    void tcApp026ReassignTimeoutTaskToNewAssignee() {
        task.setStatus(WfTaskEntity.STATUS_TIMEOUT);
        when(taskRepository.update(task)).thenReturn(1);
        UUID newAssignee = UUID.randomUUID();

        TaskActionResult result = service.act(taskUuid,
                new ActTaskCmd(UUID.randomUUID(), 0, null, "原处理人离岗，重派", null, newAssignee),
                auth(UUID.randomUUID(), Set.of(), WorkflowTaskAppService.PERM_TASK_REASSIGN));

        assertThat(task.getStatus()).isEqualTo(WfTaskEntity.STATUS_PENDING);
        assertThat(task.getAssigneeId()).isEqualTo(UuidUtils.formatCompact(newAssignee));
        assertThat(result.taskStatus()).isEqualTo(WfTaskEntity.STATUS_PENDING);
        assertThat(handler.approved).isZero();
    }

    @Test
    void reassignWithoutPermissionOrForPendingTaskIsRejected() {
        task.setStatus(WfTaskEntity.STATUS_TIMEOUT);
        assertThatThrownBy(() -> service.act(taskUuid,
                new ActTaskCmd(UUID.randomUUID(), 0, null, "重派", null, UUID.randomUUID()),
                assigneeAuth()))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.COMMON_403));

        task.setStatus(WfTaskEntity.STATUS_PENDING);
        assertThatThrownBy(() -> service.act(taskUuid,
                new ActTaskCmd(UUID.randomUUID(), 0, null, "重派", null, UUID.randomUUID()),
                auth(UUID.randomUUID(), Set.of(), WorkflowTaskAppService.PERM_TASK_REASSIGN)))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.COMMON_422));
    }

    // ------------------------------------------------------------------
    // TC-APP-027
    // ------------------------------------------------------------------

    @Test
    void tcApp027WithdrawCancelsPendingTasksAndDispatchesWithdrawn() {
        doAnswer(invocation -> {
            instance.finish(WfInstanceEntity.STATUS_CANCELLED, OffsetDateTime.now());
            task.cancel(OffsetDateTime.now());
            return null;
        }).when(workflowPort).cancelInstance(any());

        service.withdraw(new WithdrawCmd(instanceUuid, "申报人撤回", UUID.randomUUID()),
                auth(initiatorUuid, Set.of()));

        assertThat(task.getStatus()).isEqualTo(WfTaskEntity.STATUS_CANCELLED);
        assertThat(instance.getStatus()).isEqualTo(WfInstanceEntity.STATUS_CANCELLED);
        assertThat(handler.withdrawn).isEqualTo(1);
    }

    @Test
    void tcApp027CancelledTaskCannotBeActedOn() {
        task.setStatus(WfTaskEntity.STATUS_CANCELLED);

        assertThatThrownBy(() -> service.act(taskUuid,
                new ActTaskCmd(UUID.randomUUID(), 0, ApprovalAction.APPROVED, "同意", null, null),
                assigneeAuth()))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.WF_004));
        verifyNoInteractions(workflowPort);
    }

    @Test
    void withdrawByNonInitiatorWithoutPermissionIsForbidden() {
        assertThatThrownBy(() -> service.withdraw(
                new WithdrawCmd(instanceUuid, "撤回", UUID.randomUUID()),
                auth(UUID.randomUUID(), Set.of())))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.COMMON_403));
    }

    // ------------------------------------------------------------------
    // 职责分离 / 代理审批 / 审批轨迹
    // ------------------------------------------------------------------

    @Test
    void selfApprovalIsBlockedWithWf003() {
        instance.setInitiatorId(UuidUtils.formatCompact(assigneeUuid));

        assertThatThrownBy(() -> service.act(taskUuid,
                new ActTaskCmd(UUID.randomUUID(), 0, ApprovalAction.APPROVED, "同意", null, null),
                assigneeAuth()))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.WF_003));
        verifyNoInteractions(workflowPort);
    }

    @Test
    void delegateMayActOnBehalfOfAssigneeWithActiveDelegation() {
        UUID delegateUuid = UUID.randomUUID();
        WfDelegationEntity delegation = new WfDelegationEntity();
        delegation.setDelegatorId(UuidUtils.formatCompact(assigneeUuid));
        delegation.setDelegateId(UuidUtils.formatCompact(delegateUuid));
        when(delegationRepository.findValidByDelegate(eq(UuidUtils.formatCompact(delegateUuid)),
                any(OffsetDateTime.class))).thenReturn(List.of(delegation));
        when(taskRepository.findByInstance(instance.getId())).thenReturn(List.of(task));
        doAnswer(invocation -> {
            task.complete(ApprovalAction.APPROVED, "代批",
                    UuidUtils.formatCompact(invocation.<CompleteTaskCmd>getArgument(0).requestId()),
                    OffsetDateTime.now());
            return null;
        }).when(workflowPort).completeTask(any(CompleteTaskCmd.class));

        TaskActionResult result = service.act(taskUuid,
                new ActTaskCmd(UUID.randomUUID(), 0, ApprovalAction.APPROVED, "代批",
                        assigneeUuid, null),
                auth(delegateUuid, Set.of(assigneeUuid)));

        assertThat(result.taskStatus()).isEqualTo(WfTaskEntity.STATUS_APPROVED);
        verify(workflowPort).completeTask(any(CompleteTaskCmd.class));
    }

    @Test
    void nonAssigneeCannotAct() {
        assertThatThrownBy(() -> service.act(taskUuid,
                new ActTaskCmd(UUID.randomUUID(), 0, ApprovalAction.APPROVED, "同意", null, null),
                auth(UUID.randomUUID(), Set.of())))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.COMMON_403));
        verifyNoInteractions(workflowPort);
    }

    @Test
    void findTrailAllowsInitiatorAndReturnsTasks() {
        when(taskRepository.findByInstance(instance.getId())).thenReturn(List.of(task));

        ApprovalTrail trail = service.findTrail(instanceUuid, auth(initiatorUuid, Set.of()));

        assertThat(trail.instance().instanceId()).isEqualTo(instanceUuid);
        assertThat(trail.tasks()).hasSize(1);
        assertThat(trail.tasks().get(0).bizType()).isEqualTo(BIZ_TYPE);
    }

    @Test
    void findTrailRejectsUnrelatedUser() {
        when(taskRepository.findByInstance(instance.getId())).thenReturn(List.of(task));

        assertThatThrownBy(() -> service.findTrail(instanceUuid, auth(UUID.randomUUID(), Set.of())))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.COMMON_403));
    }

    // ------------------------------------------------------------------
    // 任务详情（参与人可见）/ 我发起的
    // ------------------------------------------------------------------

    @Test
    void findTaskAllowsAssignee() {
        WorkflowTaskView view = service.findTask(taskUuid, assigneeAuth());

        assertThat(view.taskId()).isEqualTo(taskUuid);
        assertThat(view.bizType()).isEqualTo(BIZ_TYPE);
        assertThat(view.nodeName()).isEqualTo("formalReview");
    }

    @Test
    void findTaskAllowsInitiator() {
        WorkflowTaskView view = service.findTask(taskUuid, auth(initiatorUuid, Set.of()));

        assertThat(view.taskId()).isEqualTo(taskUuid);
    }

    @Test
    void findTaskAllowsAdminWithInstanceViewPermission() {
        WorkflowTaskView view = service.findTask(taskUuid,
                auth(UUID.randomUUID(), Set.of(), WorkflowTaskAppService.PERM_INSTANCE_VIEW));

        assertThat(view.taskId()).isEqualTo(taskUuid);
    }

    @Test
    void findTaskRejectsUnrelatedUserWithoutPermission() {
        assertThatThrownBy(() -> service.findTask(taskUuid, auth(UUID.randomUUID(), Set.of())))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.COMMON_403));
    }

    @Test
    void findMyInitiatedReturnsScopedPage() {
        when(instanceRepository.findInitiatedPage(anyString(), eq(0L), eq(20)))
                .thenReturn(List.of(instance));
        when(instanceRepository.countInitiated(anyString())).thenReturn(1L);

        PageVO<InitiatedInstanceView> page = service.findMyInitiated(1, 20, auth(initiatorUuid, Set.of()));

        assertThat(page.getTotal()).isEqualTo(1L);
        assertThat(page.getItems()).hasSize(1);
        assertThat(page.getItems().get(0).bizType()).isEqualTo(BIZ_TYPE);
        assertThat(page.getItems().get(0).status()).isEqualTo(WfInstanceEntity.STATUS_RUNNING);
        // 数据范围收敛：只查发起人本人
        verify(instanceRepository).findInitiatedPage(eq(UuidUtils.formatCompact(initiatorUuid)), eq(0L), eq(20));
    }

    private WfTaskEntity nextTask() {
        WfTaskEntity next = new WfTaskEntity();
        next.setId(UuidUtils.formatCompact(UUID.randomUUID()));
        next.setInstanceId(instance.getId());
        next.setNodeKey("decision");
        next.setNodeName("决策");
        next.setAssigneeId(UuidUtils.formatCompact(assigneeUuid));
        next.setStatus(WfTaskEntity.STATUS_PENDING);
        next.setRoundNo(1);
        next.setVersion(0);
        return next;
    }

    /**
     * 记录回调次数的审批结果处理器桩。
     */
    private static final class RecordingHandler implements ApprovalResultHandler {

        private final String bizType;

        private int approved;

        private int rejected;

        private int returned;

        private int withdrawn;

        private ApprovalResult lastResult;

        private RuntimeException failureOnApproved;

        private RecordingHandler(String bizType) {
            this.bizType = bizType;
        }

        @Override
        public String bizType() {
            return bizType;
        }

        @Override
        public void onApproved(ApprovalResult result) {
            if (failureOnApproved != null) {
                throw failureOnApproved;
            }
            approved++;
            lastResult = result;
        }

        @Override
        public void onRejected(ApprovalResult result) {
            rejected++;
            lastResult = result;
        }

        @Override
        public void onReturned(ApprovalResult result) {
            returned++;
            lastResult = result;
        }

        @Override
        public void onWithdrawn(ApprovalResult result) {
            withdrawn++;
            lastResult = result;
        }
    }
}
