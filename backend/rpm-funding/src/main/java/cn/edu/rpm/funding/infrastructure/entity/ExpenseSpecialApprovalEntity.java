package cn.edu.rpm.funding.infrastructure.entity;

import cn.edu.rpm.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * {@code expense_special_approval} 支出特批 PO（T-FUND-011；V06）。
 *
 * <p>三类特批：{@code OVER_BUDGET} / {@code NO_BUDGET} / {@code OVER_LIMIT}；
 * 审批人与申请人不得为同一人（数据库 CHECK）；批准必须有审批人、批准时间与有效期。</p>
 */
@TableName("expense_special_approval")
public class ExpenseSpecialApprovalEntity extends BaseEntity {

    /** 特批类型：超预算。 */
    public static final String TYPE_OVER_BUDGET = "OVER_BUDGET";

    /** 特批类型：无预算。 */
    public static final String TYPE_NO_BUDGET = "NO_BUDGET";

    /** 特批类型：超限额。 */
    public static final String TYPE_OVER_LIMIT = "OVER_LIMIT";

    /** 状态：待审批。 */
    public static final String STATUS_PENDING = "PENDING";

    /** 状态：已批准。 */
    public static final String STATUS_APPROVED = "APPROVED";

    /** 状态：已拒绝。 */
    public static final String STATUS_REJECTED = "REJECTED";

    /** 状态：已取消。 */
    public static final String STATUS_CANCELLED = "CANCELLED";

    /** 状态：已过期。 */
    public static final String STATUS_EXPIRED = "EXPIRED";

    /** 支出 ID。 */
    @TableField("expense_request_id")
    private String expenseRequestId;

    /** 支出版本 ID。 */
    @TableField("expense_version_id")
    private String expenseVersionId;

    /** 特批类型。 */
    @TableField("special_type")
    private String specialType;

    /** 预算版本 ID。 */
    @TableField("budget_version_id")
    private String budgetVersionId;

    /** 预算科目 ID。 */
    @TableField("budget_line_id")
    private String budgetLineId;

    /** 超出金额。 */
    @TableField("over_amount")
    private BigDecimal overAmount;

    /** 特批理由。 */
    @TableField("reason")
    private String reason;

    /** 申请人。 */
    @TableField("applicant_id")
    private String applicantId;

    /** 申请人姓名。 */
    @TableField("applicant_name")
    private String applicantName;

    /** 证据文件 ID（jsonb 数组文本）。 */
    @TableField("evidence_file_ids")
    private String evidenceFileIds;

    /** 状态。 */
    @TableField("status")
    private String status;

    /** 审批实例 ID。 */
    @TableField("wf_instance_id")
    private String wfInstanceId;

    /** 特批审批人。 */
    @TableField("approver_id")
    private String approverId;

    /** 批准时间。 */
    @TableField("approved_at")
    private OffsetDateTime approvedAt;

    /** 拒绝理由。 */
    @TableField("rejection_reason")
    private String rejectionReason;

    /** 有效期。 */
    @TableField("valid_until")
    private LocalDate validUntil;

    /** 特批生成的预算调整版本 ID。 */
    @TableField("adjustment_version_id")
    private String adjustmentVersionId;

    /**
     * 批准特批（{@code PENDING} → {@code APPROVED}）。
     *
     * @param approverId           特批审批人用户 ID（紧凑格式）
     * @param approvedAt           批准时间
     * @param validUntil           有效期
     * @param adjustmentVersionId  预算调整版本 ID；无调整版本为 {@code null}
     * @throws IllegalStateException 当前状态不是 {@code PENDING}
     */
    public void approve(String approverId, OffsetDateTime approvedAt, LocalDate validUntil,
                        String adjustmentVersionId) {
        requireStatus(STATUS_PENDING, "批准");
        if (approverId == null || approverId.isBlank() || approvedAt == null || validUntil == null) {
            throw new IllegalArgumentException("批准人、批准时间与有效期不能为空");
        }
        this.status = STATUS_APPROVED;
        this.approverId = approverId;
        this.approvedAt = approvedAt;
        this.validUntil = validUntil;
        this.adjustmentVersionId = adjustmentVersionId;
    }

    /**
     * 拒绝特批（{@code PENDING} → {@code REJECTED}）。
     *
     * @param rejectionReason 拒绝理由（必填）
     * @throws IllegalStateException 当前状态不是 {@code PENDING}
     */
    public void reject(String rejectionReason) {
        requireStatus(STATUS_PENDING, "拒绝");
        if (rejectionReason == null || rejectionReason.isBlank()) {
            throw new IllegalArgumentException("拒绝理由不能为空");
        }
        this.status = STATUS_REJECTED;
        this.rejectionReason = rejectionReason;
    }

    /**
     * 取消特批（{@code PENDING} → {@code CANCELLED}）。
     *
     * @throws IllegalStateException 当前状态不是 {@code PENDING}
     */
    public void cancel() {
        requireStatus(STATUS_PENDING, "取消");
        this.status = STATUS_CANCELLED;
    }

    /**
     * 有效期届满（{@code APPROVED} → {@code EXPIRED}）。
     *
     * @throws IllegalStateException 当前状态不是 {@code APPROVED}
     */
    public void expire() {
        requireStatus(STATUS_APPROVED, "过期");
        this.status = STATUS_EXPIRED;
    }

    /**
     * 绑定审批实例 ID（发起特批后回写）。
     *
     * @param wfInstanceId 审批实例 ID
     */
    public void attachWorkflowInstance(String wfInstanceId) {
        this.wfInstanceId = wfInstanceId;
    }

    private void requireStatus(String expected, String action) {
        if (!expected.equals(status)) {
            throw new IllegalStateException("非法特批状态迁移：" + status + " → " + action);
        }
    }

    public String getExpenseRequestId() {
        return expenseRequestId;
    }

    public void setExpenseRequestId(String expenseRequestId) {
        this.expenseRequestId = expenseRequestId;
    }

    public String getExpenseVersionId() {
        return expenseVersionId;
    }

    public void setExpenseVersionId(String expenseVersionId) {
        this.expenseVersionId = expenseVersionId;
    }

    public String getSpecialType() {
        return specialType;
    }

    public void setSpecialType(String specialType) {
        this.specialType = specialType;
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

    public BigDecimal getOverAmount() {
        return overAmount;
    }

    public void setOverAmount(BigDecimal overAmount) {
        this.overAmount = overAmount;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getApplicantId() {
        return applicantId;
    }

    public void setApplicantId(String applicantId) {
        this.applicantId = applicantId;
    }

    public String getApplicantName() {
        return applicantName;
    }

    public void setApplicantName(String applicantName) {
        this.applicantName = applicantName;
    }

    public String getEvidenceFileIds() {
        return evidenceFileIds;
    }

    public void setEvidenceFileIds(String evidenceFileIds) {
        this.evidenceFileIds = evidenceFileIds;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getWfInstanceId() {
        return wfInstanceId;
    }

    public void setWfInstanceId(String wfInstanceId) {
        this.wfInstanceId = wfInstanceId;
    }

    public String getApproverId() {
        return approverId;
    }

    public void setApproverId(String approverId) {
        this.approverId = approverId;
    }

    public OffsetDateTime getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(OffsetDateTime approvedAt) {
        this.approvedAt = approvedAt;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public LocalDate getValidUntil() {
        return validUntil;
    }

    public void setValidUntil(LocalDate validUntil) {
        this.validUntil = validUntil;
    }

    public String getAdjustmentVersionId() {
        return adjustmentVersionId;
    }

    public void setAdjustmentVersionId(String adjustmentVersionId) {
        this.adjustmentVersionId = adjustmentVersionId;
    }
}
