package cn.edu.rpm.funding.application.dto;

import java.math.BigDecimal;

/**
 * 支付导入行视图（T-FUND-028）。
 *
 * @param rowNo             行号
 * @param externalPaymentNo 外部支付单号
 * @param matchedExpenseId  匹配到的支出 ID
 * @param amount            行金额
 * @param status            行状态
 * @param errorCode         错误码
 * @param errorMessage      错误说明
 */
public record PaymentImportRowView(
        int rowNo,
        String externalPaymentNo,
        String matchedExpenseId,
        BigDecimal amount,
        String status,
        String errorCode,
        String errorMessage
) {
}
