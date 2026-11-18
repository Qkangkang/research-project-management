package cn.edu.rpm.application.infrastructure.entity;

import cn.edu.rpm.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * {@code apply_form_template} 项目类型表单模板 PO（T-APP-002）。
 *
 * <p>对应《02-数据模型与数据库设计.md》§3.2 与 V01 基线 DDL。关键约束：</p>
 * <ul>
 *   <li>{@code uk_apply_form_type_version (project_type, version_no)}：同一项目类型的版本号唯一；</li>
 *   <li>{@code ck_apply_form_status}：DRAFT / PUBLISHED / RETIRED；</li>
 *   <li>{@code [effective_from, effective_to)} 生效窗口，任一端为空表示不限。</li>
 * </ul>
 *
 * <p>{@code form_schema} 为 JSON Schema 的 jsonb 文本，供申报表单校验
 * （ApplyFormTemplateResolver）解析；审计字段由 {@link BaseEntity} 承载。</p>
 */
@TableName("apply_form_template")
public class ApplyFormTemplateEntity extends BaseEntity {

    /** 模板状态：草稿（不可用于申报）。 */
    public static final String STATUS_DRAFT = "DRAFT";

    /** 模板状态：已发布（可作为当前有效模板）。 */
    public static final String STATUS_PUBLISHED = "PUBLISHED";

    /** 模板状态：已退役（仅保留历史版本）。 */
    public static final String STATUS_RETIRED = "RETIRED";

    /** 项目类型（如 YEAR_PLAN、EXTERNAL_CONTRACT）。 */
    @TableField("project_type")
    private String projectType;

    /** 模板版本号，从 1 递增（ck_apply_form_version）。 */
    @TableField("version_no")
    private Integer versionNo;

    /** 表单 JSON Schema（jsonb 文本）。 */
    @TableField("form_schema")
    private String formSchema;

    /** 模板状态：DRAFT / PUBLISHED / RETIRED（ck_apply_form_status）。 */
    @TableField("status")
    private String status;

    /** 生效起始时刻；为空表示不早于发布即生效。 */
    @TableField("effective_from")
    private OffsetDateTime effectiveFrom;

    /** 生效截止时刻（开区间上界）；为空表示长期有效。 */
    @TableField("effective_to")
    private OffsetDateTime effectiveTo;

    /** 发布人用户 ID（iam_user.id）；草稿为空。 */
    @TableField("published_by")
    private String publishedBy;

    /** 发布时刻；草稿为空。 */
    @TableField("published_at")
    private OffsetDateTime publishedAt;

    public String getProjectType() {
        return projectType;
    }

    public void setProjectType(String projectType) {
        this.projectType = projectType;
    }

    public Integer getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(Integer versionNo) {
        this.versionNo = versionNo;
    }

    public String getFormSchema() {
        return formSchema;
    }

    public void setFormSchema(String formSchema) {
        this.formSchema = formSchema;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public OffsetDateTime getEffectiveFrom() {
        return effectiveFrom;
    }

    public void setEffectiveFrom(OffsetDateTime effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    public OffsetDateTime getEffectiveTo() {
        return effectiveTo;
    }

    public void setEffectiveTo(OffsetDateTime effectiveTo) {
        this.effectiveTo = effectiveTo;
    }

    public String getPublishedBy() {
        return publishedBy;
    }

    public void setPublishedBy(String publishedBy) {
        this.publishedBy = publishedBy;
    }

    public OffsetDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(OffsetDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }
}
