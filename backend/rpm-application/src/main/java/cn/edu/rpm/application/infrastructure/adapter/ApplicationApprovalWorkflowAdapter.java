package cn.edu.rpm.application.infrastructure.adapter;

import cn.edu.rpm.application.domain.port.ApplicationApprovalWorkflowPort;
import cn.edu.rpm.workflow.domain.dto.ApprovalAction;
import cn.edu.rpm.workflow.domain.dto.CancelWorkflowCmd;
import cn.edu.rpm.workflow.domain.dto.CompleteTaskCmd;
import cn.edu.rpm.workflow.domain.port.WorkflowPort;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;

/**
 * 申报审批任务操作适配器（T-APP-017 / T-APP-018）。
 *
 * <p>把形式审查/撤回的业务语义翻译为 {@link WorkflowPort} 的
 * {@code completeTask} / {@code cancelInstance}；应用服务不接触 Flowable API 或表。</p>
 */
@Component
public class ApplicationApprovalWorkflowAdapter implements ApplicationApprovalWorkflowPort {

    private final WorkflowPort workflowPort;

    /**
     * 构造适配器。
     *
     * @param workflowPort 工作流端口
     */
    public ApplicationApprovalWorkflowAdapter(WorkflowPort workflowPort) {
        this.workflowPort = Objects.requireNonNull(workflowPort, "workflowPort 不能为空");
    }

    @Override
    public void completeReview(UUID instanceId, UUID taskId, UUID operatorId, boolean approved,
                               String comment, Integer expectedVersion, UUID requestId) {
        Objects.requireNonNull(instanceId, "instanceId 不能为空");
        Objects.requireNonNull(taskId, "taskId 不能为空");
        Objects.requireNonNull(operatorId, "operatorId 不能为空");
        Objects.requireNonNull(expectedVersion, "expectedVersion 不能为空");
        Objects.requireNonNull(requestId, "requestId 不能为空");
        workflowPort.completeTask(new CompleteTaskCmd(
                instanceId,
                taskId,
                operatorId,
                null,
                approved ? ApprovalAction.APPROVED : ApprovalAction.RETURNED,
                comment,
                expectedVersion,
                requestId));
    }

    @Override
    public void cancel(UUID instanceId, UUID operatorId, String reason, UUID requestId) {
        Objects.requireNonNull(instanceId, "instanceId 不能为空");
        Objects.requireNonNull(operatorId, "operatorId 不能为空");
        Objects.requireNonNull(requestId, "requestId 不能为空");
        workflowPort.cancelInstance(new CancelWorkflowCmd(instanceId, operatorId, reason, requestId));
    }
}
