package cn.edu.rpm.common.domain.model;

import java.util.Objects;

/**
 * 编号规则领域模型（T-APP-009；{@code number_rule} 表，V01 DDL）。
 *
 * <p>只承载生成编号所需的只读配置，不依赖 MyBatis PO；由
 * {@link cn.edu.rpm.common.domain.port.NumberRuleQueryPort} 从配置表装载。</p>
 *
 * @param ruleCode     规则编码（{@code uk_number_rule_code} 唯一），如 {@code APPLICATION_NO}
 * @param prefix       编号前缀，如 {@code APP}
 * @param datePattern  日期段格式（如 {@code yyyy}）；为空时按重置周期取默认格式
 * @param sequenceName 数据库序列名（只允许小写字母、数字、下划线）
 * @param padding      序列号补零位数（1～12）
 * @param resetCycle   重置周期（NEVER / YEAR / MONTH / DAY）
 */
public record NumberRule(
        String ruleCode,
        String prefix,
        String datePattern,
        String sequenceName,
        int padding,
        NumberResetCycle resetCycle
) {

    /** 校验规则关键字段，非法配置快速失败（由调用方转换为 RPM-SYS-002）。 */
    public NumberRule {
        if (ruleCode == null || ruleCode.isBlank()) {
            throw new IllegalArgumentException("ruleCode 不能为空");
        }
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("prefix 不能为空");
        }
        if (sequenceName == null || sequenceName.isBlank()) {
            throw new IllegalArgumentException("sequenceName 不能为空");
        }
        if (padding < 1 || padding > 12) {
            throw new IllegalArgumentException("padding 必须介于 1 和 12 之间");
        }
        Objects.requireNonNull(resetCycle, "resetCycle 不能为空");
    }
}
