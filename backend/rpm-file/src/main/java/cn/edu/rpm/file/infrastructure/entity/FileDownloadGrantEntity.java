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
 * {@code file_download_grant} 短期下载签名 PO（T-FILE-005；V06）。
 *
 * <p>唯一约束 {@code uk_file_download_grant (grant_token)}；撤销后保留记录供审计追溯。
 * 不继承 {@code BaseEntity}（该表无 updated_at/version/created_by）。</p>
 */
@TableName("file_download_grant")
public class FileDownloadGrantEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键；插入时由 MyBatis-Plus 生成 uuid。 */
    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    /** 文件 ID。 */
    @TableField("file_id")
    private String fileId;

    /** 被授权用户 ID。 */
    @TableField("granted_to")
    private String grantedTo;

    /** 签名 token（唯一、不可猜测）。 */
    @TableField("grant_token")
    private String grantToken;

    /** 失效时刻（≤5 分钟）。 */
    @TableField("expires_at")
    private OffsetDateTime expiresAt;

    /** 撤销时刻；未撤销为空。 */
    @TableField("revoked_at")
    private OffsetDateTime revokedAt;

    /** 签发时刻；INSERT 时若未赋值由审计填充器写入。 */
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

    public String getGrantedTo() {
        return grantedTo;
    }

    public void setGrantedTo(String grantedTo) {
        this.grantedTo = grantedTo;
    }

    public String getGrantToken() {
        return grantToken;
    }

    public void setGrantToken(String grantToken) {
        this.grantToken = grantToken;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(OffsetDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public OffsetDateTime getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(OffsetDateTime revokedAt) {
        this.revokedAt = revokedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
