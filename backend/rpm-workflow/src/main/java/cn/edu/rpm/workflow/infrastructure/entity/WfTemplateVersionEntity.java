package cn.edu.rpm.workflow.infrastructure.entity;

import cn.edu.rpm.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * {@code wf_template_version} 审批模板版本 PO（T-WF-003）。
 *
 * <p>对应《02-数据模型与数据库设计.md》§3.2 与 V01 基线 DDL：版本一经发布不可变，
 * 已运行实例绑定旧 {@code template_version_id}，不随新版本变化（技术文档 03 §3.3）。</p>
 *
 * <p>生效判定：{@code status = PUBLISHED}、{@code effective_from <= at} 且
 * {@code effective_to} 为空或 {@code effective_to > at}；uk_wf_template_version_no
 * 保证同一模板版本号唯一（ck_wf_template_version_no 要求 version_no &gt;= 1）。</p>
 */
@TableName("wf_template_version")
public class WfTemplateVersionEntity extends BaseEntity {

    /** 版本状态：草稿。 */
    public static final String STATUS_DRAFT = "DRAFT";

    /** 版本状态：已发布。 */
    public static final String STATUS_PUBLISHED = "PUBLISHED";

    /** 版本状态：已停用。 */
    public static final String STATUS_RETIRED = "RETIRED";

    /** 所属模板 ID（wf_template.id，ON DELETE RESTRICT）。 */
    @TableField("template_id")
    private String templateId;

    /** 模板内版本号（从 1 递增，uk_wf_template_version_no）。 */
    @TableField("version_no")
    private Integer versionNo;

    /** 流程定义内容（jsonb 文本：节点、处理人规则、退回策略）。 */
    @TableField("definition")
    private String definition;

    /** 路由条件表达式（jsonb 文本：金额区间/部门等白名单条件）。 */
    @TableField("condition_expr")
    private String conditionExpr;

    /** 生效起始时刻（含）。 */
    @TableField("effective_from")
    private OffsetDateTime effectiveFrom;

    /** 生效结束时刻（不含）；为空表示持续有效。 */
    @TableField("effective_to")
    private OffsetDateTime effectiveTo;

    /** 版本状态：DRAFT / PUBLISHED / RETIRED。 */
    @TableField("status")
    private String status;

    /** 发布人用户 ID（iam_user.id）。 */
    @TableField("published_by")
    private String publishedBy;

    /** 发布时间。 */
    @TableField("published_at")
    private OffsetDateTime publishedAt;

    public String getTemplateId() {
        return templateId;
    }

    public void setTemplateId(String templateId) {
        this.templateId = templateId;
    }

    public Integer getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(Integer versionNo) {
        this.versionNo = versionNo;
    }

    public String getDefinition() {
        return definition;
    }

    public void setDefinition(String definition) {
        this.definition = definition;
    }

    public String getConditionExpr() {
        return conditionExpr;
    }

    public void setConditionExpr(String conditionExpr) {
        this.conditionExpr = conditionExpr;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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
