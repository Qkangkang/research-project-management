package cn.edu.rpm.funding.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * {@code payment_import_batch} 支付导入批次 PO（T-FUND-008；技术文档 02 §3.4、05 §3.4）。
 *
 * <p>{@code batch_no} 唯一；文件本体由 rpm-file 管理，只保存 {@code file_id}。
 * 校验人与上传人不得为同一人（数据库 CHECK）。</p>
 */
@TableName("payment_import_batch")
public class PaymentImportBatchEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 状态：已上传。 */
    public static final String STATUS_UPLOADED = "UPLOADED";

    /** 状态：已校验。 */
    public static final String STATUS_VALIDATED = "VALIDATED";

    /** 状态：可应用。 */
    public static final String STATUS_READY = "READY";

    /** 状态：应用中。 */
    public static final String STATUS_APPLYING = "APPLYING";

    /** 状态：已应用。 */
    public static final String STATUS_APPLIED = "APPLIED";

    /** 状态：失败。 */
    public static final String STATUS_FAILED = "FAILED";

    /** 来源：Excel。 */
    public static final String SOURCE_EXCEL = "EXCEL";

    /** 来源：CSV。 */
    public static final String SOURCE_CSV = "CSV";

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

    /** 来源类型：EXCEL / CSV。 */
    @TableField("source_type")
    private String sourceType;

    /** 导入文件 ID（file_object.id）。 */
    @TableField("file_id")
    private String fileId;

    /** 状态。 */
    @TableField("status")
    private String status;

    /** 总行数。 */
    @TableField("total_rows")
    private Integer totalRows = 0;

    /** 成功行数。 */
    @TableField("success_rows")
    private Integer successRows = 0;

    /** 错误行数。 */
    @TableField("error_rows")
    private Integer errorRows = 0;

    /** 上传人。 */
    @TableField("uploaded_by")
    private String uploadedBy;

    /** 校验人（不得等于上传人）。 */
    @TableField("verified_by")
    private String verifiedBy;

    /** 应用人。 */
    @TableField("applied_by")
    private String appliedBy;

    /**
     * 记录校验结果（{@code UPLOADED} → {@code READY} 或保持 {@code UPLOADED}）。
     *
     * @param totalRows   总行数
     * @param successRows 有效行数
     * @param errorRows   错误行数
     * @param verifierId  校验人用户 ID
     * @throws IllegalArgumentException 行数不合法
     * @throws IllegalStateException    当前状态不允许校验
     */
    public void recordValidation(int totalRows, int successRows, int errorRows, String verifierId) {
        if (totalRows < 0 || successRows < 0 || errorRows < 0
                || successRows + errorRows > totalRows) {
            throw new IllegalArgumentException("导入批次行数不合法");
        }
        if (!STATUS_UPLOADED.equals(status) && !STATUS_VALIDATED.equals(status)
                && !STATUS_FAILED.equals(status)) {
            throw new IllegalStateException("非法导入批次状态迁移：" + status + " → VALIDATED");
        }
        this.totalRows = totalRows;
        this.successRows = successRows;
        this.errorRows = errorRows;
        this.verifiedBy = verifierId;
        this.status = errorRows == 0 ? STATUS_READY : STATUS_UPLOADED;
    }

    /**
     * 标记应用中（{@code READY} → {@code APPLYING}）。
     *
     * @throws IllegalStateException 当前状态不允许应用
     */
    public void startApplying() {
        if (!STATUS_READY.equals(status)) {
            throw new IllegalStateException("非法导入批次状态迁移：" + status + " → APPLYING");
        }
        this.status = STATUS_APPLYING;
    }

    /**
     * 标记应用完成（{@code APPLYING} → {@code APPLIED}）。
     *
     * @param appliedBy 应用人用户 ID
     * @throws IllegalStateException 当前状态不允许完成
     */
    public void markApplied(String appliedBy) {
        if (!STATUS_APPLYING.equals(status)) {
            throw new IllegalStateException("非法导入批次状态迁移：" + status + " → APPLIED");
        }
        this.status = STATUS_APPLIED;
        this.appliedBy = appliedBy;
    }

    /**
     * 标记应用失败（{@code APPLYING} → {@code FAILED}）。
     *
     * @throws IllegalStateException 当前状态不允许失败
     */
    public void markFailed() {
        if (!STATUS_APPLYING.equals(status)) {
            throw new IllegalStateException("非法导入批次状态迁移：" + status + " → FAILED");
        }
        this.status = STATUS_FAILED;
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

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getFileId() {
        return fileId;
    }

    public void setFileId(String fileId) {
        this.fileId = fileId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getTotalRows() {
        return totalRows;
    }

    public void setTotalRows(Integer totalRows) {
        this.totalRows = totalRows;
    }

    public Integer getSuccessRows() {
        return successRows;
    }

    public void setSuccessRows(Integer successRows) {
        this.successRows = successRows;
    }

    public Integer getErrorRows() {
        return errorRows;
    }

    public void setErrorRows(Integer errorRows) {
        this.errorRows = errorRows;
    }

    public String getUploadedBy() {
        return uploadedBy;
    }

    public void setUploadedBy(String uploadedBy) {
        this.uploadedBy = uploadedBy;
    }

    public String getVerifiedBy() {
        return verifiedBy;
    }

    public void setVerifiedBy(String verifiedBy) {
        this.verifiedBy = verifiedBy;
    }

    public String getAppliedBy() {
        return appliedBy;
    }

    public void setAppliedBy(String appliedBy) {
        this.appliedBy = appliedBy;
    }
}
