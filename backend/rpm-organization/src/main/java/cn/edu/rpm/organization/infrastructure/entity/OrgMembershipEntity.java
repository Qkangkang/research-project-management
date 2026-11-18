package cn.edu.rpm.organization.infrastructure.entity;

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
 * {@code org_membership} 人员归属 PO（T-ORG-002）。
 *
 * <p>对应《02-数据模型与数据库设计.md》§3.1 与 V01 基线 DDL。人员与组织的归属关系
 * （一人可归属多个部门，其中至多一个为主要部门）：</p>
 * <ul>
 *   <li>{@code user_id} 人员（iam_user.id，ON DELETE CASCADE），{@code org_id} 部门
 *       （org_unit.id，ON DELETE RESTRICT）；</li>
 *   <li>{@code is_primary} 主要部门标记；V01～V07 无 (user_id, is_primary) 唯一约束
 *       （DDL 权威结构），主部门唯一性由查询语义与数据治理保证；</li>
 *   <li>{@code valid_from} / {@code valid_to} 归属有效期，valid_to 为空视为长期
 *       （ck_org_membership_valid 要求 valid_to &gt; valid_from）；</li>
 *   <li>{@code status} 枚举：ACTIVE / INACTIVE（ck_org_membership_status）；</li>
 *   <li>V01 该表审计列为 created_at / updated_at / version（DDL 权威结构），无
 *       created_by / updated_by，故<b>不继承</b> BaseEntity，时间填充与乐观锁在本类内声明；
 *       查询走 idx_org_membership_user / idx_org_membership_org。</li>
 * </ul>
 */
@TableName("org_membership")
public class OrgMembershipEntity implements Serializable {

    /** 状态枚举值：生效中（ck_org_membership_status）。 */
    public static final String STATUS_ACTIVE = "ACTIVE";

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键；插入时由 MyBatis-Plus 生成 uuid（与 BaseEntity 同策略）。 */
    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    /** 人员用户 ID（iam_user.id，ON DELETE CASCADE）。 */
    @TableField("user_id")
    private String userId;

    /** 部门组织 ID（org_unit.id，ON DELETE RESTRICT）。 */
    @TableField("org_id")
    private String orgId;

    /** 岗位名称；可空。 */
    @TableField("position_name")
    private String positionName;

    /** 主要部门标记；一人至多一个主要部门。 */
    @TableField("is_primary")
    private Boolean isPrimary;

    /** 归属有效期起（含）。 */
    @TableField("valid_from")
    private OffsetDateTime validFrom;

    /** 归属有效期止（不含）；null 表示长期。 */
    @TableField("valid_to")
    private OffsetDateTime validTo;

    /** 归属状态：ACTIVE / INACTIVE（ck_org_membership_status）。 */
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

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getOrgId() {
        return orgId;
    }

    public void setOrgId(String orgId) {
        this.orgId = orgId;
    }

    public String getPositionName() {
        return positionName;
    }

    public void setPositionName(String positionName) {
        this.positionName = positionName;
    }

    public Boolean getIsPrimary() {
        return isPrimary;
    }

    public void setIsPrimary(Boolean isPrimary) {
        this.isPrimary = isPrimary;
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
