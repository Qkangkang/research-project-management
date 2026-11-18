package cn.edu.rpm.iam.infrastructure.entity;

import cn.edu.rpm.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * {@code iam_user_role} 用户-角色-组织关联 PO（T-IAM-004）。
 *
 * <p>对应《02-数据模型与数据库设计.md》§3.1 与 V01 基线 DDL。字段与列逐一映射：</p>
 * <ul>
 *   <li>三元组（user_id, role_id, org_id）承载"在某组织范围内担任某角色"的授权语义；
 *       V01 主键为代理键 {@code id}（权威 DDL），任务拆解所述"复合主键"以 DDL 为准；</li>
 *   <li>{@code valid_from} / {@code valid_to} 授权有效期，valid_to 为空视为长期有效
 *       （ck_iam_user_role_valid 要求 valid_to &gt; valid_from）；</li>
 *   <li>{@code status} 枚举：ACTIVE / EXPIRED / REVOKED（ck_iam_user_role_status）；</li>
 *   <li>审计字段由 {@link BaseEntity} 承载；查询走 idx_iam_user_role_user / idx_iam_user_role_valid。</li>
 * </ul>
 */
@TableName("iam_user_role")
public class IamUserRoleEntity extends BaseEntity {

    /** 状态枚举值：生效中（ck_iam_user_role_status）。 */
    public static final String STATUS_ACTIVE = "ACTIVE";

    /** 用户 ID（iam_user.id，ON DELETE CASCADE）。 */
    @TableField("user_id")
    private String userId;

    /** 角色 ID（iam_role.id，ON DELETE RESTRICT）。 */
    @TableField("role_id")
    private String roleId;

    /** 授权范围组织 ID（org_unit.id）；可空，空表示全院范围。 */
    @TableField("org_id")
    private String orgId;

    /** 授权有效期起（含）。 */
    @TableField("valid_from")
    private OffsetDateTime validFrom;

    /** 授权有效期止（不含）；null 表示长期有效。 */
    @TableField("valid_to")
    private OffsetDateTime validTo;

    /** 关联状态：ACTIVE / EXPIRED / REVOKED（ck_iam_user_role_status）。 */
    @TableField("status")
    private String status;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getRoleId() {
        return roleId;
    }

    public void setRoleId(String roleId) {
        this.roleId = roleId;
    }

    public String getOrgId() {
        return orgId;
    }

    public void setOrgId(String orgId) {
        this.orgId = orgId;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
