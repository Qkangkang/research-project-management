package cn.edu.rpm.funding.application.dto;

import java.math.BigDecimal;

/**
 * 财务支付回调结果（T-FUND-027；技术文档 05 §3.3）。
 *
 * @param accepted         是否受理
 * @param idempotent       是否命中幂等返回首次结果
 * @param acceptedWithDiff 是否进入对账差异（无匹配/晚到/超额等）
 * @param paymentRecordId  支付记录 ID（差异场景可空）
 * @param expenseStatus    支出当前状态（无匹配可空）
 * @param paidTotal        累计已支付
 * @param remainingUnpaid  剩余未付
 * @param diffId           对账差异 ID（可空）
 */
public record PaymentCallbackResult(
        boolean accepted,
        boolean idempotent,
        boolean acceptedWithDiff,
        String paymentRecordId,
        String expenseStatus,
        BigDecimal paidTotal,
        BigDecimal remainingUnpaid,
        String diffId
) {
}
