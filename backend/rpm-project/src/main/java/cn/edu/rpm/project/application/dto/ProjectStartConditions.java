package cn.edu.rpm.project.application.dto;

/**
 * 项目启动条件（T-PRJ-012；PRD §7.1「待启动：补充合同/到账/预算条件、启动」）。
 *
 * <p>启动前必须齐备：负责人确认与启动审批。任一缺失时服务抛
 * {@code RPM-PROJ-002}，项目保持 {@code PENDING_START}（TC-PRJ-006）。</p>
 *
 * @param piConfirmed  负责人已确认
 * @param startApproved 启动审批已完成
 */
public record ProjectStartConditions(boolean piConfirmed, boolean startApproved) {

    /**
     * 启动条件是否齐备。
     *
     * @return 两项均满足返回 {@code true}
     */
    public boolean satisfied() {
        return piConfirmed && startApproved;
    }
}
