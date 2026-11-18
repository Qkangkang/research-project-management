package cn.edu.rpm.project.domain.port;

/**
 * 预算变更生效端口（T-PRJ-021；技术文档 08 §8.2 BUDGET 行）。
 *
 * <p>定义在 {@code rpm-project}，由 {@code rpm-funding} 实现；避免
 * project → funding 的反向依赖。批准预算变更时与基线切换同事务调用。</p>
 */
public interface BudgetChangePort {

    /**
     * 应用预算变更：生成新预算版本并初始化余额。
     *
     * @param command 预算变更命令
     * @return 新预算版本 ID
     */
    String applyBudgetChange(BudgetChangeCommand command);

    /**
     * 预算变更命令。
     *
     * @param projectId       项目 ID
     * @param changeRequestId 变更单 ID
     * @param changeVersionId 变更版本 ID
     * @param payloadJson     变更载荷（规范化 JSON）
     * @param operatorId      操作人用户 ID
     */
    record BudgetChangeCommand(
            String projectId,
            String changeRequestId,
            String changeVersionId,
            String payloadJson,
            java.util.UUID operatorId
    ) {
    }
}
