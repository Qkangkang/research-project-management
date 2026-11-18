package cn.edu.rpm.iam.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.Version;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * {@code iam_temp_grant} 临时授权 PO（T-IAM-005）。
 *
 * <p>对应《02-数据模型与数据库设计.md》§3.1 与 V01 基线 DDL。临时授权在有效期内
 * 为被授权人追加单项权限（如项目查看、代理审批）：</p>
 * <ul>
 *   <li>{@code grantee_id} 被授权人（iam_user.id，ON DELETE CASCADE），
 *       {@code granted_by} 授权操作人（iam_user.id），两者必填；</li>
 *   <li>{@code resource_type} + {@code resource_id} + {@code permission_code} 定位
 *       "对某资源的某权限"，如 PROJECT + 项目 ID + project:view；</li>
 *   <li>{@code valid_from} / {@code valid_to} 均非空且 valid_to &gt; valid_from
 *       （ck_temp_grant_valid），无"长期有效"分支；</li>
 *   <li>{@code status} 枚举：ACTIVE / EXPIRED / REVOKED（ck_temp_grant_status）；</li>
 *   <li>V01 该表审计列为 created_at / updated_at / version（DDL 权威结构，授权操作人
 *       由 granted_by 承载），无 created_by / updated_by，故<b>不继承</b> BaseEntity，
 *       时间填充与乐观锁在本类内声明；查询走 idx_temp_grant_grantee_valid。</li>
 * </ul>
 */
@TableName("iam_temp_grant")
public class IamTempGrantEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 状态枚举值：生效中（ck_temp_grant_status）。 */
    public static final String STATUS_ACTIVE = "ACTIVE";

    /** 主键；插入时由 MyBatis-Plus 生成 uuid（与 BaseEntity 同策略）。 */
    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    /** 被授权人用户 ID（iam_user.id，ON DELETE CASCADE）。 */
    @TableField("grantee_id")
    private String granteeId;

    /** 授权资源类型，如 PROJECT。 */
    @TableField("resource_type")
    private String resourceType;

    /** 授权资源 ID（uuid）。 */
    @TableField("resource_id")
    private String resourceId;

    /** 授权权限码，如 project:view。 */
    @TableField("permission_code")
    private String permissionCode;

    /** 授权原因（审计必需，如出差代理）。 */
    @TableField("reason")
    private String reason;

    /** 授权有效期起（含）。 */
    @TableField("valid_from")
    private OffsetDateTime validFrom;

    /** 授权有效期止（不含）；非空（ck_temp_grant_valid 保证大于 valid_from）。 */
    @TableField("valid_to")
    private OffsetDateTime validTo;

    /** 授权操作人用户 ID（iam_user.id）。 */
    @TableField("granted_by")
    private String grantedBy;

    /** 授权状态：ACTIVE / EXPIRED / REVOKED（ck_temp_grant_status）。 */
    @TableField("status")
    private String status;

    /** 创建时间；INSERT 时由审计填充器写入。 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    /** 最后更新时间；INSERT/UPDATE 时由审计填充器在字段为 null 时写入。 */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;

    /** 乐观锁版本号；更新语句以此字段旧值作为并发条件。 */
    @Version
    @TableField(value = "version")
    private Integer version = 0;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getGranteeId() {
        return granteeId;
    }

    public void setGranteeId(String granteeId) {
        this.granteeId = granteeId;
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }

    public String getPermissionCode() {
        return permissionCode;
    }

    public void setPermissionCode(String permissionCode) {
        this.permissionCode = permissionCode;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public OffsetDateTime getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(OffsetDateTime validFrom) {
        this.validFrom = validFrom;
    }

    public OffsetDateTime getValidTo() {
        return validTo;
    }

    public void setValidTo(OffsetDateTime validTo) {
        this.validTo = validTo;
    }

    public String getGrantedBy() {
        return grantedBy;
    }

    public void setGrantedBy(String grantedBy) {
        this.grantedBy = grantedBy;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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
