package cn.edu.rpm.funding.application.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 财务支付回调命令（T-FUND-027；技术文档 05 §3.3）。
 *
 * <p>{@code rawBody} 为接入层读取的原始报文，服务据此重新验签，避免控制器
 * 反序列化后的字段与签名内容不一致。{@code eventType} 取值：
 * {@code PAY}/{@code PARTIAL_PAY}/{@code REVERSE}/{@code REFUND}。</p>
 *
 * @param rawBody            原始报文
 * @param signature          HMAC-SHA256 签名
 * @param externalEventId    外部事件 ID
 * @param eventType          事件类型
 * @param externalPaymentNo  外部支付单号
 * @param externalExpenseNo  外部支出单号（支出业务编号）
 * @param projectNo          项目编号
 * @param amount             金额
 * @param currency           币种
 * @param paidAt             支付时间
 * @param voucherNo          凭证号
 */
public record PaymentCallbackCmd(
        String rawBody,
        String signature,
        String externalEventId,
        String eventType,
        String externalPaymentNo,
        String externalExpenseNo,
        String projectNo,
        BigDecimal amount,
        String currency,
        OffsetDateTime paidAt,
        String voucherNo
) {
}
