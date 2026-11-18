package cn.edu.rpm.common.domain.model;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * 编号序列重置周期（{@code number_rule.reset_cycle}，V01 DDL）。
 *
 * <p>周期决定编号中的日期段：{@link #YEAR} → {@code yyyy}、{@link #MONTH} →
 * {@code yyyyMM}、{@link #DAY} → {@code yyyyMMdd}、{@link #NEVER} → 无日期段。
 * 规则显式配置 {@code date_pattern} 时以配置为准。</p>
 *
 * <p>物理序列的重置由编号规则维护入口（{@code NumberRuleService}，后续批次）
 * 按周期执行；本枚举只负责编号分段口径，保证跨周期编号在视觉上按周期重新起算。</p>
 */
public enum NumberResetCycle {

    /** 永不重置。 */
    NEVER(""),

    /** 按年重置。 */
    YEAR("yyyy"),

    /** 按月重置。 */
    MONTH("yyyyMM"),

    /** 按日重置。 */
    DAY("yyyyMMdd");

    private final String defaultDatePattern;

    NumberResetCycle(String defaultDatePattern) {
        this.defaultDatePattern = defaultDatePattern;
    }

    /**
     * 周期对应的默认日期段格式。
     *
     * @return 日期段格式（{@link #NEVER} 返回空串）
     */
    public String defaultDatePattern() {
        return defaultDatePattern;
    }

    /**
     * 解析数据库枚举值。
     *
     * @param value 数据库值（NEVER / YEAR / MONTH / DAY）
     * @return 枚举值
     * @throws IllegalArgumentException 值非法
     */
    public static NumberResetCycle from(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("reset_cycle 不能为空");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("reset_cycle 非法: " + value, ex);
        }
    }

    /**
     * 按周期格式渲染日期段。
     *
     * @param date  业务日期（已按展示时区取本地日期）
     * @param pattern 日期格式；为空时使用周期默认格式
     * @return 日期段（{@link #NEVER} 且未配置格式时返回空串）
     * @throws IllegalArgumentException 日期格式非法
     */
    public String formatDateSegment(LocalDate date, String pattern) {
        String effectivePattern = pattern == null || pattern.isBlank() ? defaultDatePattern : pattern;
        if (effectivePattern.isEmpty()) {
            return "";
        }
        try {
            return DateTimeFormatter.ofPattern(effectivePattern, Locale.ROOT).format(date);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("date_pattern 非法: " + effectivePattern, ex);
        }
    }
}
