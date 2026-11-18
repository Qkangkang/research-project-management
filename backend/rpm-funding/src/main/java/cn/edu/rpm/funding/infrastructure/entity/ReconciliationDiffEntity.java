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
 * {@code reconciliation_diff} 对账差异 PO（T-FUND-010；技术文档 02 §3.4、05 §3.6）。
 *
 * <p>四类差异：{@code MISSING_PAYMENT} / {@code AMOUNT_MISMATCH} / {@code DUPLICATE} /
 * {@code STATUS_LAG}。关闭/忽略必须经显式方法，且只能从未结状态迁移。</p>
 */
@TableName("reconciliation_diff")
public class ReconciliationDiffEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 差异类型：系统缺失支付。 */
    public static final String TYPE_MISSING_PAYMENT = "MISSING_PAYMENT";

    /** 差异类型：金额不一致。 */
    public static final String TYPE_AMOUNT_MISMATCH = "AMOUNT_MISMATCH";

    /** 差异类型：重复支付。 */
    public static final String TYPE_DUPLICATE = "DUPLICATE";

    /** 差异类型：状态滞后。 */
    public static final String TYPE_STATUS_LAG = "STATUS_LAG";

    /** 状态：待处理。 */
    public static final String STATUS_OPEN = "OPEN";

    /** 状态：处理中。 */
    public static final String STATUS_PROCESSING = "PROCESSING";

    /** 状态：已关闭。 */
    public static final String STATUS_CLOSED = "CLOSED";

    /** 状态：已忽略。 */
    public static final String STATUS_IGNORED = "IGNORED";

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

    /** 对账批次 ID。 */
    @TableField("batch_id")
    private String batchId;

    /** 项目 ID。 */
    @TableField("project_id")
    private String projectId;

    /** 支付记录 ID。 */
    @TableField("payment_record_id")
    private String paymentRecordId;

    /** 差异类型。 */
    @TableField("diff_type")
    private String diffType;

    /** 外部支付单号。 */
    @TableField("external_payment_no")
    private String externalPaymentNo;

    /** 系统侧金额。 */
    @TableField("system_amount")
    private BigDecimal systemAmount;

    /** 财务侧金额。 */
    @TableField("finance_amount")
    private BigDecimal financeAmount;

    /** 责任人。 */
    @TableField("owner_id")
    private String ownerId;

    /** 状态。 */
    @TableField("status")
    private String status;

    /** 处置说明。 */
    @TableField("resolution")
    private String resolution;

    /** 证据文件 ID（jsonb 数组文本）。 */
    @TableField("evidence_file_ids")
    private String evidenceFileIds;

    /** 关闭人。 */
    @TableField("closed_by")
    private String closedBy;

    /** 关闭时间。 */
    @TableField("closed_at")
    private OffsetDateTime closedAt;

    /**
     * 关闭差异（{@code OPEN}/{@code PROCESSING} → {@code CLOSED}）。
     *
     * @param resolution 处置说明
     * @param closedBy   关闭人用户 ID
     * @param at         关闭时间
     * @throws IllegalStateException 当前状态不允许关闭
     */
    public void close(String resolution, String closedBy, OffsetDateTime at) {
        requireUnresolved();
        this.status = STATUS_CLOSED;
        this.resolution = resolution;
        this.closedBy = closedBy;
        this.closedAt = at;
    }

    /**
     * 忽略差异（{@code OPEN}/{@code PROCESSING} → {@code IGNORED}）。
     *
     * @param resolution 忽略说明
     * @param closedBy   操作人用户 ID
     * @param at         操作时间
     * @throws IllegalStateException 当前状态不允许忽略
     */
    public void ignore(String resolution, String closedBy, OffsetDateTime at) {
        requireUnresolved();
        this.status = STATUS_IGNORED;
        this.resolution = resolution;
        this.closedBy = closedBy;
        this.closedAt = at;
    }

    private void requireUnresolved() {
        if (!STATUS_OPEN.equals(status) && !STATUS_PROCESSING.equals(status)) {
            throw new IllegalStateException("非法差异状态迁移：当前 " + status);
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

    public String getBatchId() {
        return batchId;
    }

    public void setBatchId(String batchId) {
        this.batchId = batchId;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getPaymentRecordId() {
        return paymentRecordId;
    }

    public void setPaymentRecordId(String paymentRecordId) {
        this.paymentRecordId = paymentRecordId;
    }

    public String getDiffType() {
        return diffType;
    }

    public void setDiffType(String diffType) {
        this.diffType = diffType;
    }

    public String getExternalPaymentNo() {
        return externalPaymentNo;
    }

    public void setExternalPaymentNo(String externalPaymentNo) {
        this.externalPaymentNo = externalPaymentNo;
    }

    public BigDecimal getSystemAmount() {
        return systemAmount;
    }

    public void setSystemAmount(BigDecimal systemAmount) {
        this.systemAmount = systemAmount;
    }

    public BigDecimal getFinanceAmount() {
        return financeAmount;
    }

    public void setFinanceAmount(BigDecimal financeAmount) {
        this.financeAmount = financeAmount;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getResolution() {
        return resolution;
    }

    public void setResolution(String resolution) {
        this.resolution = resolution;
    }

    public String getEvidenceFileIds() {
        return evidenceFileIds;
    }

    public void setEvidenceFileIds(String evidenceFileIds) {
        this.evidenceFileIds = evidenceFileIds;
    }

    public String getClosedBy() {
        return closedBy;
    }

    public void setClosedBy(String closedBy) {
        this.closedBy = closedBy;
    }

    public OffsetDateTime getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(OffsetDateTime closedAt) {
        this.closedAt = closedAt;
    }
}
