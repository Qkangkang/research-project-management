package cn.edu.rpm.funding.application.dto;

import java.math.BigDecimal;

/**
 * 支出提交结果（T-FUND-020；TC-FUND-012）。
 *
 * @param expenseId         支出 ID
 * @param fundEventId       本次 RESERVE 事件 ID
 * @param versionNo         提交版本号
 * @param availableAmount   预算科目提交后可用余额
 * @param riskWarning       WARN 级别风险提示；无提示为 {@code null}
 * @param workflowInstanceId 审批实例 ID
 */
public record ExpenseSubmitted(
        String expenseId,
        String fundEventId,
        int versionNo,
        BigDecimal availableAmount,
        String riskWarning,
        String workflowInstanceId
) {
}
