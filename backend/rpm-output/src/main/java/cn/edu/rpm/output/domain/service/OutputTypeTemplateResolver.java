package cn.edu.rpm.output.domain.service;

import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.output.domain.model.OutputDraftData;
import cn.edu.rpm.output.domain.model.OutputTypeTemplate;
import cn.edu.rpm.output.domain.model.OutputTypes;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 成果类型模板解析与校验（T-OUT-007；技术文档 05 §4.2、07 §5.1）。
 *
 * <p>按 {@code output_type} 读取该类型的必填公共字段与扩展元数据格式，统一校验：</p>
 * <ul>
 *   <li>公共字段：标题必填且 ≤ 512 字符、完成日期必填且不得晚于当前日期；</li>
 *   <li>类型差异字段：PATENT 额外要求专利号；扩展元数据键类型不符时拒绝；</li>
 *   <li>标识格式：DOI 符合 {@code 10.{4-9 位注册机构}/后缀} 语法；专利号 ≤ 128 字符。</li>
 * </ul>
 *
 * <p>失败语义（与验证用例一致）：缺少标题/完成日期等必填项 → {@code RPM-COMMON-422}；
 * DOI 格式非法、完成日期晚于当前 → {@code RPM-COMMON-400}。</p>
 *
 * <p>纯 Java 领域服务：不依赖 Spring / ORM，时间基准由调用方传入，便于固定时钟单测。
 * 院方正式模板未定前，模板在此集中声明（论文/专利示例），禁止在校验逻辑内散落类型分支。</p>
 */
public class OutputTypeTemplateResolver {

    /** DOI 语法：注册机构前缀 4～9 位数字 + 后缀。 */
    private static final Pattern DOI_PATTERN = Pattern.compile("^10\\.\\d{4,9}/\\S+$");

    private static final int TITLE_MAX_LENGTH = 512;

    private static final int DOI_MAX_LENGTH = 256;

    private static final int PATENT_NO_MAX_LENGTH = 128;

    private static final Map<String, OutputTypeTemplate> TEMPLATES = Map.of(
            OutputTypes.PAPER, new OutputTypeTemplate(
                    OutputTypes.PAPER, Set.of(),
                    Map.of("journal", OutputTypeTemplate.MetadataFieldType.TEXT,
                            "authors", OutputTypeTemplate.MetadataFieldType.LIST,
                            "doi", OutputTypeTemplate.MetadataFieldType.TEXT)),
            OutputTypes.PATENT, new OutputTypeTemplate(
                    OutputTypes.PATENT, Set.of("patentNo"),
                    Map.of("inventors", OutputTypeTemplate.MetadataFieldType.LIST,
                            "application_no", OutputTypeTemplate.MetadataFieldType.TEXT)),
            OutputTypes.SOFTWARE, new OutputTypeTemplate(
                    OutputTypes.SOFTWARE, Set.of(),
                    Map.of("registration_no", OutputTypeTemplate.MetadataFieldType.TEXT,
                            "software_version", OutputTypeTemplate.MetadataFieldType.TEXT)),
            OutputTypes.STANDARD, new OutputTypeTemplate(
                    OutputTypes.STANDARD, Set.of(),
                    Map.of("standard_no", OutputTypeTemplate.MetadataFieldType.TEXT,
                            "standard_level", OutputTypeTemplate.MetadataFieldType.TEXT)),
            OutputTypes.AWARD, new OutputTypeTemplate(
                    OutputTypes.AWARD, Set.of(),
                    Map.of("award_level", OutputTypeTemplate.MetadataFieldType.TEXT,
                            "award_year", OutputTypeTemplate.MetadataFieldType.NUMBER)),
            OutputTypes.REPORT, new OutputTypeTemplate(
                    OutputTypes.REPORT, Set.of(),
                    Map.of("report_type", OutputTypeTemplate.MetadataFieldType.TEXT)),
            OutputTypes.OTHER, new OutputTypeTemplate(
                    OutputTypes.OTHER, Set.of(), Map.of()));

    /**
     * 解析成果类型模板。
     *
     * @param outputType 成果类型（大小写不敏感）
     * @return 类型模板
     * @throws BizException 类型缺失或不在 DDL 枚举内 {@code RPM-COMMON-400}
     */
    public OutputTypeTemplate resolve(String outputType) {
        if (outputType == null || outputType.isBlank()) {
            throw new BizException(ErrorCode.COMMON_400, "成果类型不能为空");
        }
        OutputTypeTemplate template = TEMPLATES.get(outputType.trim().toUpperCase(Locale.ROOT));
        if (template == null) {
            throw new BizException(ErrorCode.COMMON_400, "成果类型非法: " + outputType);
        }
        return template;
    }

    /**
     * 校验成果登记/修订元数据。
     *
     * <p>触发场景：成果登记、保存草稿与修订。校验顺序固定为「类型 → 必填 →
     * 完成日期 → 标识格式 → 扩展元数据类型」，保证同一输入返回稳定错误码。</p>
     *
     * @param data  待校验元数据
     * @param today 当前业务日期（由调用方以 {@code Clock} 计算）
     * @throws BizException 参数/格式非法 {@code RPM-COMMON-400}；必填缺失或类型不符
     *                      {@code RPM-COMMON-422}
     */
    public void validate(OutputDraftData data, LocalDate today) {
        if (data == null) {
            throw new BizException(ErrorCode.COMMON_400, "成果元数据不能为空");
        }
        if (today == null) {
            throw new IllegalArgumentException("today 不能为空");
        }
        OutputTypeTemplate template = resolve(data.outputType());

        String title = normalize(data.title());
        if (title == null) {
            throw new BizException(ErrorCode.COMMON_422, "成果标题不能为空");
        }
        if (title.length() > TITLE_MAX_LENGTH) {
            throw new BizException(ErrorCode.COMMON_422, "成果标题长度不能超过 512 字符");
        }
        if (data.completedAt() == null) {
            throw new BizException(ErrorCode.COMMON_422, "完成日期不能为空");
        }
        if (data.completedAt().isAfter(today)) {
            throw new BizException(ErrorCode.COMMON_400, "完成日期不得晚于当前日期");
        }

        String doi = normalize(data.doi());
        if (doi != null) {
            if (doi.length() > DOI_MAX_LENGTH) {
                throw new BizException(ErrorCode.COMMON_400, "DOI 长度不能超过 256 字符");
            }
            if (!DOI_PATTERN.matcher(doi).matches()) {
                throw new BizException(ErrorCode.COMMON_400, "DOI 格式非法: " + doi);
            }
        }

        String patentNo = normalize(data.patentNo());
        if (template.requiredFields().contains("patentNo") && patentNo == null) {
            throw new BizException(ErrorCode.COMMON_422, "专利成果必须填写专利号");
        }
        if (patentNo != null && patentNo.length() > PATENT_NO_MAX_LENGTH) {
            throw new BizException(ErrorCode.COMMON_400, "专利号长度不能超过 128 字符");
        }

        for (Map.Entry<String, Object> entry : data.metadata().entrySet()) {
            if (!template.accepts(entry.getKey(), entry.getValue())) {
                throw new BizException(ErrorCode.COMMON_422,
                        "扩展元数据字段类型不符: " + entry.getKey());
            }
        }
        Object metadataDoi = data.metadata().get("doi");
        if (metadataDoi instanceof String metadataDoiText && !metadataDoiText.isBlank()
                && !DOI_PATTERN.matcher(metadataDoiText.trim()).matches()) {
            throw new BizException(ErrorCode.COMMON_400, "扩展元数据 DOI 格式非法");
        }
    }

    /**
     * 返回规范化（去首尾空格）后的类型差异必填元数据键。
     *
     * @param outputType 成果类型
     * @return 额外必填公共字段集合
     */
    public Set<String> requiredFields(String outputType) {
        return resolve(outputType).requiredFields();
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
