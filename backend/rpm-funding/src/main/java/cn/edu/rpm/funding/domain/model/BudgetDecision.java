package cn.edu.rpm.funding.domain.model;

/**
 * 预算控制判定结果（T-FUND-012；技术文档 05 §1.3）。
 *
 * <p>拒绝场景由策略直接抛业务异常（{@code RPM-FUND-001} / {@code RPM-FUND-006}），
 * 本结果只表达放行与可选的告警提示，避免调用方遗漏拒绝分支。</p>
 *
 * @param allowed     是否放行（策略放行时恒为 {@code true}）
 * @param riskWarning 风险提示；无提示为 {@code null}
 */
public record BudgetDecision(boolean allowed, String riskWarning) {

    /**
     * 放行且无风险提示。
     *
     * @return 放行结果
     */
    public static BudgetDecision allow() {
        return new BudgetDecision(true, null);
    }

    /**
     * 放行并携带风险提示（{@code WARN} 级别执行率超阈值场景）。
     *
     * @param warning 风险提示文案
     * @return 放行结果
     */
    public static BudgetDecision allowWithWarning(String warning) {
        return new BudgetDecision(true, warning);
    }

    /**
     * 是否存在风险提示。
     *
     * @return 有提示返回 {@code true}
     */
    public boolean hasWarning() {
        return riskWarning != null && !riskWarning.isBlank();
    }
}
