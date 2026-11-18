package cn.edu.rpm.workflow.domain.dto;

/**
 * 审批任务处理动作（T-WF-008）。
 *
 * <p>取值与 {@code wf_task.action} 的 CHECK 约束一致；代理审批同样使用本枚举，
 * 委托人身份由 {@link CompleteTaskCmd#onBehalfOf()} 单独承载。</p>
 */
public enum ApprovalAction {

    /** 通过：推进到下一节点或完成实例。 */
    APPROVED,

    /** 退回补正：回到申请人/上一节点，保留审批轮次。 */
    RETURNED,

    /** 拒绝：终止本次审批，业务状态按各自规则处理。 */
    REJECTED
}
