package cn.edu.rpm.iam.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * {@code iam_permission} 功能权限点 PO（T-IAM-003）。
 *
 * <p>对应《02-数据模型与数据库设计.md》§3.1 与 V01 基线 DDL。权限点为受控字典表：</p>
 * <ul>
 *   <li>{@code code} 权限码全局唯一（uk_iam_permission_code），如 {@code project:view}；</li>
 *   <li>{@code module} 所属功能模块，供权限点按模块维护与筛选；</li>
 *   <li>V01 该表仅含 {@code created_at} 一个审计时间列（DDL 权威结构），
 *       无 updated_at / created_by / updated_by / version，故<b>不继承</b>
 *       {@link cn.edu.rpm.common.entity.BaseEntity}，也无更新语义（仅插入与查询）。</li>
 * </ul>
 */
@TableName("iam_permission")
public class IamPermissionEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键；插入时由 MyBatis-Plus 生成 uuid（与 BaseEntity 同策略）。 */
    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    /** 权限码；全局唯一（uk_iam_permission_code），如 {@code project:view}。 */
    @TableField("code")
    private String code;

    /** 权限点名称。 */
    @TableField("name")
    private String name;

    /** 所属功能模块（如 iam / project / funding）。 */
    @TableField("module")
    private String module;

    /** 权限点说明；可空。 */
    @TableField("description")
    private String description;

    /** 创建时间；INSERT 时由审计填充器写入（该表无其余审计列）。 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public String getModule() {
        return module;
    }

    public void setModule(String module) {
        this.module = module;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
