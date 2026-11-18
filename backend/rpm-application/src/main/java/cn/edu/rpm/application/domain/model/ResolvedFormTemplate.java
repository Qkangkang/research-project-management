package cn.edu.rpm.application.domain.model;

import java.util.Map;
import java.util.Set;

/**
 * 已解析的项目类型表单模板（T-APP-013）。
 *
 * <p>{@code apply_form_template.form_schema} 解析后的只读视图：提交链路据此执行
 * 必填、类型、长度、金额、日期校验，并核对必填附件用途集合。</p>
 *
 * @param templateId          模板 ID
 * @param versionNo           模板版本号
 * @param requiredFields      必填字段集合（JSON Schema {@code required}）
 * @param fields              字段规则（JSON Schema {@code properties}）
 * @param requiredAttachments 必填附件用途集合（自定义关键字 {@code requiredAttachments}）
 */
public record ResolvedFormTemplate(
        String templateId,
        int versionNo,
        Set<String> requiredFields,
        Map<String, FormFieldRule> fields,
        Set<String> requiredAttachments
) {

    /** 规整集合与映射为不可变副本。 */
    public ResolvedFormTemplate {
        requiredFields = requiredFields == null ? Set.of() : Set.copyOf(requiredFields);
        fields = fields == null ? Map.of() : Map.copyOf(fields);
        requiredAttachments = requiredAttachments == null ? Set.of() : Set.copyOf(requiredAttachments);
    }
}
