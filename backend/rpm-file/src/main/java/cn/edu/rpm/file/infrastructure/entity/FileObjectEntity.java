package cn.edu.rpm.file.infrastructure.entity;

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
 * {@code file_object} 文件元数据 PO（T-FILE-001）。
 *
 * <p>对应 V01 基线 DDL：不继承 {@link cn.edu.rpm.common.entity.BaseEntity}，
 * 因为该表用 {@code uploaded_by} 记录上传人，且没有 {@code created_by}/{@code updated_by}。
 * 数据库只存元数据，文件本体在 MinIO/S3（技术文档 02 §1 原则 10）。</p>
 */
@TableName("file_object")
public class FileObjectEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键；插入时由 MyBatis-Plus 生成 uuid。 */
    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    /** 对象存储桶。 */
    @TableField("bucket")
    private String bucket;

    /** 对象键（uk_file_object_key 全局唯一）。 */
    @TableField("object_key")
    private String objectKey;

    /** 原始文件名。 */
    @TableField("original_name")
    private String originalName;

    /** MIME 类型。 */
    @TableField("mime_type")
    private String mimeType;

    /** 字节数。 */
    @TableField("size_bytes")
    private Long sizeBytes;

    /** 内容 SHA-256（64 位十六进制）。 */
    @TableField("sha256")
    private String sha256;

    /** 文件状态：UPLOADING / QUARANTINED / AVAILABLE / REJECTED / DELETED。 */
    @TableField("status")
    private String status;

    /** 扫描状态：PENDING / PASSED / FAILED / SKIPPED。 */
    @TableField("scan_status")
    private String scanStatus;

    /** 上传人用户 ID。 */
    @TableField("uploaded_by")
    private String uploadedBy;

    /** 创建时间；INSERT 时由审计填充器写入。 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    /** 更新时间；INSERT/UPDATE 时由审计填充器写入。 */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;

    /** 乐观锁版本号。 */
    @Version
    @TableField("version")
    private Integer version = 0;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public void setObjectKey(String objectKey) {
        this.objectKey = objectKey;
    }

    public String getOriginalName() {
        return originalName;
    }

    public void setOriginalName(String originalName) {
        this.originalName = originalName;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(Long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getScanStatus() {
        return scanStatus;
    }

    public void setScanStatus(String scanStatus) {
        this.scanStatus = scanStatus;
    }

    public String getUploadedBy() {
        return uploadedBy;
    }

    public void setUploadedBy(String uploadedBy) {
        this.uploadedBy = uploadedBy;
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
}
