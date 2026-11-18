package cn.edu.rpm.workflow.domain.port;

import cn.edu.rpm.workflow.domain.dto.ApprovalResult;

/**
 * 审批结果处理器（T-WF-010；技术文档 03 §3.2 / §3.5）。
 *
 * <p>每个业务类型（{@code wf_instance.biz_type}）实现一个处理器，由
 * {@code ApprovalResultHandlerRegistry} 按 {@code bizType} 分派。回调时机：</p>
 * <ul>
 *   <li>{@link #onApproved}：当前节点通过且流程继续或结束；</li>
 *   <li>{@link #onReturned}：退回补正，业务进入补充材料状态；</li>
 *   <li>{@link #onRejected}：拒绝，业务按类型终结；</li>
 *   <li>{@link #onWithdrawn}：申请人撤回或管理员终止实例。</li>
 * </ul>
 *
 * <p><b>幂等要求</b>：同一 {@code requestId} 可能被重复投递（重试/重复提交），
 * 实现必须按 {@code instanceId + requestId} 或业务幂等键去重，重复执行结果一致。
 * <b>版本校验</b>：实现必须先在本地事务内确认 {@code bizVersionId} 仍是当前审批版本，
 * 不是则记录 {@code STALE_APPROVAL} 并抛 {@code RPM-WF-005}（技术文档 03 §3.5 规则 1）。</p>
 */
public interface ApprovalResultHandler {

    /**
     * 本处理器负责的业务类型。
     *
     * @return 业务类型编码（与 {@code wf_instance.biz_type} 一致）
     */
    String bizType();

    /**
     * 审批通过回调。
     *
     * @param result 审批结果（含业务 ID/版本、操作人、幂等键与下一节点任务）
     */
    void onApproved(ApprovalResult result);

    /**
     * 审批拒绝回调。
     *
     * @param result 审批结果
     */
    void onRejected(ApprovalResult result);

    /**
     * 审批退回回调（退回补正）。
     *
     * @param result 审批结果
     */
    void onReturned(ApprovalResult result);

    /**
     * 实例撤回/终止回调。
     *
     * @param result 审批结果（action 为 CANCELLED）
     */
    void onWithdrawn(ApprovalResult result);
}
