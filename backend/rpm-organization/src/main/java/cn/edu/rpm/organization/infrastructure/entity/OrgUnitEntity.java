package cn.edu.rpm.organization.infrastructure.entity;

import cn.edu.rpm.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * {@code org_unit} 组织树 PO（T-ORG-001）。
 *
 * <p>对应《02-数据模型与数据库设计.md》§3.1 与 V01 基线 DDL。字段与列逐一映射：</p>
 * <ul>
 *   <li>{@code parent_id} 父节点自引用（org_unit.id，ON DELETE RESTRICT），根节点为 null；</li>
 *   <li>{@code code} 组织编码全局唯一（uk_org_unit_code）；</li>
 *   <li>{@code path} 树路径（字符串拼接存储，如 {@code /UNIV/CSE/CSE01}），子树查询用
 *       前缀 LIKE 实现（V01 未启用 ltree 扩展）；</li>
 *   <li>{@code unit_type} 枚举：UNIVERSITY / DEPARTMENT / INSTITUTE / GROUP / OTHER
 *       （ck_org_unit_type）；</li>
 *   <li>{@code status} 枚举：ACTIVE / INACTIVE（ck_org_unit_status）；</li>
 *   <li>审计字段由 {@link BaseEntity} 承载；子节点查询走 idx_org_unit_parent。</li>
 * </ul>
 */
@TableName("org_unit")
public class OrgUnitEntity extends BaseEntity {

    /** 状态枚举值：生效中（ck_org_unit_status）。 */
    public static final String STATUS_ACTIVE = "ACTIVE";

    /** 父组织 ID（org_unit.id 自引用）；根节点为 null。 */
    @TableField("parent_id")
    private String parentId;

    /** 组织编码；全局唯一（uk_org_unit_code）。 */
    @TableField("code")
    private String code;

    /** 组织名称。 */
    @TableField("name")
    private String name;

    /** 组织类型：UNIVERSITY / DEPARTMENT / INSTITUTE / GROUP / OTHER（ck_org_unit_type）。 */
    @TableField("unit_type")
    private String unitType;

    /** 树路径（字符串拼接，如 /UNIV/CSE）；子树前缀查询依据。 */
    @TableField("path")
    private String path;

    /** 同层展示排序号。 */
    @TableField("sort_order")
    private Integer sortOrder;

    /** 组织状态：ACTIVE / INACTIVE（ck_org_unit_status）。 */
    @TableField("status")
    private String status;

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUnitType() {
        return unitType;
    }

    public void setUnitType(String unitType) {
        this.unitType = unitType;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
