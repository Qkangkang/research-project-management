package cn.edu.rpm.funding.application.dto;

import java.math.BigDecimal;

/**
 * 财务侧支付条目（T-FUND-029；技术文档 05 §3.6）。
 *
 * <p>无外部财务接口时由请求体传入，按外部支付单号与系统侧支付记录匹配。</p>
 *
 * @param externalPaymentNo 外部支付单号
 * @param externalExpenseNo 外部支出单号
 * @param amount            财务侧金额
 */
public record FinancePaymentEntry(
        String externalPaymentNo,
        String externalExpenseNo,
        BigDecimal amount
) {
}
