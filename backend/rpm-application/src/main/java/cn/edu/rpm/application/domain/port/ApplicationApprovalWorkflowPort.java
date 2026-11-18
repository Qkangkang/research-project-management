package cn.edu.rpm.application.domain.port;

import java.util.UUID;

/**
 * 申报审批任务操作端口（T-APP-017 / T-APP-018；技术文档 04 §3.3、§2.2）。
 *
 * <p>应用服务不直接依赖 Flowable 或 {@code WorkflowPort}：形式审查的通过/退回需要
 * 完成当前审批任务，撤回与不予受理需要取消审批实例，均通过本端口表达。
 * 实现位于 {@code rpm-application/infrastructure/adapter}，内部只调用
 * {@code WorkflowPort}。</p>
 */
public interface ApplicationApprovalWorkflowPort {

    /**
     * 完成当前审批任务（形式审查通过/退回）。
     *
     * <p>同一 {@code requestId} 重复提交返回首次结果（由工作流实现幂等）；
     * {@code action} 取值 {@code APPROVED}（PASS）或 {@code RETURNED}（RETURN）。</p>
     *
     * @param instanceId      审批实例 ID
     * @param taskId          当前任务 ID
     * @param operatorId      操作人用户 ID
     * @param approved        {@code true} 通过；{@code false} 退回
     * @param comment         审查意见
     * @param expectedVersion 读取任务时的 {@code wf_task.version}
     * @param requestId       请求幂等键
     */
    void completeReview(UUID instanceId, UUID taskId, UUID operatorId, boolean approved,
                        String comment, Integer expectedVersion, UUID requestId);

    /**
     * 取消审批实例（撤回/不予受理）。
     *
     * <p>实例已处于终态时幂等返回，不抛异常。</p>
     *
     * @param instanceId 审批实例 ID
     * @param operatorId 操作人用户 ID
     * @param reason     取消原因（审计必填）
     * @param requestId  请求幂等键
     */
    void cancel(UUID instanceId, UUID operatorId, String reason, UUID requestId);
}
