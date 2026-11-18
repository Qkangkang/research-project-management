package cn.edu.rpm.funding.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * {@code payment_import_row} 支付导入行 PO（T-FUND-008）。
 *
 * <p>{@code (batch_id, row_no)} 唯一；行级状态与错误码用于导入结果展示。</p>
 */
@TableName("payment_import_row")
public class PaymentImportRowEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 状态：待校验。 */
    public static final String STATUS_PENDING = "PENDING";

    /** 状态：校验通过。 */
    public static final String STATUS_VALID = "VALID";

    /** 状态：校验失败。 */
    public static final String STATUS_INVALID = "INVALID";

    /** 状态：已应用。 */
    public static final String STATUS_APPLIED = "APPLIED";

    /** 状态：已跳过。 */
    public static final String STATUS_SKIPPED = "SKIPPED";

    /** 主键；插入时由 MyBatis-Plus 生成 uuid。 */
    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    /** 创建时间；INSERT 时由审计填充器写入。 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    /** 批次 ID。 */
    @TableField("batch_id")
    private String batchId;

    /** 行号（从 1 开始）。 */
    @TableField("row_no")
    private Integer rowNo;

    /** 原始行数据（jsonb）。 */
    @TableField("raw_data")
    private String rawData;

    /** 匹配到的支出 ID。 */
    @TableField("matched_expense_id")
    private String matchedExpenseId;

    /** 外部支付单号。 */
    @TableField("external_payment_no")
    private String externalPaymentNo;

    /** 行金额。 */
    @TableField("amount")
    private BigDecimal amount;

    /** 行状态。 */
    @TableField("status")
    private String status;

    /** 错误码。 */
    @TableField("error_code")
    private String errorCode;

    /** 错误说明。 */
    @TableField("error_message")
    private String errorMessage;

    /**
     * 标记校验通过（{@code PENDING} → {@code VALID}）。
     *
     * @throws IllegalStateException 当前状态不允许校验
     */
    public void markValid() {
        requirePending();
        this.status = STATUS_VALID;
    }

    /**
     * 标记校验失败（{@code PENDING} → {@code INVALID}）。
     *
     * @param errorCode    错误码
     * @param errorMessage 错误说明
     * @throws IllegalStateException 当前状态不允许校验
     */
    public void markInvalid(String errorCode, String errorMessage) {
        requirePending();
        this.status = STATUS_INVALID;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    /**
     * 标记已应用（{@code VALID} → {@code APPLIED}）。
     *
     * @param matchedExpenseId 匹配到的支出 ID
     * @throws IllegalStateException 当前状态不允许应用
     */
    public void markApplied(String matchedExpenseId) {
        if (!STATUS_VALID.equals(status)) {
            throw new IllegalStateException("非法导入行状态迁移：" + status + " → APPLIED");
        }
        this.status = STATUS_APPLIED;
        this.matchedExpenseId = matchedExpenseId;
    }

    /**
     * 标记已跳过（{@code VALID} → {@code SKIPPED}）。
     *
     * @param errorCode    原因码
     * @param errorMessage 原因说明
     * @throws IllegalStateException 当前状态不允许跳过
     */
    public void markSkipped(String errorCode, String errorMessage) {
        if (!STATUS_VALID.equals(status)) {
            throw new IllegalStateException("非法导入行状态迁移：" + status + " → SKIPPED");
        }
        this.status = STATUS_SKIPPED;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    /**
     * 应用阶段发现业务错误（{@code VALID} → {@code INVALID}）。
     *
     * @param errorCode    错误码
     * @param errorMessage 错误说明
     * @throws IllegalStateException 当前状态不允许回退
     */
    public void markInvalidOnApply(String errorCode, String errorMessage) {
        if (!STATUS_VALID.equals(status)) {
            throw new IllegalStateException("非法导入行状态迁移：" + status + " → INVALID");
        }
        this.status = STATUS_INVALID;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    private void requirePending() {
        if (!STATUS_PENDING.equals(status)) {
            throw new IllegalStateException("非法导入行状态迁移：" + status + " → 校验");
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getBatchId() {
        return batchId;
    }

    public void setBatchId(String batchId) {
        this.batchId = batchId;
    }

    public Integer getRowNo() {
        return rowNo;
    }

    public void setRowNo(Integer rowNo) {
        this.rowNo = rowNo;
    }

    public String getRawData() {
        return rawData;
    }

    public void setRawData(String rawData) {
        this.rawData = rawData;
    }

    public String getMatchedExpenseId() {
        return matchedExpenseId;
    }

    public void setMatchedExpenseId(String matchedExpenseId) {
        this.matchedExpenseId = matchedExpenseId;
    }

    public String getExternalPaymentNo() {
        return externalPaymentNo;
    }

    public void setExternalPaymentNo(String externalPaymentNo) {
        this.externalPaymentNo = externalPaymentNo;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
