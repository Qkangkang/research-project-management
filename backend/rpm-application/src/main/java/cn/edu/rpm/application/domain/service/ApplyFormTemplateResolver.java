package cn.edu.rpm.application.domain.service;

import cn.edu.rpm.application.domain.model.FormFieldRule;
import cn.edu.rpm.application.domain.model.FormValidationError;
import cn.edu.rpm.application.domain.model.ResolvedFormTemplate;
import cn.edu.rpm.application.infrastructure.entity.ApplyFormTemplateEntity;
import cn.edu.rpm.application.infrastructure.repository.ApplyFormTemplateRepository;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 项目类型表单模板解析与校验（T-APP-013；技术文档 04 §3.2）。
 *
 * <p>按 {@code project_type} 读取当前有效模板（{@code apply_form_template}），把
 * {@code form_schema}（JSON Schema 子集）解析为 {@link ResolvedFormTemplate}，
 * 并对提交的 {@code form_data} 执行必填、类型、长度、金额区间、日期格式与
 * 必填附件用途校验。校验错误以 {@link FormValidationError} 列表返回，由应用服务
 * 汇总抛出 {@code RPM-APPLY-001}。</p>
 *
 * <p>支持的 JSON Schema 关键字见 {@link FormFieldRule}；模板缺失按
 * {@code RPM-APPLY-001} 处理，模板 JSON 非法属于配置缺陷，按 {@code RPM-SYS-002}
 * 快速失败（禁止以空规则放行提交）。</p>
 */
public class ApplyFormTemplateResolver {

    private static final String ERROR_TYPE_MISMATCH = "类型不匹配";

    private static final String ERROR_REQUIRED_MISSING = "必填字段缺失";

    private static final Set<String> SUPPORTED_TYPES =
            Set.of("string", "number", "integer", "boolean", "array", "object");

    private final ApplyFormTemplateRepository templateRepository;

    private final ObjectMapper objectMapper;

    /**
     * 构造解析器。
     *
     * @param templateRepository 表单模板仓储
     * @param objectMapper       JSON 解析器
     */
    public ApplyFormTemplateResolver(ApplyFormTemplateRepository templateRepository,
                                     ObjectMapper objectMapper) {
        this.templateRepository = Objects.requireNonNull(templateRepository,
                "templateRepository 不能为空");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper 不能为空");
    }

    /**
     * 读取并解析项目类型当前有效模板。
     *
     * @param projectType 项目类型
     * @return 解析后的只读模板视图
     * @throws BizException 无有效模板 {@code RPM-APPLY-001}；模板 schema 非法 {@code RPM-SYS-002}
     */
    public ResolvedFormTemplate resolve(String projectType) {
        if (projectType == null || projectType.isBlank()) {
            throw new BizException(ErrorCode.APPLY_001, "项目类型不能为空");
        }
        ApplyFormTemplateEntity entity = templateRepository.findEffectiveByProjectType(projectType)
                .orElseThrow(() -> new BizException(ErrorCode.APPLY_001,
                        "项目类型无有效表单模板: " + projectType));
        return parse(entity);
    }

    /**
     * 按模板规则校验表单数据。
     *
     * <p>返回全部错误而不是遇到第一个错误即中断，便于前端一次性展示；
     * 空表单且无必填字段时通过。</p>
     *
     * @param template 已解析模板
     * @param formData 表单数据
     * @return 校验错误列表；全部通过返回空列表
     */
    public List<FormValidationError> validate(ResolvedFormTemplate template, Map<String, Object> formData) {
        Objects.requireNonNull(template, "template 不能为空");
        if (formData == null) {
            return List.of(new FormValidationError("_form", "表单数据不能为空"));
        }

        List<FormValidationError> errors = new ArrayList<>();
        Set<String> fieldNames = new LinkedHashSet<>(template.requiredFields());
        fieldNames.addAll(template.fields().keySet());
        for (String field : fieldNames) {
            Object value = formData.get(field);
            if (value == null) {
                if (template.requiredFields().contains(field)) {
                    errors.add(new FormValidationError(field, ERROR_REQUIRED_MISSING));
                }
                continue;
            }
            FormFieldRule rule = template.fields().get(field);
            if (rule != null) {
                validateValue(field, value, rule, errors);
            }
        }
        return List.copyOf(errors);
    }

    private ResolvedFormTemplate parse(ApplyFormTemplateEntity entity) {
        JsonNode root;
        try {
            root = objectMapper.readTree(entity.getFormSchema());
        } catch (Exception ex) {
            throw new BizException(ErrorCode.SYS_002, "表单模板 schema 不是合法 JSON: " + entity.getId());
        }
        if (root == null || !root.isObject()) {
            throw new BizException(ErrorCode.SYS_002, "表单模板 schema 必须为 JSON 对象: " + entity.getId());
        }

        Set<String> requiredFields = readTextArray(root.get("required"), "required", entity.getId());
        Set<String> requiredAttachments = readTextArray(root.get("requiredAttachments"),
                "requiredAttachments", entity.getId());
        Map<String, FormFieldRule> fields = new LinkedHashMap<>();
        JsonNode properties = root.get("properties");
        if (properties != null) {
            if (!properties.isObject()) {
                throw new BizException(ErrorCode.SYS_002,
                        "表单模板 properties 必须为 JSON 对象: " + entity.getId());
            }
            properties.fields().forEachRemaining(entry ->
                    fields.put(entry.getKey(), parseRule(entry.getKey(), entry.getValue(), entity.getId())));
        }
        return new ResolvedFormTemplate(entity.getId(), entity.getVersionNo() == null ? 0 : entity.getVersionNo(),
                requiredFields, fields, requiredAttachments);
    }

    private FormFieldRule parseRule(String field, JsonNode node, String templateId) {
        if (node == null || !node.isObject()) {
            throw new BizException(ErrorCode.SYS_002,
                    "表单模板字段定义必须为对象: " + templateId + "#" + field);
        }
        String type = textOrNull(node.get("type"));
        if (type != null && !SUPPORTED_TYPES.contains(type)) {
            throw new BizException(ErrorCode.SYS_002,
                    "表单模板字段类型不支持: " + templateId + "#" + field + " type=" + type);
        }
        return new FormFieldRule(type,
                intOrNull(node.get("minLength")),
                intOrNull(node.get("maxLength")),
                intOrNull(node.get("minItems")),
                intOrNull(node.get("maxItems")),
                decimalOrNull(node.get("minimum")),
                decimalOrNull(node.get("maximum")),
                textOrNull(node.get("format")));
    }

    private static Set<String> readTextArray(JsonNode node, String keyword, String templateId) {
        if (node == null || node.isNull()) {
            return Set.of();
        }
        if (!node.isArray()) {
            throw new BizException(ErrorCode.SYS_002,
                    "表单模板 " + keyword + " 必须为字符串数组: " + templateId);
        }
        Set<String> values = new LinkedHashSet<>();
        for (JsonNode item : node) {
            if (item == null || !item.isTextual() || item.asText().isBlank()) {
                throw new BizException(ErrorCode.SYS_002,
                        "表单模板 " + keyword + " 含非法元素: " + templateId);
            }
            values.add(item.asText());
        }
        return values;
    }

    private static void validateValue(String field, Object value, FormFieldRule rule,
                                      List<FormValidationError> errors) {
        String type = rule.type();
        if ("string".equals(type)) {
            if (!(value instanceof String text)) {
                errors.add(new FormValidationError(field, ERROR_TYPE_MISMATCH + "：应为字符串"));
                return;
            }
            int length = text.codePointCount(0, text.length());
            if (rule.minLength() != null && length < rule.minLength()) {
                errors.add(new FormValidationError(field, "长度不能小于 " + rule.minLength()));
            }
            if (rule.maxLength() != null && length > rule.maxLength()) {
                errors.add(new FormValidationError(field, "长度不能超过 " + rule.maxLength()));
            }
            validateFormat(field, text, rule.format(), errors);
            return;
        }
        if ("number".equals(type) || "integer".equals(type)) {
            if (!(value instanceof Number number)) {
                errors.add(new FormValidationError(field, ERROR_TYPE_MISMATCH + "：应为数值"));
                return;
            }
            BigDecimal decimal;
            try {
                decimal = toBigDecimal(number);
            } catch (NumberFormatException ex) {
                errors.add(new FormValidationError(field, "金额/数值格式非法"));
                return;
            }
            if ("integer".equals(type) && decimal.stripTrailingZeros().scale() > 0) {
                errors.add(new FormValidationError(field, "应为整数"));
                return;
            }
            if (rule.minimum() != null && decimal.compareTo(rule.minimum()) < 0) {
                errors.add(new FormValidationError(field, "数值不能小于 " + rule.minimum().toPlainString()));
            }
            if (rule.maximum() != null && decimal.compareTo(rule.maximum()) > 0) {
                errors.add(new FormValidationError(field, "数值不能大于 " + rule.maximum().toPlainString()));
            }
            return;
        }
        if ("boolean".equals(type)) {
            if (!(value instanceof Boolean)) {
                errors.add(new FormValidationError(field, ERROR_TYPE_MISMATCH + "：应为布尔值"));
            }
            return;
        }
        if ("array".equals(type)) {
            if (!(value instanceof List<?> list)) {
                errors.add(new FormValidationError(field, ERROR_TYPE_MISMATCH + "：应为数组"));
                return;
            }
            if (rule.minItems() != null && list.size() < rule.minItems()) {
                errors.add(new FormValidationError(field, "元素数量不能少于 " + rule.minItems()));
            }
            if (rule.maxItems() != null && list.size() > rule.maxItems()) {
                errors.add(new FormValidationError(field, "元素数量不能超过 " + rule.maxItems()));
            }
            return;
        }
        if ("object".equals(type) && !(value instanceof Map<?, ?>)) {
            errors.add(new FormValidationError(field, ERROR_TYPE_MISMATCH + "：应为对象"));
        }
    }

    private static void validateFormat(String field, String value, String format,
                                       List<FormValidationError> errors) {
        if (format == null || value.isBlank()) {
            return;
        }
        if ("date".equals(format)) {
            try {
                LocalDate.parse(value);
            } catch (DateTimeParseException ex) {
                errors.add(new FormValidationError(field, "日期格式非法（应为 yyyy-MM-dd）"));
            }
            return;
        }
        if ("date-time".equals(format)) {
            try {
                OffsetDateTime.parse(value);
            } catch (DateTimeParseException ex) {
                errors.add(new FormValidationError(field, "日期时间格式非法（应为 ISO-8601 含时区）"));
            }
        }
    }

    private static BigDecimal toBigDecimal(Number number) {
        if (number instanceof BigDecimal decimal) {
            return decimal;
        }
        if (number instanceof Double || number instanceof Float) {
            double doubleValue = number.doubleValue();
            if (Double.isNaN(doubleValue) || Double.isInfinite(doubleValue)) {
                throw new NumberFormatException("非有限数值");
            }
            return new BigDecimal(number.toString());
        }
        return BigDecimal.valueOf(number.longValue());
    }

    private static String textOrNull(JsonNode node) {
        return node == null || !node.isTextual() ? null : node.asText();
    }

    private static Integer intOrNull(JsonNode node) {
        return node == null || !node.isNumber() ? null : node.intValue();
    }

    private static BigDecimal decimalOrNull(JsonNode node) {
        return node == null || !node.isNumber() ? null : node.decimalValue();
    }
}
