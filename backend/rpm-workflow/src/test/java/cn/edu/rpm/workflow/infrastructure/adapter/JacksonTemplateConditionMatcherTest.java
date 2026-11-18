package cn.edu.rpm.workflow.infrastructure.adapter;

import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.workflow.domain.dto.TemplateRoutingContext;
import cn.edu.rpm.workflow.domain.model.BudgetConditionRange;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link JacksonTemplateConditionMatcher} 纯单元测试（T-WF-012 先行测试）。
 *
 * <p>覆盖技术文档 03 §3.3 白名单字段/运算符、严格模式（未知字段拒绝）、
 * 金额区间提取与发布前结构校验。</p>
 */
class JacksonTemplateConditionMatcherTest {

    private static final UUID DEPT_ID = UUID.fromString("0b7f5b7e-2a1c-4c2e-8d4f-9a0b1c2d3e4f");

    private JacksonTemplateConditionMatcher matcher;

    @BeforeEach
    void setUp() {
        matcher = new JacksonTemplateConditionMatcher(new ObjectMapper());
    }

    private TemplateRoutingContext context() {
        return new TemplateRoutingContext("YEAR_PLAN", DEPT_ID, new BigDecimal("1200000.00"),
                new BigDecimal("8000.00"), 24, false, "PROFESSOR");
    }

    @Test
    void emptyConditionMatchesEverything() {
        assertThat(matcher.matches(null, context())).isTrue();
        assertThat(matcher.matches("", context())).isTrue();
        assertThat(matcher.matches("{}", context())).isTrue();
    }

    @Test
    void matchesProjectTypeBudgetDepartmentAndTitle() {
        String expr = """
                {"conditions":[
                  {"field":"project_type","operator":"EQ","value":"YEAR_PLAN"},
                  {"field":"budget_total","operator":"BETWEEN","value":[1000000,2000000]},
                  {"field":"dept_id","operator":"IN","value":["0b7f5b7e-2a1c-4c2e-8d4f-9a0b1c2d3e4f"]},
                  {"field":"applicant_title","operator":"EQ","value":"PROFESSOR"},
                  {"field":"duration_months","operator":"GTE","value":12},
                  {"field":"is_external","operator":"EQ","value":false}
                ]}
                """;
        assertThat(matcher.matches(expr, context())).isTrue();
    }

    @Test
    void rejectsWhenAnyConditionFails() {
        String expr = "{\"conditions\":[{\"field\":\"budget_total\",\"operator\":\"LT\",\"value\":1000000}]}";
        assertThat(matcher.matches(expr, context())).isFalse();
    }

    @Test
    void rejectsUnknownFieldInStrictMode() {
        String expr = "{\"conditions\":[{\"field\":\"secret_field\",\"operator\":\"EQ\",\"value\":\"x\"}]}";
        assertThatThrownBy(() -> matcher.matches(expr, context()))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.SYS_001));
    }

    @Test
    void rejectsMalformedJson() {
        assertThatThrownBy(() -> matcher.matches("{not-json", context()))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.SYS_001));
    }

    @Test
    void extractsBudgetRangeFromBetween() {
        Optional<BudgetConditionRange> range = matcher.findBudgetRange(
                "{\"conditions\":[{\"field\":\"budget_total\",\"operator\":\"BETWEEN\","
                        + "\"value\":[1000000,2000000]}]}");
        assertThat(range).isPresent();
        assertThat(range.get().lower()).isEqualByComparingTo("1000000");
        assertThat(range.get().upper()).isEqualByComparingTo("2000000");
        assertThat(range.get().lowerInclusive()).isTrue();
        assertThat(range.get().upperInclusive()).isTrue();
    }

    @Test
    void extractsLowerBoundedRangeFromGte() {
        Optional<BudgetConditionRange> range = matcher.findBudgetRange(
                "{\"conditions\":[{\"field\":\"budget_total\",\"operator\":\"GTE\",\"value\":1000000}]}");
        assertThat(range).isPresent();
        assertThat(range.get().lowerInclusive()).isTrue();
        assertThat(range.get().upper()).isNull();
    }

    @Test
    void findBudgetRangeReturnsEmptyWithoutBudgetCondition() {
        assertThat(matcher.findBudgetRange(
                "{\"conditions\":[{\"field\":\"project_type\",\"operator\":\"EQ\",\"value\":\"A\"}]}"))
                .isEmpty();
    }

    @Test
    void validateAcceptsWhitelistedCondition() {
        matcher.validate("{\"conditions\":[{\"field\":\"duration_months\",\"operator\":\"BETWEEN\","
                + "\"value\":[6,36]}]}");
        matcher.validate("{\"conditions\":[{\"field\":\"dept_id\",\"operator\":\"EQ\","
                + "\"value\":\"0b7f5b7e-2a1c-4c2e-8d4f-9a0b1c2d3e4f\"}]}");
    }

    @Test
    void validateRejectsUnknownFieldAndBadValueType() {
        assertThatThrownBy(() -> matcher.validate(
                "{\"conditions\":[{\"field\":\"unknown\",\"operator\":\"EQ\",\"value\":1}]}"))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> matcher.validate(
                "{\"conditions\":[{\"field\":\"budget_total\",\"operator\":\"EQ\",\"value\":1}]}"))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> matcher.validate(
                "{\"conditions\":[{\"field\":\"duration_months\",\"operator\":\"EQ\",\"value\":1.5}]}"))
                .isInstanceOf(BizException.class);
    }
}
