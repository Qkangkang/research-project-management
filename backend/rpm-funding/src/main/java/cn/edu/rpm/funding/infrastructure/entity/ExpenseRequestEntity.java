package cn.edu.rpm.funding.infrastructure.entity;

import cn.edu.rpm.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * {@code expense_request} 支出申请 PO（T-FUND-004；技术文档 02 §3.4）。
 *
 * <p>{@code request_no} 全局唯一；创建幂等键映射 V04 新增的
 * {@code creation_idempotency_key}（部分唯一索引），提交幂等键在版本表。
 * 金额 {@link BigDecimal}；银行账号仅保存脱敏值与加密值，禁止明文落库。</p>
 */
@TableName("expense_request")
public class ExpenseRequestEntity extends BaseEntity {

    /** 状态：草稿。 */
    public static final String STATUS_DRAFT = "DRAFT";

    /** 状态：审批中。 */
    public static final String STATUS_IN_REVIEW = "IN_REVIEW";

    /** 状态：待补正。 */
    public static final String STATUS_SUPPLEMENT = "SUPPLEMENT";

    /** 状态：已批准未付。 */
    public static final String STATUS_APPROVED_UNPAID = "APPROVED_UNPAID";

    /** 状态：部分支付。 */
    public static final String STATUS_PARTIALLY_PAID = "PARTIALLY_PAID";

    /** 状态：已支付。 */
    public static final String STATUS_PAID = "PAID";

    /** 状态：已取消。 */
    public static final String STATUS_CANCELLED = "CANCELLED";

    /** 状态：已拒绝。 */
    public static final String STATUS_REJECTED = "REJECTED";

    /** 状态：已冲正。 */
    public static final String STATUS_REVERSED = "REVERSED";

    /** 支出单号（uk_expense_request_no）。 */
    @TableField("request_no")
    private String requestNo;

    /** 项目 ID。 */
    @TableField("project_id")
    private String projectId;

    /** 预算版本 ID。 */
    @TableField("budget_version_id")
    private String budgetVersionId;

    /** 预算科目 ID。 */
    @TableField("budget_line_id")
    private String budgetLineId;

    /** 申请金额。 */
    @TableField("amount")
    private BigDecimal amount;

    /** 用途说明。 */
    @TableField("purpose")
    private String purpose;

    /** 收款人名称。 */
    @TableField("payee_name")
    private String payeeName;

    /** 脱敏银行账号。 */
    @TableField("payee_bank_account_masked")
    private String payeeBankAccountMasked;

    /** 加密银行账号。 */
    @TableField("payee_bank_account_encrypted")
    private String payeeBankAccountEncrypted;

    /** 开户行。 */
    @TableField("payee_bank_name")
    private String payeeBankName;

    /** 状态。 */
    @TableField("status")
    private String status;

    /** 当前版本 ID。 */
    @TableField("current_version_id")
    private String currentVersionId;

    /** 创建幂等键（V04 起为 creation_idempotency_key，唯一索引 uk_expense_creation_idem）。 */
    @TableField("creation_idempotency_key")
    private String idempotencyKey;

    /** 提交人。 */
    @TableField("submitted_by")
    private String submittedBy;

    /** 提交时间。 */
    @TableField("submitted_at")
    private OffsetDateTime submittedAt;

    /** 批准时间。 */
    @TableField("approved_at")
    private OffsetDateTime approvedAt;

    /** 支付完成时间。 */
    @TableField("paid_at")
    private OffsetDateTime paidAt;

    /**
     * 提交审批（{@code DRAFT}/{@code SUPPLEMENT} → {@code IN_REVIEW}）。
     *
     * @param versionId   提交版本 ID
     * @param submitterId 提交人用户 ID（紧凑格式）
     * @param at          提交时间
     * @throws IllegalStateException 当前状态不允许提交
     */
    public void submitForReview(String versionId, String submitterId, OffsetDateTime at) {
        if (!STATUS_DRAFT.equals(status) && !STATUS_SUPPLEMENT.equals(status)) {
            throw new IllegalStateException("非法支出状态迁移：" + status + " → IN_REVIEW");
        }
        this.status = STATUS_IN_REVIEW;
        this.currentVersionId = versionId;
        this.submittedBy = submitterId;
        this.submittedAt = at;
    }

    /**
     * 退回补正（{@code IN_REVIEW} → {@code SUPPLEMENT}），占用保留。
     *
     * @throws IllegalStateException 当前状态不允许退回
     */
    public void markSupplement() {
        requireStatus(STATUS_IN_REVIEW, "退回补正");
        this.status = STATUS_SUPPLEMENT;
    }

    /**
     * 审批通过（{@code IN_REVIEW} → {@code APPROVED_UNPAID}）。
     *
     * @param at 批准时间
     * @throws IllegalStateException 当前状态不允许批准
     */
    public void approve(OffsetDateTime at) {
        requireStatus(STATUS_IN_REVIEW, "审批通过");
        this.status = STATUS_APPROVED_UNPAID;
        this.approvedAt = at;
    }

    /**
     * 审批拒绝（{@code IN_REVIEW} → {@code REJECTED}）。
     *
     * @throws IllegalStateException 当前状态不允许拒绝
     */
    public void reject() {
        requireStatus(STATUS_IN_REVIEW, "审批拒绝");
        this.status = STATUS_REJECTED;
    }

    /**
     * 撤回/取消（未支付状态 → {@code CANCELLED}）。
     *
     * @throws IllegalStateException 已支付或已终态不允许取消
     */
    public void cancel() {
        if (STATUS_PAID.equals(status) || STATUS_REVERSED.equals(status)
                || STATUS_CANCELLED.equals(status) || STATUS_REJECTED.equals(status)) {
            throw new IllegalStateException("非法支出状态迁移：" + status + " → CANCELLED");
        }
        this.status = STATUS_CANCELLED;
    }

    /**
     * 部分支付（{@code APPROVED_UNPAID} → {@code PARTIALLY_PAID}）。
     *
     * @throws IllegalStateException 当前状态不允许部分支付
     */
    public void markPartiallyPaid() {
        requireStatus(STATUS_APPROVED_UNPAID, "部分支付");
        this.status = STATUS_PARTIALLY_PAID;
    }

    /**
     * 全额支付完成（{@code APPROVED_UNPAID}/{@code PARTIALLY_PAID} → {@code PAID}）。
     *
     * @param at 支付完成时间
     * @throws IllegalStateException 当前状态不允许完成支付
     */
    public void markPaid(OffsetDateTime at) {
        if (!STATUS_APPROVED_UNPAID.equals(status) && !STATUS_PARTIALLY_PAID.equals(status)) {
            throw new IllegalStateException("非法支出状态迁移：" + status + " → PAID");
        }
        this.status = STATUS_PAID;
        this.paidAt = at;
    }

    /**
     * 冲正（已支付/部分支付 → {@code REVERSED}）。
     *
     * @throws IllegalStateException 当前状态不允许冲正
     */
    public void reverse() {
        if (!STATUS_PAID.equals(status) && !STATUS_PARTIALLY_PAID.equals(status)) {
            throw new IllegalStateException("非法支出状态迁移：" + status + " → REVERSED");
        }
        this.status = STATUS_REVERSED;
    }

    /**
     * 部分冲正后回退为部分支付（{@code PAID}/{@code PARTIALLY_PAID} → {@code PARTIALLY_PAID}）。
     *
     * @throws IllegalStateException 当前状态不允许回退
     */
    public void markPartiallyPaidAfterReversal() {
        if (!STATUS_PAID.equals(status) && !STATUS_PARTIALLY_PAID.equals(status)) {
            throw new IllegalStateException("非法支出状态迁移：" + status + " → PARTIALLY_PAID");
        }
        this.status = STATUS_PARTIALLY_PAID;
    }

    private void requireStatus(String expected, String action) {
        if (!expected.equals(status)) {
            throw new IllegalStateException("非法支出状态迁移：" + status + " → " + action);
        }
    }

    public String getRequestNo() {
        return requestNo;
    }

    public void setRequestNo(String requestNo) {
        this.requestNo = requestNo;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getBudgetVersionId() {
        return budgetVersionId;
    }

    public void setBudgetVersionId(String budgetVersionId) {
        this.budgetVersionId = budgetVersionId;
    }

    public String getBudgetLineId() {
        return budgetLineId;
    }

    public void setBudgetLineId(String budgetLineId) {
        this.budgetLineId = budgetLineId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    public String getPayeeName() {
        return payeeName;
    }

    public void setPayeeName(String payeeName) {
        this.payeeName = payeeName;
    }

    public String getPayeeBankAccountMasked() {
        return payeeBankAccountMasked;
    }

    public void setPayeeBankAccountMasked(String payeeBankAccountMasked) {
        this.payeeBankAccountMasked = payeeBankAccountMasked;
    }

    public String getPayeeBankAccountEncrypted() {
        return payeeBankAccountEncrypted;
    }

    public void setPayeeBankAccountEncrypted(String payeeBankAccountEncrypted) {
        this.payeeBankAccountEncrypted = payeeBankAccountEncrypted;
    }

    public String getPayeeBankName() {
        return payeeBankName;
    }

    public void setPayeeBankName(String payeeBankName) {
        this.payeeBankName = payeeBankName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCurrentVersionId() {
        return currentVersionId;
    }

    public void setCurrentVersionId(String currentVersionId) {
        this.currentVersionId = currentVersionId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getSubmittedBy() {
        return submittedBy;
    }

    public void setSubmittedBy(String submittedBy) {
        this.submittedBy = submittedBy;
    }

    public OffsetDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(OffsetDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    public OffsetDateTime getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(OffsetDateTime approvedAt) {
        this.approvedAt = approvedAt;
    }

    public OffsetDateTime getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(OffsetDateTime paidAt) {
        this.paidAt = paidAt;
    }
}
