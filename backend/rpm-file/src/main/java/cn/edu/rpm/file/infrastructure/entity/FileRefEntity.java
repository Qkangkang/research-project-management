package cn.edu.rpm.file.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * {@code file_ref} 文件业务引用 PO（T-FILE-002）。
 *
 * <p>对应 V01 DDL：唯一约束 {@code uk_file_ref_biz (file_id, biz_type, biz_id, purpose)}；
 * 不继承 {@code BaseEntity}（该表只有 {@code created_by}/{@code created_at}，无更新审计列与版本列）。</p>
 */
@TableName("file_ref")
public class FileRefEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键；插入时由 MyBatis-Plus 生成 uuid。 */
    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    /** 文件对象 ID（file_object.id，ON DELETE RESTRICT）。 */
    @TableField("file_id")
    private String fileId;

    /** 业务类型。 */
    @TableField("biz_type")
    private String bizType;

    /** 业务对象 ID。 */
    @TableField("biz_id")
    private String bizId;

    /** 用途。 */
    @TableField("purpose")
    private String purpose;

    /** 访问级别：INTERNAL / PROJECT / RESTRICTED / PUBLIC。 */
    @TableField("access_level")
    private String accessLevel;

    /** 创建人用户 ID。 */
    @TableField(value = "created_by", fill = FieldFill.INSERT)
    private String createdBy;

    /** 创建时间；INSERT 时由审计填充器写入。 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getFileId() {
        return fileId;
    }

    public void setFileId(String fileId) {
        this.fileId = fileId;
    }

    public String getBizType() {
        return bizType;
    }

    public void setBizType(String bizType) {
        this.bizType = bizType;
    }

    public String getBizId() {
        return bizId;
    }

    public void setBizId(String bizId) {
        this.bizId = bizId;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    public String getAccessLevel() {
        return accessLevel;
    }

    public void setAccessLevel(String accessLevel) {
        this.accessLevel = accessLevel;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
