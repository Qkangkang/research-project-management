package cn.edu.rpm.output.domain.service;

import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.output.domain.model.OutputDraftData;
import cn.edu.rpm.output.domain.model.OutputTypeTemplate;
import cn.edu.rpm.output.domain.model.OutputTypes;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link OutputTypeTemplateResolver} 纯单元测试（T-OUT-007 先行测试）。
 *
 * <p>覆盖 TC-OUT-002（缺标题/完成日期 → 422）、TC-OUT-003（DOI 非法 → 400）、
 * TC-OUT-004（完成日期晚于当前 → 400）与合法输入通过；另验证 PATENT 必填专利号
 * 与扩展元数据类型校验。</p>
 */
class OutputTypeTemplateResolverTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 18);

    private final OutputTypeTemplateResolver resolver = new OutputTypeTemplateResolver();

    @Test
    void resolveReturnsPaperTemplateForLowercaseType() {
        OutputTypeTemplate template = resolver.resolve("paper");

        assertThat(template.outputType()).isEqualTo(OutputTypes.PAPER);
        assertThat(template.requiredFields()).isEmpty();
        assertThat(template.metadataFieldTypes()).containsKeys("journal", "authors", "doi");
    }

    @Test
    void resolveRejectsUnknownTypeWithCommon400() {
        assertThatThrownBy(() -> resolver.resolve("BOOK"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.COMMON_400);
    }

    @Test
    void validateRejectsMissingTitleWithCommon422() {
        OutputDraftData data = new OutputDraftData(
                OutputTypes.PAPER, "  ", null, null, TODAY, Map.of());

        assertThatThrownBy(() -> resolver.validate(data, TODAY))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.COMMON_422);
    }

    @Test
    void validateRejectsMissingCompletedAtWithCommon422() {
        OutputDraftData data = new OutputDraftData(
                OutputTypes.PAPER, "复杂工况下的多模态感知方法", null, null, null, Map.of());

        assertThatThrownBy(() -> resolver.validate(data, TODAY))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.COMMON_422);
    }

    @Test
    void validateRejectsMalformedDoiWithCommon400() {
        OutputDraftData data = new OutputDraftData(
                OutputTypes.PAPER, "复杂工况下的多模态感知方法", "not-a-doi", null,
                TODAY.minusDays(1), Map.of());

        assertThatThrownBy(() -> resolver.validate(data, TODAY))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.COMMON_400);
    }

    @Test
    void validateRejectsFutureCompletedAtWithCommon400() {
        OutputDraftData data = new OutputDraftData(
                OutputTypes.PAPER, "复杂工况下的多模态感知方法", null, null,
                TODAY.plusDays(1), Map.of());

        assertThatThrownBy(() -> resolver.validate(data, TODAY))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.COMMON_400);
    }

    @Test
    void validateAcceptsLegalPaperMetadata() {
        OutputDraftData data = new OutputDraftData(
                OutputTypes.PAPER,
                "复杂工况下的多模态感知方法",
                "10.1000/example.2026.001",
                null,
                TODAY.minusDays(3),
                Map.of("journal", "某学报", "authors", java.util.List.of("张三", "李四")));

        resolver.validate(data, TODAY);
    }

    @Test
    void validateRequiresPatentNoForPatentType() {
        OutputDraftData data = new OutputDraftData(
                OutputTypes.PATENT, "一种感知装置", null, " ", TODAY.minusDays(1), Map.of());

        assertThatThrownBy(() -> resolver.validate(data, TODAY))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.COMMON_422);
    }

    @Test
    void validateRejectsMetadataTypeMismatchWithCommon422() {
        OutputDraftData data = new OutputDraftData(
                OutputTypes.AWARD, "科技进步奖", null, null, TODAY.minusDays(1),
                Map.of("award_year", "二零二六"));

        assertThatThrownBy(() -> resolver.validate(data, TODAY))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.COMMON_422);
    }

    @Test
    void requiredFieldsExposeTypeSpecificMandatoryKey() {
        assertThat(resolver.requiredFields(OutputTypes.PATENT)).containsExactly("patentNo");
        assertThat(resolver.requiredFields(OutputTypes.REPORT)).isEmpty();
    }
}
