package cn.edu.rpm.workflow.infrastructure.entity;

import cn.edu.rpm.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * {@code wf_template} 审批模板主档 PO（T-WF-002）。
 *
 * <p>对应《02-数据模型与数据库设计.md》§3.2 与 V01 基线 DDL：模板按
 * {@code project_type}、经费区间、部门等受控条件路由（技术文档 03 §3.3）；
 * {@code code} 为业务唯一编码（uk_wf_template_code），{@code current_version_id}
 * 指向当前发布版本（发布服务在 T-WF-013 维护）。</p>
 *
 * <p>继承 {@link BaseEntity}：V01 该表具备 created_at / updated_at /
 * created_by / updated_by / version 全量审计列。</p>
 */
@TableName("wf_template")
public class WfTemplateEntity extends BaseEntity {

    /** 模板状态：草稿（ck_wf_template_status）。 */
    public static final String STATUS_DRAFT = "DRAFT";

    /** 模板状态：已发布。 */
    public static final String STATUS_PUBLISHED = "PUBLISHED";

    /** 模板状态：已停用。 */
    public static final String STATUS_RETIRED = "RETIRED";

    /** 模板业务编码（uk_wf_template_code）。 */
    @TableField("code")
    private String code;

    /** 模板名称。 */
    @TableField("name")
    private String name;

    /** 适用项目类型（如面上项目、青年项目）；为空表示不限。 */
    @TableField("project_type")
    private String projectType;

    /** 受控条件声明（jsonb 文本，字段白名单见技术文档 03 §3.3）。 */
    @TableField("condition_schema")
    private String conditionSchema;

    /** 模板状态：DRAFT / PUBLISHED / RETIRED。 */
    @TableField("status")
    private String status;

    /** 当前生效版本 ID（wf_template_version.id）；未发布时为空。 */
    @TableField("current_version_id")
    private String currentVersionId;

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

    public String getProjectType() {
        return projectType;
    }

    public void setProjectType(String projectType) {
        this.projectType = projectType;
    }

    public String getConditionSchema() {
        return conditionSchema;
    }

    public void setConditionSchema(String conditionSchema) {
        this.conditionSchema = conditionSchema;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCurrentVersionId() {
        return currentVersionId;
    }

    public void setCurrentVersionId(String currentVersionId) {
        this.currentVersionId = currentVersionId;
    }
}
