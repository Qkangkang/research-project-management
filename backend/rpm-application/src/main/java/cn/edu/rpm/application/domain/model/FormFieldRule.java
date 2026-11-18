package cn.edu.rpm.application.domain.model;

import java.math.BigDecimal;

/**
 * 表单字段规则（JSON Schema 子集，T-APP-013）。
 *
 * <p>一期只支持申报表单实际使用的校验关键字：{@code type}、{@code minLength}、
 * {@code maxLength}、{@code minItems}、{@code maxItems}、{@code minimum}、
 * {@code maximum}、{@code format}（{@code date} / {@code date-time}）。
 * 未识别的关键字忽略，保持前向兼容。</p>
 *
 * @param type      类型：{@code string} / {@code number} / {@code integer} /
 *                  {@code boolean} / {@code array} / {@code object}
 * @param minLength 字符串最小长度；可空
 * @param maxLength 字符串最大长度；可空
 * @param minItems  数组最小元素数；可空
 * @param maxItems  数组最大元素数；可空
 * @param minimum   数值下限（含）；可空
 * @param maximum   数值上限（含）；可空
 * @param format    字符串格式：{@code date} / {@code date-time}；可空
 */
public record FormFieldRule(
        String type,
        Integer minLength,
        Integer maxLength,
        Integer minItems,
        Integer maxItems,
        BigDecimal minimum,
        BigDecimal maximum,
        String format
) {
}
