package cn.edu.rpm.common.domain.service;

import cn.edu.rpm.common.domain.model.NumberResetCycle;
import cn.edu.rpm.common.domain.model.NumberRule;
import cn.edu.rpm.common.domain.port.NumberRuleQueryPort;
import cn.edu.rpm.common.domain.port.SequenceValuePort;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link NumberGenerator} 纯单元测试（T-APP-009 先行测试）。
 *
 * <p>覆盖：前缀 + 日期段 + 补零序列的格式、按重置周期的日期段、并发生成不重号、
 * 规则/序列配置非法时的快速失败。固定时钟取 {@code Asia/Shanghai} 2026-11-18。</p>
 */
class NumberGeneratorTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-11-18T02:00:00Z"), ZONE);

    @Test
    void generatesPrefixDateAndPaddedSequence() {
        NumberGenerator generator = new NumberGenerator(
                rulePort(new NumberRule("APPLICATION_NO", "APP", "yyyy",
                        "seq_application_no", 6, NumberResetCycle.YEAR)),
                sequencePort(123), FIXED_CLOCK);

        assertThat(generator.next("APPLICATION_NO")).isEqualTo("APP-2026-000123");
    }

    @Test
    void usesConfiguredPaddingAndNeverCycleWithoutDateSegment() {
        NumberGenerator generator = new NumberGenerator(
                rulePort(new NumberRule("OUTPUT_NO", "OUT", "",
                        "seq_output_no", 4, NumberResetCycle.NEVER)),
                sequencePort(7), FIXED_CLOCK);

        assertThat(generator.next("OUTPUT_NO")).isEqualTo("OUT-0007");
    }

    @Test
    void rendersCycleSegmentFromResetCycleWhenDatePatternBlank() {
        NumberGenerator yearly = generatorFor(NumberResetCycle.YEAR, "");
        NumberGenerator monthly = generatorFor(NumberResetCycle.MONTH, "");
        NumberGenerator daily = generatorFor(NumberResetCycle.DAY, "");

        assertThat(yearly.next("RULE")).isEqualTo("APP-2026-000001");
        assertThat(monthly.next("RULE")).isEqualTo("APP-202611-000001");
        assertThat(daily.next("RULE")).isEqualTo("APP-20261118-000001");
    }

    @Test
    void concurrentGenerationProducesUniqueNumbers() throws Exception {
        int threads = 8;
        int perThread = 100;
        NumberGenerator generator = new NumberGenerator(
                rulePort(new NumberRule("APPLICATION_NO", "APP", "yyyy",
                        "seq_application_no", 6, NumberResetCycle.YEAR)),
                sequencePort(), FIXED_CLOCK);

        Set<String> numbers = ConcurrentHashMap.newKeySet();
        List<Throwable> failures = new ArrayList<>();
        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            for (int t = 0; t < threads; t++) {
                executor.submit(() -> {
                    try {
                        startGate.await();
                        for (int i = 0; i < perThread; i++) {
                            numbers.add(generator.next("APPLICATION_NO"));
                        }
                    } catch (Throwable ex) {
                        failures.add(ex);
                    }
                });
            }
            startGate.countDown();
        } finally {
            executor.shutdown();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(failures).isEmpty();
        assertThat(numbers).hasSize(threads * perThread);
        assertThat(numbers).allMatch(number -> number.matches("APP-2026-\\d{6}"));
    }

    @Test
    void rejectsUnknownRuleWithSystemConfigError() {
        NumberGenerator generator = new NumberGenerator(
                code -> Optional.empty(), sequencePort(1), FIXED_CLOCK);

        assertThatThrownBy(() -> generator.next("UNKNOWN_NO"))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.SYS_002));
    }

    @Test
    void rejectsIllegalSequenceNameBeforeCallingDatabase() {
        AtomicLong calls = new AtomicLong();
        SequenceValuePort sequencePort = sequenceName -> {
            calls.incrementAndGet();
            return 1L;
        };
        NumberGenerator generator = new NumberGenerator(
                rulePort(new NumberRule("APPLICATION_NO", "APP", "yyyy",
                        "seq_application_no; DROP TABLE application", 6, NumberResetCycle.YEAR)),
                sequencePort, FIXED_CLOCK);

        assertThatThrownBy(() -> generator.next("APPLICATION_NO"))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.SYS_002));
        assertThat(calls).hasValue(0);
    }

    @Test
    void rejectsNonPositiveSequenceValue() {
        NumberGenerator generator = new NumberGenerator(
                rulePort(new NumberRule("APPLICATION_NO", "APP", "yyyy",
                        "seq_application_no", 6, NumberResetCycle.YEAR)),
                sequencePort(0), FIXED_CLOCK);

        assertThatThrownBy(() -> generator.next("APPLICATION_NO"))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.SYS_002));
    }

    @Test
    void rejectsInvalidDatePattern() {
        NumberGenerator generator = new NumberGenerator(
                rulePort(new NumberRule("APPLICATION_NO", "APP", "not-a-pattern",
                        "seq_application_no", 6, NumberResetCycle.YEAR)),
                sequencePort(1), FIXED_CLOCK);

        assertThatThrownBy(() -> generator.next("APPLICATION_NO"))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.SYS_002));
    }

    private static NumberGenerator generatorFor(NumberResetCycle cycle, String datePattern) {
        return new NumberGenerator(
                rulePort(new NumberRule("RULE", "APP", datePattern,
                        "seq_application_no", 6, cycle)),
                sequencePort(1), FIXED_CLOCK);
    }

    private static NumberRuleQueryPort rulePort(NumberRule rule) {
        return ruleCode -> rule.ruleCode().equals(ruleCode) ? Optional.of(rule) : Optional.empty();
    }

    private static SequenceValuePort sequencePort(long value) {
        return sequenceName -> value;
    }

    private static SequenceValuePort sequencePort() {
        AtomicLong counter = new AtomicLong();
        return sequenceName -> counter.incrementAndGet();
    }
}
