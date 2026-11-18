package cn.edu.rpm.funding.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 经费接口请求体（T-FUND-031/032；技术文档 05 §2）。
 */
public final class FundingRequests {

    private FundingRequests() {
    }

    /**
     * 预算科目行。
     *
     * @param subjectCode 科目编码
     * @param subjectName 科目名称
     * @param amount      金额
     */
    public record BudgetLineRequest(String subjectCode, String subjectName, BigDecimal amount) {
    }

    /**
     * 创建预算草稿请求；字段与 {@code CreateBudgetDraftCmd} 对齐。
     *
     * @param sourceType    来源类型
     * @param controlLevel  控制级别
     * @param effectiveFrom 生效起
     * @param effectiveTo   生效止
     * @param lines         科目明细
     * @param totalAmount   预算总额
     */
    public record CreateBudgetRequest(String sourceType, String controlLevel,
                                      LocalDate effectiveFrom, LocalDate effectiveTo,
                                      List<BudgetLineRequest> lines, BigDecimal totalAmount) {
    }

    /**
     * 创建支出草稿请求。
     *
     * @param budgetVersionId           预算版本 ID
     * @param budgetLineId              科目 ID
     * @param amount                    金额
     * @param purpose                   用途
     * @param payeeName                 收款人
     * @param payeeBankAccountMasked    脱敏账号
     * @param payeeBankAccountEncrypted 加密账号
     * @param payeeBankName             开户行
     * @param idempotencyKey            幂等键
     */
    public record CreateExpenseRequest(String budgetVersionId, String budgetLineId,
                                       BigDecimal amount, String purpose, String payeeName,
                                       String payeeBankAccountMasked,
                                       String payeeBankAccountEncrypted,
                                       String payeeBankName, String idempotencyKey) {
    }

    /**
     * 提交支出请求。
     *
     * @param expectedVersion   期望版本
     * @param budgetVersionId   预算版本 ID
     * @param budgetLineId      科目 ID
     * @param amount            金额
     * @param purpose           用途
     * @param attachmentFileIds 附件
     * @param specialReason     特批原因
     */
    public record SubmitExpenseRequest(Integer expectedVersion, String budgetVersionId,
                                       String budgetLineId, BigDecimal amount, String purpose,
                                       List<String> attachmentFileIds, String specialReason) {
    }

    /**
     * 撤回/取消请求。
     *
     * @param expectedVersion 期望版本
     * @param reason          原因
     */
    public record ExpenseActionRequest(Integer expectedVersion, String reason) {
    }

    /**
     * 财务支付回调报文（snake_case 对齐 05 §3.3）。
     *
     * @param externalEventId   外部事件 ID
     * @param eventType         事件类型
     * @param externalPaymentNo 外部支付单号
     * @param externalExpenseNo 外部支出单号
     * @param projectNo         项目编号
     * @param amount            金额
     * @param currency          币种
     * @param paidAt            支付时间
     * @param voucherNo         凭证号
     */
    public record FinanceCallbackRequest(
            @JsonProperty("external_event_id") String externalEventId,
            @JsonProperty("event_type") String eventType,
            @JsonProperty("external_payment_no") String externalPaymentNo,
            @JsonProperty("external_expense_no") String externalExpenseNo,
            @JsonProperty("project_no") String projectNo,
            BigDecimal amount,
            String currency,
            @JsonProperty("paid_at") OffsetDateTime paidAt,
            @JsonProperty("voucher_no") String voucherNo) {
    }

    /**
     * 创建对账批次请求。
     *
     * @param periodStart    期间起
     * @param periodEnd      期间止
     * @param financeBatchNo 财务批次号
     * @param payments       财务侧支付条目
     */
    public record ReconciliationRunRequest(
            @JsonProperty("period_start") LocalDate periodStart,
            @JsonProperty("period_end") LocalDate periodEnd,
            @JsonProperty("finance_batch_no") String financeBatchNo,
            List<PaymentEntry> payments) {
    }

    /**
     * 财务支付条目。
     *
     * @param externalPaymentNo 外部支付单号
     * @param externalExpenseNo 外部支出单号
     * @param amount            金额
     */
    public record PaymentEntry(
            @JsonProperty("external_payment_no") String externalPaymentNo,
            @JsonProperty("external_expense_no") String externalExpenseNo,
            BigDecimal amount) {
    }

    /**
     * 关闭差异请求。
     *
     * @param resolution      处置说明
     * @param ownerId         责任人
     * @param evidenceFileIds 证据文件
     */
    public record CloseDiffRequest(String resolution, String ownerId,
                                   @JsonProperty("evidence_file_ids")
                                   List<String> evidenceFileIds) {
    }
}
