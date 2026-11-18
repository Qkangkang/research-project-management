package cn.edu.rpm.output.domain.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 成果类型模板（T-OUT-007；技术文档 05 §4.2）。
 *
 * <p>公共字段统一（标题、完成日期、完成人、部门），类型差异体现在：
 * {@link #requiredFields()} 声明该类型额外必填的公共字段（如专利号）；
 * {@link #metadataFieldTypes()} 声明该类型允许的扩展元数据键及期望类型，
 * 类型不符时返回 {@code RPM-COMMON-422}。</p>
 *
 * <p>院方正式模板未定时按 PRD RES-01 的公共字段 + 论文/专利示例实现；
 * 模板以本记录集中声明，禁止在校验逻辑内散落类型分支。</p>
 *
 * @param outputType         成果类型
 * @param requiredFields     额外必填公共字段（如 {@code patentNo}）
 * @param metadataFieldTypes 扩展元数据键 → 期望 JSON 类型
 */
public record OutputTypeTemplate(
        String outputType,
        Set<String> requiredFields,
        Map<String, MetadataFieldType> metadataFieldTypes
) {

    /** 扩展元数据期望类型。 */
    public enum MetadataFieldType {

        /** 文本（JSON string）。 */
        TEXT,

        /** 数值（JSON number）。 */
        NUMBER,

        /** 列表（JSON array）。 */
        LIST
    }

    /** 规范化集合与映射。 */
    public OutputTypeTemplate {
        Objects.requireNonNull(outputType, "outputType 不能为空");
        requiredFields = requiredFields == null ? Set.of() : Set.copyOf(requiredFields);
        metadataFieldTypes = metadataFieldTypes == null ? Map.of() : Map.copyOf(metadataFieldTypes);
    }

    /**
     * 判断元数据值是否符合该键的期望类型。
     *
     * @param key   元数据键
     * @param value 元数据值（可为 {@code null}）
     * @return 未声明的键或类型匹配返回 {@code true}
     */
    public boolean accepts(String key, Object value) {
        MetadataFieldType expected = metadataFieldTypes.get(key);
        if (expected == null || value == null) {
            return true;
        }
        return switch (expected) {
            case TEXT -> value instanceof String;
            case NUMBER -> value instanceof Number;
            case LIST -> value instanceof List<?>;
        };
    }
}
