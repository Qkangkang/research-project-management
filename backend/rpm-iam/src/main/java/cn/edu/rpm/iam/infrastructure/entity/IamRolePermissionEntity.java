package cn.edu.rpm.iam.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * {@code iam_role_permission} 角色-权限点绑定 PO（Batch-4 / T-IAM-008）。
 *
 * <p>对应 V01 基线 DDL：主键为复合键 {@code (role_id, permission_id)}，无代理键，
 * 故不声明 {@code @TableId}；本批次只做按角色查询（{@code selectList}），
 * 不做单条主键读写。审计列仅 {@code created_at}（与 DDL 一致），插入时由审计填充器写入。</p>
 */
@TableName("iam_role_permission")
public class IamRolePermissionEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 角色 ID（iam_role.id）。 */
    @TableField("role_id")
    private String roleId;

    /** 权限点 ID（iam_permission.id）。 */
    @TableField("permission_id")
    private String permissionId;

    /** 绑定操作人用户 ID；可空。 */
    @TableField("granted_by")
    private String grantedBy;

    /** 创建时间；INSERT 时由审计填充器写入。 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    public String getRoleId() {
        return roleId;
    }

    public void setRoleId(String roleId) {
        this.roleId = roleId;
    }

    public String getPermissionId() {
        return permissionId;
    }

    public void setPermissionId(String permissionId) {
        this.permissionId = permissionId;
    }

    public String getGrantedBy() {
        return grantedBy;
    }

    public void setGrantedBy(String grantedBy) {
        this.grantedBy = grantedBy;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
