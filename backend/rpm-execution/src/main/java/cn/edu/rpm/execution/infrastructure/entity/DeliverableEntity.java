package cn.edu.rpm.execution.infrastructure.entity;

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
 * {@code deliverable} 交付物 PO（T-EXEC-003）。
 *
 * <p>DDL 无 {@code created_by}/{@code updated_by} 列，因此不继承 {@code BaseEntity}；
 * 只保存 {@code file_id}（文件本体在 rpm-file），{@code sha256} 用于校验一致性
 * （TC-EXEC-014）。</p>
 */
@TableName("deliverable")
public class DeliverableEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 交付物状态：有效。 */
    public static final String STATUS_ACTIVE = "ACTIVE";

    /** 交付物状态：已撤销。 */
    public static final String STATUS_REVOKED = "REVOKED";

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

    /** 项目 ID（project.id）。 */
    @TableField("project_id")
    private String projectId;

    /** 关联里程碑 ID（milestone.id）；可空。 */
    @TableField("milestone_id")
    private String milestoneId;

    /** 关联报告版本 ID（progress_report_version.id）；可空。 */
    @TableField("report_version_id")
    private String reportVersionId;

    /** 文件 ID（file_object.id）。 */
    @TableField("file_id")
    private String fileId;

    /** 交付物名称。 */
    @TableField("title")
    private String title;

    /** 文件内容 SHA-256（可空）。 */
    @TableField("sha256")
    private String sha256;

    /** 状态：ACTIVE / REVOKED。 */
    @TableField("status")
    private String status;

    /** 上传人用户 ID。 */
    @TableField("uploaded_by")
    private String uploadedBy;

    /**
     * 撤销交付物（逻辑撤销，保留历史）。
     *
     * @throws IllegalStateException 当前状态不是 ACTIVE
     */
    public void revoke() {
        if (!STATUS_ACTIVE.equals(status)) {
            throw new IllegalStateException("非法交付物状态迁移：" + status + " → " + STATUS_REVOKED);
        }
        this.status = STATUS_REVOKED;
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

    public String getMilestoneId() {
        return milestoneId;
    }

    public void setMilestoneId(String milestoneId) {
        this.milestoneId = milestoneId;
    }

    public String getReportVersionId() {
        return reportVersionId;
    }

    public void setReportVersionId(String reportVersionId) {
        this.reportVersionId = reportVersionId;
    }

    public String getFileId() {
        return fileId;
    }

    public void setFileId(String fileId) {
        this.fileId = fileId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
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

    public String getUploadedBy() {
        return uploadedBy;
    }

    public void setUploadedBy(String uploadedBy) {
        this.uploadedBy = uploadedBy;
    }
}
