package cn.edu.rpm.workflow.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * 已发布模板候选（T-WF-012；技术文档 03 §3.3）。
 *
 * <p>只包含路由所需字段，避免领域层依赖 {@code wf_template} PO。
 * {@code projectType} 为空表示不限项目类型。</p>
 *
 * @param templateId  模板 ID
 * @param projectType 适用项目类型；为空表示不限
 */
public record WorkflowTemplateCandidate(
        UUID templateId,
        String projectType
) {

    /** 校验模板 ID。 */
    public WorkflowTemplateCandidate {
        Objects.requireNonNull(templateId, "templateId 不能为空");
    }
}
