package cn.edu.rpm.workflow.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * 指定时点生效的模板版本候选（T-WF-012；技术文档 03 §3.3）。
 *
 * @param versionId     模板版本 ID
 * @param conditionExpr 路由条件表达式（jsonb 文本）；空串按无附加条件处理
 */
public record WorkflowTemplateVersionCandidate(
        UUID versionId,
        String conditionExpr
) {

    /** 校验版本 ID。 */
    public WorkflowTemplateVersionCandidate {
        Objects.requireNonNull(versionId, "versionId 不能为空");
    }
}
