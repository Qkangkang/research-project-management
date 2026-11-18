package cn.edu.rpm.application.domain.service;

import cn.edu.rpm.application.domain.model.FormValidationError;
import cn.edu.rpm.application.domain.model.ResolvedFormTemplate;
import cn.edu.rpm.application.infrastructure.entity.ApplyFormTemplateEntity;
import cn.edu.rpm.application.infrastructure.repository.ApplyFormTemplateRepository;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * {@link ApplyFormTemplateResolver} 纯单元测试（T-APP-013）。
 *
 * <p>覆盖 TC-APP-006 所依赖的必填字段与必填附件解析，并验证类型、长度、
 * 金额区间、日期格式校验以及模板缺失/非法 schema 的失败语义。</p>
 */
@ExtendWith(MockitoExtension.class)
class ApplyFormTemplateResolverTest {

    private static final String SCHEMA = """
            {
              "type": "object",
              "required": ["title", "budget", "startDate"],
              "properties": {
                "title": {"type": "string", "minLength": 1, "maxLength": 512},
                "budget": {"type": "number", "minimum": 0, "maximum": 10000000},
                "startDate": {"type": "string", "format": "date"},
                "members": {"type": "array", "minItems": 1, "maxItems": 20},
                "ethicsApproved": {"type": "boolean"}
              },
              "requiredAttachments": ["ETHICS_APPROVAL"]
            }
            """;

    @Mock
    private ApplyFormTemplateRepository templateRepository;

    private ApplyFormTemplateResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new ApplyFormTemplateResolver(templateRepository, new ObjectMapper());
    }

    @Test
    void resolvesRequiredFieldsAndRequiredAttachments() {
        when(templateRepository.findEffectiveByProjectType("YEAR_PLAN"))
                .thenReturn(Optional.of(template(SCHEMA)));

        ResolvedFormTemplate template = resolver.resolve("YEAR_PLAN");

        assertThat(template.templateId()).isEqualTo("eeeeeeeeffffffff0000000011111111");
        assertThat(template.versionNo()).isEqualTo(3);
        assertThat(template.requiredFields()).containsExactlyInAnyOrder("title", "budget", "startDate");
        assertThat(template.requiredAttachments()).containsExactly("ETHICS_APPROVAL");
        assertThat(template.fields().get("title").maxLength()).isEqualTo(512);
        assertThat(template.fields().get("budget").minimum()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(template.fields().get("startDate").format()).isEqualTo("date");
        assertThat(template.fields().get("members").minItems()).isEqualTo(1);
    }

    @Test
    void requiredFieldMissingProducesError() {
        ResolvedFormTemplate template = resolvedTemplate();

        List<FormValidationError> errors = resolver.validate(template, Map.of(
                "budget", new BigDecimal("1000.00"),
                "startDate", LocalDate.now().toString()));

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).field()).isEqualTo("title");
        assertThat(errors.get(0).message()).contains("必填");
    }

    @Test
    void nonNumericAmountProducesError() {
        ResolvedFormTemplate template = resolvedTemplate();

        List<FormValidationError> errors = resolver.validate(template, Map.of(
                "title", "智能感知研究",
                "budget", "abc",
                "startDate", "2026-01-01"));

        assertThat(errors).extracting(FormValidationError::field).contains("budget");
    }

    @Test
    void amountOutOfRangeProducesError() {
        ResolvedFormTemplate template = resolvedTemplate();

        List<FormValidationError> errors = resolver.validate(template, Map.of(
                "title", "智能感知研究",
                "budget", new BigDecimal("-1"),
                "startDate", "2026-01-01"));

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).field()).isEqualTo("budget");
        assertThat(errors.get(0).message()).contains("不能小于");
    }

    @Test
    void invalidDateProducesError() {
        ResolvedFormTemplate template = resolvedTemplate();

        List<FormValidationError> errors = resolver.validate(template, Map.of(
                "title", "智能感知研究",
                "budget", new BigDecimal("1000.00"),
                "startDate", "2026/01/01"));

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).field()).isEqualTo("startDate");
        assertThat(errors.get(0).message()).contains("日期格式");
    }

    @Test
    void validFormPasses() {
        ResolvedFormTemplate template = resolvedTemplate();

        List<FormValidationError> errors = resolver.validate(template, Map.of(
                "title", "面向复杂工况的智能感知方法研究",
                "budget", new BigDecimal("100000.00"),
                "startDate", "2026-03-01",
                "members", List.of("张三"),
                "ethicsApproved", true));

        assertThat(errors).isEmpty();
    }

    @Test
    void missingTemplateFailsWithApply001() {
        when(templateRepository.findEffectiveByProjectType("UNKNOWN")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.resolve("UNKNOWN"))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.APPLY_001));
    }

    @Test
    void malformedSchemaFailsFastWithSys002() {
        when(templateRepository.findEffectiveByProjectType("YEAR_PLAN"))
                .thenReturn(Optional.of(template("{not-json")));

        assertThatThrownBy(() -> resolver.resolve("YEAR_PLAN"))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.SYS_002));
    }

    private ResolvedFormTemplate resolvedTemplate() {
        when(templateRepository.findEffectiveByProjectType("YEAR_PLAN"))
                .thenReturn(Optional.of(template(SCHEMA)));
        return resolver.resolve("YEAR_PLAN");
    }

    private static ApplyFormTemplateEntity template(String schema) {
        ApplyFormTemplateEntity template = new ApplyFormTemplateEntity();
        template.setId("eeeeeeeeffffffff0000000011111111");
        template.setProjectType("YEAR_PLAN");
        template.setVersionNo(3);
        template.setStatus(ApplyFormTemplateEntity.STATUS_PUBLISHED);
        template.setFormSchema(schema);
        return template;
    }
}
