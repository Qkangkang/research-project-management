package cn.edu.rpm.workflow.application.dto;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * 审批模板发布命令（T-WF-013；技术文档 03 §3.3）。
 *
 * @param templateId    模板主档 ID
 * @param definition    受控流程定义 JSON（节点列表；首节点处理人必填）
 * @param conditionExpr 路由条件 JSON；为空按无条件处理
 * @param effectiveFrom 生效起始时刻
 * @param publishedBy   发布人用户 ID
 */
public record PublishTemplateCmd(
        UUID templateId,
        String definition,
        String conditionExpr,
        OffsetDateTime effectiveFrom,
        UUID publishedBy
) {

    /** 校验必填字段。 */
    public PublishTemplateCmd {
        Objects.requireNonNull(templateId, "templateId 不能为空");
        if (definition == null || definition.isBlank()) {
            throw new IllegalArgumentException("definition 不能为空");
        }
        Objects.requireNonNull(effectiveFrom, "effectiveFrom 不能为空");
        Objects.requireNonNull(publishedBy, "publishedBy 不能为空");
    }
}
