package cn.edu.rpm.funding.domain.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 支付导入解析行（T-FUND-033；技术文档 05 §3.4）。
 *
 * <p>解析器只输出该值对象，不写业务表。{@code errorCode}/{@code errorMessage}
 * 非空表示字段级校验失败；行号从 1 开始（含表头行之后的第一个数据行）。</p>
 *
 * @param rowNo             行号
 * @param externalPaymentNo 外部支付单号
 * @param externalExpenseNo 外部支出单号
 * @param projectNo         项目编号
 * @param amount            金额
 * @param currency          币种
 * @param paidAt            支付时间
 * @param voucherNo         凭证号
 * @param rawData           原始行 JSON
 * @param errorCode         字段级错误码（可空）
 * @param errorMessage      字段级错误说明（可空）
 */
public record PaymentImportRow(
        int rowNo,
        String externalPaymentNo,
        String externalExpenseNo,
        String projectNo,
        BigDecimal amount,
        String currency,
        OffsetDateTime paidAt,
        String voucherNo,
        String rawData,
        String errorCode,
        String errorMessage
) {

    /**
     * 是否通过字段校验。
     *
     * @return {@code true} 表示无字段级错误
     */
    public boolean valid() {
        return errorCode == null || errorCode.isBlank();
    }
}
