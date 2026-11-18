package cn.edu.rpm.iam.infrastructure.entity;

import cn.edu.rpm.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * {@code iam_role} 角色 PO（T-IAM-002）。
 *
 * <p>对应《02-数据模型与数据库设计.md》§3.1 与 V01 基线 DDL。字段与列逐一映射：</p>
 * <ul>
 *   <li>{@code code} 角色编码全局唯一（uk_iam_role_code），如 PI / DEPT_SECRETARY / RESEARCH_ADMIN；</li>
 *   <li>{@code scope_type} 角色数据范围类型枚举：SELF / PROJECT / DEPT / DEPT_TREE / ALL / CUSTOM
 *       （ck_iam_role_scope），供 Batch-4 数据范围解析使用；</li>
 *   <li>{@code status} 枚举：ACTIVE / INACTIVE（ck_iam_role_status）；</li>
 *   <li>审计字段由 {@link BaseEntity} 承载。</li>
 * </ul>
 */
@TableName("iam_role")
public class IamRoleEntity extends BaseEntity {

    /** 角色编码；全局唯一（uk_iam_role_code）。 */
    @TableField("code")
    private String code;

    /** 角色名称。 */
    @TableField("name")
    private String name;

    /** 数据范围类型：SELF / PROJECT / DEPT / DEPT_TREE / ALL / CUSTOM（ck_iam_role_scope）。 */
    @TableField("scope_type")
    private String scopeType;

    /** 角色描述；可空。 */
    @TableField("description")
    private String description;

    /** 角色状态：ACTIVE / INACTIVE（ck_iam_role_status）。 */
    @TableField("status")
    private String status;

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

    public String getScopeType() {
        return scopeType;
    }

    public void setScopeType(String scopeType) {
        this.scopeType = scopeType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
