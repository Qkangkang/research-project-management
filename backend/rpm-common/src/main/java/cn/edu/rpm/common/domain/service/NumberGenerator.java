package cn.edu.rpm.common.domain.service;

import cn.edu.rpm.common.domain.model.NumberRule;
import cn.edu.rpm.common.domain.port.NumberRuleQueryPort;
import cn.edu.rpm.common.domain.port.SequenceValuePort;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 业务编号生成器（T-APP-009；技术文档 02 §3.7 {@code number_rule}）。
 *
 * <p>生成口径：{@code 前缀 + "-" + 日期段 + "-" + 补零序列}，如
 * {@code APP-2026-000123}。序列来自 {@code number_rule.sequence_name} 指向的
 * PostgreSQL 序列（{@code nextval}），由数据库保证并发唯一；日期段按
 * {@code reset_cycle} / {@code date_pattern} 渲染，使编号按周期重新起算。</p>
 *
 * <p>安全约束：序列名必须先通过 {@code ^[a-z][a-z0-9_]{0,62}$} 白名单校验，
 * 适配器以 {@code nextval(?::regclass)} 参数化取值，禁止字符串拼接 SQL。
 * 规则缺失、序列名非法、日期格式非法统一抛 {@code RPM-SYS-002}（配置值非法）。</p>
 *
 * <p>纯 Java 领域服务，不依赖 Spring / MyBatis；时钟与端口由装配层注入，
 * 便于固定时点单测。</p>
 */
public class NumberGenerator {

    /** 申报编号规则编码（{@code number_rule.rule_code}）。 */
    public static final String RULE_APPLICATION_NO = "APPLICATION_NO";

    /** 允许的数据库序列名（PostgreSQL 未加引号标识符口径）。 */
    private static final Pattern SEQUENCE_NAME = Pattern.compile("^[a-z][a-z0-9_]{0,62}$");

    private final NumberRuleQueryPort numberRuleQueryPort;

    private final SequenceValuePort sequenceValuePort;

    private final Clock clock;

    /**
     * 构造编号生成器。
     *
     * @param numberRuleQueryPort 编号规则查询端口
     * @param sequenceValuePort   数据库序列取值端口
     * @param clock               时间基准（决定日期段）
     */
    public NumberGenerator(NumberRuleQueryPort numberRuleQueryPort,
                           SequenceValuePort sequenceValuePort,
                           Clock clock) {
        this.numberRuleQueryPort = Objects.requireNonNull(numberRuleQueryPort, "numberRuleQueryPort 不能为空");
        this.sequenceValuePort = Objects.requireNonNull(sequenceValuePort, "sequenceValuePort 不能为空");
        this.clock = Objects.requireNonNull(clock, "clock 不能为空");
    }

    /**
     * 按规则生成下一个业务编号。
     *
     * @param ruleCode 编号规则编码（如 {@link #RULE_APPLICATION_NO}）
     * @return 形如 {@code APP-2026-000123} 的业务编号
     * @throws BizException 规则未配置/停用、序列不可用或配置非法 {@code RPM-SYS-002}
     */
    public String next(String ruleCode) {
        if (ruleCode == null || ruleCode.isBlank()) {
            throw new IllegalArgumentException("ruleCode 不能为空");
        }
        NumberRule rule = numberRuleQueryPort.findActiveByRuleCode(ruleCode.trim())
                .orElseThrow(() -> new BizException(ErrorCode.SYS_002,
                        "未配置启用的编号规则: " + ruleCode));
        if (!SEQUENCE_NAME.matcher(rule.sequenceName()).matches()) {
            throw new BizException(ErrorCode.SYS_002,
                    "编号序列名非法: " + rule.sequenceName());
        }

        long sequenceValue;
        try {
            sequenceValue = sequenceValuePort.nextValue(rule.sequenceName());
        } catch (BizException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BizException(ErrorCode.SYS_002,
                    "编号序列不可用: " + rule.sequenceName());
        }
        if (sequenceValue <= 0) {
            throw new BizException(ErrorCode.SYS_002,
                    "编号序列返回值非法: " + sequenceValue);
        }

        String dateSegment;
        try {
            dateSegment = rule.resetCycle()
                    .formatDateSegment(LocalDate.now(clock), rule.datePattern());
        } catch (IllegalArgumentException ex) {
            throw new BizException(ErrorCode.SYS_002, ex.getMessage());
        }

        String sequenceSegment = String.format("%0" + rule.padding() + "d", sequenceValue);
        if (dateSegment.isEmpty()) {
            return rule.prefix() + "-" + sequenceSegment;
        }
        return rule.prefix() + "-" + dateSegment + "-" + sequenceSegment;
    }
}
