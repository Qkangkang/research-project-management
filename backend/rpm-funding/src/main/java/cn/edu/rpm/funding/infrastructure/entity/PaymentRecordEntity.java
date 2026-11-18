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
 * {@code payment_record} 支付记录 PO（T-FUND-007；技术文档 02 §3.4）。
 *
 * <p>外部支付单号唯一（{@code uk_payment_external_no}）；币种一期仅 CNY；
 * 原始报文保存为 JSON 文本，供对账追溯。</p>
 */
@TableName("payment_record")
public class PaymentRecordEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 状态：已接收。 */
    public static final String STATUS_RECEIVED = "RECEIVED";

    /** 状态：已应用。 */
    public static final String STATUS_APPLIED = "APPLIED";

    /** 状态：已冲正。 */
    public static final String STATUS_REVERSED = "REVERSED";

    /** 状态：有差异。 */
    public static final String STATUS_DIFF = "DIFF";

    /** 主键；插入时由 MyBatis-Plus 生成 uuid。 */
    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    /** 创建时间；INSERT 时由审计填充器写入。 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    /** 最后更新时间；INSERT/UPDATE 时由审计填充器写入。 */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;

    /** 乐观锁版本号。 */
    @Version
    @TableField(value = "version")
    private Integer version = 0;

    /** 项目 ID。 */
    @TableField("project_id")
    private String projectId;

    /** 关联支出 ID。 */
    @TableField("expense_request_id")
    private String expenseRequestId;

    /** 外部支付单号（唯一）。 */
    @TableField("external_payment_no")
    private String externalPaymentNo;

    /** 外部事件 ID。 */
    @TableField("external_event_id")
    private String externalEventId;

    /** 支付金额。 */
    @TableField("amount")
    private BigDecimal amount;

    /** 币种（一期 CNY）。 */
    @TableField("currency")
    private String currency;

    /** 支付时间。 */
    @TableField("paid_at")
    private OffsetDateTime paidAt;

    /** 凭证号。 */
    @TableField("voucher_no")
    private String voucherNo;

    /** 状态。 */
    @TableField("status")
    private String status;

    /** 原始报文（jsonb）。 */
    @TableField("raw_payload")
    private String rawPayload;

    /**
     * 标记支付已入账（{@code RECEIVED} → {@code APPLIED}）。
     *
     * @throws IllegalStateException 当前状态不允许入账
     */
    public void markApplied() {
        requireStatus(STATUS_RECEIVED, "入账");
        this.status = STATUS_APPLIED;
    }

    /**
     * 标记支付已冲正（{@code APPLIED} → {@code REVERSED}）。
     *
     * @throws IllegalStateException 当前状态不允许冲正
     */
    public void markReversed() {
        requireStatus(STATUS_APPLIED, "冲正");
        this.status = STATUS_REVERSED;
    }

    /**
     * 标记支付存在差异（{@code RECEIVED} → {@code DIFF}）。
     *
     * @throws IllegalStateException 当前状态不允许标记差异
     */
    public void markDiff() {
        requireStatus(STATUS_RECEIVED, "标记差异");
        this.status = STATUS_DIFF;
    }

    private void requireStatus(String expected, String action) {
        if (!expected.equals(status)) {
            throw new IllegalStateException("非法支付记录状态迁移：" + status + " → " + action);
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

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getExpenseRequestId() {
        return expenseRequestId;
    }

    public void setExpenseRequestId(String expenseRequestId) {
        this.expenseRequestId = expenseRequestId;
    }

    public String getExternalPaymentNo() {
        return externalPaymentNo;
    }

    public void setExternalPaymentNo(String externalPaymentNo) {
        this.externalPaymentNo = externalPaymentNo;
    }

    public String getExternalEventId() {
        return externalEventId;
    }

    public void setExternalEventId(String externalEventId) {
        this.externalEventId = externalEventId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public OffsetDateTime getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(OffsetDateTime paidAt) {
        this.paidAt = paidAt;
    }

    public String getVoucherNo() {
        return voucherNo;
    }

    public void setVoucherNo(String voucherNo) {
        this.voucherNo = voucherNo;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRawPayload() {
        return rawPayload;
    }

    public void setRawPayload(String rawPayload) {
        this.rawPayload = rawPayload;
    }
}
