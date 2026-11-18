package cn.edu.rpm.audit.application.service;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AuditDigestService} 纯单元测试（T-AUD-003 先行测试）。
 *
 * <p>断言摘要为稳定的 64 位小写十六进制 SHA-256：同值同摘要、异值异摘要、
 * Map 键顺序不影响结果；不可序列化对象走回退路径仍产出摘要而非抛错；
 * 摘要本身不含业务明文。</p>
 */
class AuditDigestServiceTest {

    private final AuditDigestService digestService = new AuditDigestService();

    @Test
    void sameValueProducesSame64HexDigest() {
        Object value = Map.of("applicationId", "A-001", "version", 3);

        String first = digestService.digest(value);
        String second = digestService.digest(value);

        assertThat(first).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(second).isEqualTo(first);
    }

    @Test
    void differentValuesProduceDifferentDigests() {
        String submitted = digestService.digest(new String[]{"SUBMITTED", "v1"});
        String approved = digestService.digest(new String[]{"APPROVED", "v1"});

        assertThat(submitted).isNotEqualTo(approved);
    }

    @Test
    void nullValueProducesNullDigest() {
        assertThat(digestService.digest(null)).isNull();
    }

    @Test
    void mapInsertionOrderDoesNotChangeDigest() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("b", 2);
        first.put("a", 1);
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("a", 1);
        second.put("b", 2);

        assertThat(digestService.digest(first)).isEqualTo(digestService.digest(second));
    }

    @Test
    void unserializableValueFallsBackToStableDigestWithoutThrowing() {
        Object broken = new BrokenBean();

        String digest = digestService.digest(broken);

        assertThat(digest).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(digestService.digest(broken)).isEqualTo(digest);
    }

    @Test
    void digestDoesNotContainPlaintextSecret() {
        String digest = digestService.digest(Map.of("bankAccount", "6222020202020202"));

        assertThat(digest).doesNotContain("6222020202020202");
    }

    /** 序列化时 getter 抛错的 Bean，用于验证回退路径。 */
    public static class BrokenBean {

        public String getBoom() {
            throw new IllegalStateException("cannot serialize");
        }
    }
}
