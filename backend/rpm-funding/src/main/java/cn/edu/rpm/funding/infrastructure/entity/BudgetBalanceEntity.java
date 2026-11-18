package cn.edu.rpm.funding.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * {@code budget_balance} 预算余额投影 PO（T-FUND-003；技术文档 02 §3.4、§4.2）。
 *
 * <p>每个预算科目一行（{@code uk_budget_balance_line}），保存四类金额：
 * {@code approved_amount}（批准额）、{@code reserved_amount}（占用）、
 * {@code approved_unpaid_amount}（已批未付）、{@code paid_amount}（已付）。
 * 非负与 {@code reserved + approved_unpaid + paid <= approved} 由数据库 CHECK 兜底；
 * 在线投影更新前须 {@code FOR UPDATE} 锁定本行。</p>
 */
@TableName("budget_balance")
public class BudgetBalanceEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键；插入时由 MyBatis-Plus 生成 uuid。 */
    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    /** 最后更新时间；INSERT/UPDATE 时由审计填充器写入。 */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;

    /** 乐观锁版本号。 */
    @Version
    @TableField(value = "version")
    private Integer version = 0;

    /** 预算科目 ID（budget_line.id，唯一）。 */
    @TableField("budget_line_id")
    private String budgetLineId;

    /** 批准金额。 */
    @TableField("approved_amount")
    private BigDecimal approvedAmount = BigDecimal.ZERO;

    /** 占用金额。 */
    @TableField("reserved_amount")
    private BigDecimal reservedAmount = BigDecimal.ZERO;

    /** 已批未付金额。 */
    @TableField("approved_unpaid_amount")
    private BigDecimal approvedUnpaidAmount = BigDecimal.ZERO;

    /** 已付金额。 */
    @TableField("paid_amount")
    private BigDecimal paidAmount = BigDecimal.ZERO;

    /**
     * 可用余额：批准额 − 占用 − 已批未付 − 已付。
     *
     * @return 可用金额（不修改实体）
     */
    public BigDecimal availableAmount() {
        return approvedAmount.subtract(reservedAmount)
                .subtract(approvedUnpaidAmount).subtract(paidAmount);
    }

    /**
     * 投影 {@code RESERVE}：占用增加（项目 05 §1.2、08 §10）。
     *
     * @param amount 事件金额（正数）
     * @throws IllegalStateException 金额非正或可用余额不足
     */
    public void reserve(BigDecimal amount) {
        requirePositive(amount);
        requireAvailable(amount);
        reservedAmount = reservedAmount.add(amount);
    }

    /**
     * 投影 {@code RELEASE} 或冲正 {@code RESERVE}：占用减少。
     *
     * @param amount 事件金额（正数）
     * @throws IllegalStateException 金额非正或占用不足
     */
    public void release(BigDecimal amount) {
        requirePositive(amount);
        requireBucket(reservedAmount, amount, "占用");
        reservedAmount = reservedAmount.subtract(amount);
    }

    /**
     * 投影 {@code APPROVE}：占用转为已批未付。
     *
     * @param amount 事件金额（正数）
     * @throws IllegalStateException 金额非正或占用不足
     */
    public void approve(BigDecimal amount) {
        requirePositive(amount);
        requireBucket(reservedAmount, amount, "占用");
        reservedAmount = reservedAmount.subtract(amount);
        approvedUnpaidAmount = approvedUnpaidAmount.add(amount);
    }

    /**
     * 投影 {@code PAY} / {@code PARTIAL_PAY}：已批未付转为已付。
     *
     * @param amount 事件金额（正数）
     * @throws IllegalStateException 金额非正或已批未付不足
     */
    public void settlePayment(BigDecimal amount) {
        requirePositive(amount);
        requireBucket(approvedUnpaidAmount, amount, "已批未付");
        approvedUnpaidAmount = approvedUnpaidAmount.subtract(amount);
        paidAmount = paidAmount.add(amount);
    }

    /**
     * 投影 {@code CANCEL}：已批未付减少。
     *
     * @param amount 事件金额（正数）
     * @throws IllegalStateException 金额非正或已批未付不足
     */
    public void cancelApprovedUnpaid(BigDecimal amount) {
        requirePositive(amount);
        requireBucket(approvedUnpaidAmount, amount, "已批未付");
        approvedUnpaidAmount = approvedUnpaidAmount.subtract(amount);
    }

    /**
     * 投影 {@code REFUND}：已付减少。
     *
     * @param amount 事件金额（正数）
     * @throws IllegalStateException 金额非正或已付不足
     */
    public void refund(BigDecimal amount) {
        requirePositive(amount);
        requireBucket(paidAmount, amount, "已付");
        paidAmount = paidAmount.subtract(amount);
    }

    /**
     * 投影冲正 {@code APPROVE}：已批未付退回占用。
     *
     * @param amount 被冲正事件金额（正数）
     * @throws IllegalStateException 金额非正或已批未付不足
     */
    public void reverseApprove(BigDecimal amount) {
        requirePositive(amount);
        requireBucket(approvedUnpaidAmount, amount, "已批未付");
        approvedUnpaidAmount = approvedUnpaidAmount.subtract(amount);
        reservedAmount = reservedAmount.add(amount);
    }

    /**
     * 投影冲正 {@code PAY} / {@code PARTIAL_PAY}：已付退回已批未付。
     *
     * @param amount 被冲正事件金额（正数）
     * @throws IllegalStateException 金额非正或已付不足
     */
    public void reversePayment(BigDecimal amount) {
        requirePositive(amount);
        requireBucket(paidAmount, amount, "已付");
        paidAmount = paidAmount.subtract(amount);
        approvedUnpaidAmount = approvedUnpaidAmount.add(amount);
    }

    /**
     * 投影冲正 {@code CANCEL}：已批未付恢复。
     *
     * @param amount 被冲正事件金额（正数）
     * @throws IllegalStateException 金额非正或可用余额不足
     */
    public void restoreApprovedUnpaid(BigDecimal amount) {
        requirePositive(amount);
        requireAvailable(amount);
        approvedUnpaidAmount = approvedUnpaidAmount.add(amount);
    }

    /**
     * 投影冲正 {@code REFUND}：已付恢复。
     *
     * @param amount 被冲正事件金额（正数）
     * @throws IllegalStateException 金额非正或可用余额不足
     */
    public void restorePaid(BigDecimal amount) {
        requirePositive(amount);
        requireAvailable(amount);
        paidAmount = paidAmount.add(amount);
    }

    /**
     * 投影 {@code ADJUST}：按带符号增量修正批准额。
     *
     * @param delta 调整增量（正增负减，不得为 0）
     * @throws IllegalStateException 增量为 0 或调整后可用余额为负
     */
    public void adjustApprovedAmount(BigDecimal delta) {
        if (delta == null || delta.compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalStateException("ADJUST 增量不能为 0");
        }
        BigDecimal adjusted = approvedAmount.add(delta);
        if (adjusted.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException("ADJUST 后批准额为负");
        }
        approvedAmount = adjusted;
        requireAvailable(BigDecimal.ZERO);
    }

    private static void requirePositive(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("事件金额必须为正数");
        }
    }

    private static void requireBucket(BigDecimal bucket, BigDecimal amount, String name) {
        if (bucket.compareTo(amount) < 0) {
            throw new IllegalStateException(name + "金额不足，投影将出现负数");
        }
    }

    private void requireAvailable(BigDecimal amount) {
        if (availableAmount().compareTo(amount) < 0) {
            throw new IllegalStateException("可用余额不足，投影将出现负数");
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getBudgetLineId() {
        return budgetLineId;
    }

    public void setBudgetLineId(String budgetLineId) {
        this.budgetLineId = budgetLineId;
    }

    public BigDecimal getApprovedAmount() {
        return approvedAmount;
    }

    public void setApprovedAmount(BigDecimal approvedAmount) {
        this.approvedAmount = approvedAmount;
    }

    public BigDecimal getReservedAmount() {
        return reservedAmount;
    }

    public void setReservedAmount(BigDecimal reservedAmount) {
        this.reservedAmount = reservedAmount;
    }

    public BigDecimal getApprovedUnpaidAmount() {
        return approvedUnpaidAmount;
    }

    public void setApprovedUnpaidAmount(BigDecimal approvedUnpaidAmount) {
        this.approvedUnpaidAmount = approvedUnpaidAmount;
    }

    public BigDecimal getPaidAmount() {
        return paidAmount;
    }

    public void setPaidAmount(BigDecimal paidAmount) {
        this.paidAmount = paidAmount;
    }
}
