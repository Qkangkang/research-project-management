package cn.edu.rpm.workflow.infrastructure.adapter;

import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.common.util.UuidUtils;
import cn.edu.rpm.workflow.domain.dto.TemplateRoutingContext;
import cn.edu.rpm.workflow.domain.model.BudgetConditionRange;
import cn.edu.rpm.workflow.domain.port.TemplateConditionMatcher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 基于 Jackson 的模板条件匹配器（T-WF-012；技术文档 03 §3.3 白名单）。
 *
 * <p>严格模式：字段或运算符不在白名单、JSON 结构非法、数值/布尔类型不符时，
 * 一律抛 {@code RPM-SYS-001}，禁止"忽略未知条件"导致路由命中错误模板。</p>
 */
@Repository
public class JacksonTemplateConditionMatcher implements TemplateConditionMatcher {

    private static final String FIELD_PROJECT_TYPE = "project_type";
    private static final String FIELD_DEPT_ID = "dept_id";
    private static final String FIELD_BUDGET_TOTAL = "budget_total";
    private static final String FIELD_EXPENSE_AMOUNT = "expense_amount";
    private static final String FIELD_DURATION_MONTHS = "duration_months";
    private static final String FIELD_IS_EXTERNAL = "is_external";
    private static final String FIELD_APPLICANT_TITLE = "applicant_title";

    private final ObjectMapper objectMapper;

    /**
     * 构造匹配器。
     *
     * @param objectMapper JSON 解析器
     */
    public JacksonTemplateConditionMatcher(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper 不能为空");
    }

    @Override
    public boolean matches(String conditionExpr, TemplateRoutingContext context) {
        Objects.requireNonNull(context, "context 不能为空");
        for (JsonNode condition : readConditions(conditionExpr)) {
            if (!evaluate(condition, context)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void validate(String conditionExpr) {
        for (JsonNode condition : readConditions(conditionExpr)) {
            validateCondition(condition);
        }
    }

    private void validateCondition(JsonNode condition) {
        String field = text(condition, "field");
        String operator = text(condition, "operator");
        JsonNode value = condition.get("value");
        if (field == null || operator == null || value == null || value.isNull()) {
            throw invalid("条件缺少 field/operator/value");
        }
        switch (field) {
            case FIELD_PROJECT_TYPE, FIELD_APPLICANT_TITLE -> validateTextOperator(field, operator, value);
            case FIELD_DEPT_ID -> {
                validateTextOperator(field, operator, value);
                if ("EQ".equals(operator)) {
                    parseUuid(requireText(value, field), field);
                } else {
                    textValues(value, field).forEach(text -> parseUuid(text, field));
                }
            }
            case FIELD_BUDGET_TOTAL, FIELD_EXPENSE_AMOUNT -> validateNumericOperator(field, operator, value);
            case FIELD_DURATION_MONTHS -> {
                validateNumericOperator(field, operator, value);
                if ("BETWEEN".equals(operator)) {
                    requireIntegral(value.get(0), field);
                    requireIntegral(value.get(1), field);
                } else {
                    requireIntegral(value, field);
                }
            }
            case FIELD_IS_EXTERNAL -> {
                if (!"EQ".equals(operator)) {
                    throw invalid("字段 " + field + " 不支持运算符 " + operator);
                }
                if (!value.isBoolean()) {
                    throw invalid("字段 " + field + " 需要布尔值");
                }
            }
            default -> throw invalid("条件字段不在白名单: " + field);
        }
    }

    private void validateTextOperator(String field, String operator, JsonNode value) {
        switch (operator) {
            case "EQ" -> requireText(value, field);
            case "IN" -> textValues(value, field);
            default -> throw invalid("字段 " + field + " 不支持运算符 " + operator);
        }
    }

    private void validateNumericOperator(String field, String operator, JsonNode value) {
        if ("BETWEEN".equals(operator)) {
            if (!value.isArray() || value.size() != 2) {
                throw invalid("BETWEEN 需要两个数值: " + field);
            }
            BigDecimal lower = decimal(value.get(0), field);
            BigDecimal upper = decimal(value.get(1), field);
            if (lower.compareTo(upper) > 0) {
                throw invalid("BETWEEN 下界大于上界: " + field);
            }
            return;
        }
        if (!"GT".equals(operator) && !"GTE".equals(operator)
                && !"LT".equals(operator) && !"LTE".equals(operator)) {
            throw invalid("字段 " + field + " 不支持运算符 " + operator);
        }
        decimal(value, field);
    }

    private void requireIntegral(JsonNode node, String field) {
        BigDecimal number = decimal(node, field);
        if (number.stripTrailingZeros().scale() > 0) {
            throw invalid("字段 " + field + " 需要整数");
        }
    }

    @Override
    public Optional<BudgetConditionRange> findBudgetRange(String conditionExpr) {
        for (JsonNode condition : readConditions(conditionExpr)) {
            if (FIELD_BUDGET_TOTAL.equals(text(condition, "field"))) {
                return Optional.of(toBudgetRange(condition));
            }
        }
        return Optional.empty();
    }

    private boolean evaluate(JsonNode condition, TemplateRoutingContext context) {
        String field = text(condition, "field");
        String operator = text(condition, "operator");
        JsonNode value = condition.get("value");
        if (field == null || operator == null || value == null || value.isNull()) {
            throw invalid("条件缺少 field/operator/value");
        }
        return switch (field) {
            case FIELD_PROJECT_TYPE -> matchText(context.projectType(), operator, value, field);
            case FIELD_APPLICANT_TITLE -> matchText(context.applicantTitle(), operator, value, field);
            case FIELD_DEPT_ID -> matchUuid(context.deptId(), operator, value, field);
            case FIELD_BUDGET_TOTAL -> matchDecimal(context.budgetTotal(), operator, value, field);
            case FIELD_EXPENSE_AMOUNT -> matchDecimal(context.expenseAmount(), operator, value, field);
            case FIELD_DURATION_MONTHS -> matchInteger(context.durationMonths(), operator, value, field);
            case FIELD_IS_EXTERNAL -> matchBoolean(context.external(), operator, value, field);
            default -> throw invalid("条件字段不在白名单: " + field);
        };
    }

    private boolean matchText(String actual, String operator, JsonNode value, String field) {
        if (actual == null) {
            return false;
        }
        return switch (operator) {
            case "EQ" -> actual.equals(requireText(value, field));
            case "IN" -> textValues(value, field).contains(actual);
            default -> throw invalid("字段 " + field + " 不支持运算符 " + operator);
        };
    }

    private boolean matchUuid(UUID actual, String operator, JsonNode value, String field) {
        if (actual == null) {
            return false;
        }
        return switch (operator) {
            case "EQ" -> actual.equals(parseUuid(requireText(value, field), field));
            case "IN" -> textValues(value, field).stream()
                    .map(text -> parseUuid(text, field))
                    .anyMatch(actual::equals);
            default -> throw invalid("字段 " + field + " 不支持运算符 " + operator);
        };
    }

    private boolean matchDecimal(BigDecimal actual, String operator, JsonNode value, String field) {
        if (actual == null) {
            return false;
        }
        return compareDecimal(actual, operator, value, field);
    }

    private boolean matchInteger(Integer actual, String operator, JsonNode value, String field) {
        if (actual == null) {
            return false;
        }
        return compareDecimal(BigDecimal.valueOf(actual), operator, value, field);
    }

    private boolean compareDecimal(BigDecimal actual, String operator, JsonNode value, String field) {
        if ("BETWEEN".equals(operator)) {
            if (!value.isArray() || value.size() != 2) {
                throw invalid("BETWEEN 需要两个数值: " + field);
            }
            BigDecimal lower = decimal(value.get(0), field);
            BigDecimal upper = decimal(value.get(1), field);
            if (lower.compareTo(upper) > 0) {
                throw invalid("BETWEEN 下界大于上界: " + field);
            }
            return actual.compareTo(lower) >= 0 && actual.compareTo(upper) <= 0;
        }
        BigDecimal expected = decimal(value, field);
        int compared = actual.compareTo(expected);
        return switch (operator) {
            case "GT" -> compared > 0;
            case "GTE" -> compared >= 0;
            case "LT" -> compared < 0;
            case "LTE" -> compared <= 0;
            default -> throw invalid("字段 " + field + " 不支持运算符 " + operator);
        };
    }

    private boolean matchBoolean(Boolean actual, String operator, JsonNode value, String field) {
        if (actual == null) {
            return false;
        }
        if (!"EQ".equals(operator)) {
            throw invalid("字段 " + field + " 不支持运算符 " + operator);
        }
        if (!value.isBoolean()) {
            throw invalid("字段 " + field + " 需要布尔值");
        }
        return actual == value.booleanValue();
    }

    private BudgetConditionRange toBudgetRange(JsonNode condition) {
        String operator = text(condition, "operator");
        JsonNode value = condition.get("value");
        if (operator == null || value == null || value.isNull()) {
            throw invalid("金额条件缺少 operator/value");
        }
        return switch (operator) {
            case "BETWEEN" -> {
                if (!value.isArray() || value.size() != 2) {
                    throw invalid("BETWEEN 需要两个数值");
                }
                BigDecimal lower = decimal(value.get(0), FIELD_BUDGET_TOTAL);
                BigDecimal upper = decimal(value.get(1), FIELD_BUDGET_TOTAL);
                if (lower.compareTo(upper) > 0) {
                    throw invalid("BETWEEN 下界大于上界");
                }
                yield new BudgetConditionRange(lower, upper, true, true);
            }
            case "GTE" -> new BudgetConditionRange(decimal(value, FIELD_BUDGET_TOTAL), null, true, false);
            case "GT" -> new BudgetConditionRange(decimal(value, FIELD_BUDGET_TOTAL), null, false, false);
            case "LTE" -> new BudgetConditionRange(null, decimal(value, FIELD_BUDGET_TOTAL), false, true);
            case "LT" -> new BudgetConditionRange(null, decimal(value, FIELD_BUDGET_TOTAL), false, false);
            default -> throw invalid("金额条件不支持运算符 " + operator);
        };
    }

    private List<JsonNode> readConditions(String conditionExpr) {
        if (conditionExpr == null || conditionExpr.isBlank()) {
            return List.of();
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(conditionExpr);
        } catch (Exception ex) {
            throw invalid("模板条件不是合法 JSON");
        }
        if (root == null || root.isNull() || (root.isObject() && root.isEmpty())) {
            return List.of();
        }
        if (!root.isObject()) {
            throw invalid("模板条件根节点必须是对象");
        }
        JsonNode conditions = root.get("conditions");
        if (conditions == null || conditions.isNull()) {
            return List.of();
        }
        if (!conditions.isArray()) {
            throw invalid("conditions 必须是数组");
        }
        List<JsonNode> result = new ArrayList<>(conditions.size());
        conditions.forEach(result::add);
        return result;
    }

    private List<String> textValues(JsonNode value, String field) {
        if (!value.isArray()) {
            throw invalid("运算符 IN 需要文本数组: " + field);
        }
        List<String> values = new ArrayList<>(value.size());
        for (JsonNode item : value) {
            if (!item.isTextual()) {
                throw invalid("运算符 IN 需要文本数组: " + field);
            }
            values.add(item.asText());
        }
        return values;
    }

    private String text(JsonNode condition, String field) {
        JsonNode node = condition.get(field);
        return node == null || node.isNull() ? null : node.asText();
    }

    private String requireText(JsonNode value, String field) {
        if (!value.isTextual()) {
            throw invalid("字段 " + field + " 需要文本值");
        }
        return value.asText();
    }

    private UUID parseUuid(String text, String field) {
        try {
            return UuidUtils.parse(text);
        } catch (IllegalArgumentException ex) {
            throw invalid("字段 " + field + " 的 UUID 非法: " + text);
        }
    }

    private BigDecimal decimal(JsonNode node, String field) {
        if (node == null || !node.isNumber()) {
            throw invalid("字段 " + field + " 需要数值");
        }
        return node.decimalValue();
    }

    private BizException invalid(String message) {
        return new BizException(ErrorCode.SYS_001, "模板条件不可解析: " + message);
    }
}
