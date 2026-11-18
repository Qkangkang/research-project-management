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
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * {@code reconciliation_batch} 对账批次 PO（T-FUND-009；技术文档 02 §3.4、05 §3.6）。
 *
 * <p>{@code batch_no} 唯一；对账期间 {@code period_end >= period_start}；
 * 差异数由对账任务汇总写入。</p>
 */
@TableName("reconciliation_batch")
public class ReconciliationBatchEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 状态：运行中。 */
    public static final String STATUS_RUNNING = "RUNNING";

    /** 状态：已完成。 */
    public static final String STATUS_COMPLETED = "COMPLETED";

    /** 状态：失败。 */
    public static final String STATUS_FAILED = "FAILED";

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

    /** 批次号（唯一）。 */
    @TableField("batch_no")
    private String batchNo;

    /** 对账期间起。 */
    @TableField("period_start")
    private LocalDate periodStart;

    /** 对账期间止。 */
    @TableField("period_end")
    private LocalDate periodEnd;

    /** 财务来源批次号。 */
    @TableField("source_batch_no")
    private String sourceBatchNo;

    /** 状态。 */
    @TableField("status")
    private String status;

    /** 财务侧合计。 */
    @TableField("total_finance_amount")
    private BigDecimal totalFinanceAmount = BigDecimal.ZERO;

    /** 系统侧合计。 */
    @TableField("total_system_amount")
    private BigDecimal totalSystemAmount = BigDecimal.ZERO;

    /** 差异数量。 */
    @TableField("diff_count")
    private Integer diffCount = 0;

    /** 开始时间。 */
    @TableField("started_at")
    private OffsetDateTime startedAt;

    /** 完成时间。 */
    @TableField("finished_at")
    private OffsetDateTime finishedAt;

    /** 创建人。 */
    @TableField("created_by")
    private String createdBy;

    /**
     * 完成对账（{@code RUNNING} → {@code COMPLETED}）。
     *
     * @param financeAmount 财务侧合计
     * @param systemAmount  系统侧合计
     * @param diffCount     差异数量
     * @param finishedAt    完成时间
     * @throws IllegalArgumentException 合计或差异数非法
     * @throws IllegalStateException    当前状态不允许完成
     */
    public void complete(BigDecimal financeAmount, BigDecimal systemAmount,
                         int diffCount, OffsetDateTime finishedAt) {
        if (financeAmount == null || systemAmount == null || diffCount < 0) {
            throw new IllegalArgumentException("对账合计或差异数不合法");
        }
        if (!STATUS_RUNNING.equals(status)) {
            throw new IllegalStateException("非法对账批次状态迁移：" + status + " → COMPLETED");
        }
        this.totalFinanceAmount = financeAmount;
        this.totalSystemAmount = systemAmount;
        this.diffCount = diffCount;
        this.finishedAt = finishedAt;
        this.status = STATUS_COMPLETED;
    }

    /**
     * 标记对账失败（{@code RUNNING} → {@code FAILED}）。
     *
     * @param finishedAt 完成时间
     * @throws IllegalStateException 当前状态不允许失败
     */
    public void fail(OffsetDateTime finishedAt) {
        if (!STATUS_RUNNING.equals(status)) {
            throw new IllegalStateException("非法对账批次状态迁移：" + status + " → FAILED");
        }
        this.finishedAt = finishedAt;
        this.status = STATUS_FAILED;
    }

    /**
     * 回调差异累计（仅更新差异计数，不改变状态）。
     *
     * @param delta 增量
     */
    public void incrementDiffCount(int delta) {
        this.diffCount = Math.max(0, (diffCount == null ? 0 : diffCount) + delta);
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

    public String getBatchNo() {
        return batchNo;
    }

    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public void setPeriodStart(LocalDate periodStart) {
        this.periodStart = periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public void setPeriodEnd(LocalDate periodEnd) {
        this.periodEnd = periodEnd;
    }

    public String getSourceBatchNo() {
        return sourceBatchNo;
    }

    public void setSourceBatchNo(String sourceBatchNo) {
        this.sourceBatchNo = sourceBatchNo;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public BigDecimal getTotalFinanceAmount() {
        return totalFinanceAmount;
    }

    public void setTotalFinanceAmount(BigDecimal totalFinanceAmount) {
        this.totalFinanceAmount = totalFinanceAmount;
    }

    public BigDecimal getTotalSystemAmount() {
        return totalSystemAmount;
    }

    public void setTotalSystemAmount(BigDecimal totalSystemAmount) {
        this.totalSystemAmount = totalSystemAmount;
    }

    public Integer getDiffCount() {
        return diffCount;
    }

    public void setDiffCount(Integer diffCount) {
        this.diffCount = diffCount;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(OffsetDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public OffsetDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(OffsetDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }
}
