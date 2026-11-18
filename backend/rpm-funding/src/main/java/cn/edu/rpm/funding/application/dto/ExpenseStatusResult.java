package cn.edu.rpm.funding.application.dto;

import java.math.BigDecimal;

/**
 * 支出状态动作结果（T-FUND-021）。
 *
 * @param expenseId        支出 ID
 * @param status           动作后的支出状态
 * @param releasedAmount   本次释放/取消的金额；未产生资金事件为 {@code null}
 */
public record ExpenseStatusResult(
        String expenseId,
        String status,
        BigDecimal releasedAmount
) {
}
