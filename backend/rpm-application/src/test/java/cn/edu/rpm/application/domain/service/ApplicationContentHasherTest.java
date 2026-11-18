package cn.edu.rpm.application.domain.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ApplicationContentHasher} 纯单元测试（T-APP-014 支撑能力）。
 *
 * <p>验证规范 JSON 的键顺序无关性、数组保序、摘要标准向量与 64 位十六进制约束；
 * 这些性质是 {@code content_hash} 可复算与审计比对的前提。</p>
 */
class ApplicationContentHasherTest {

    private final ApplicationContentHasher hasher = new ApplicationContentHasher();

    @Test
    void canonicalJsonSortsNestedKeysAndIgnoresInsertionOrder() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("title", "智能感知");
        Map<String, Object> budget = new LinkedHashMap<>();
        budget.put("direct", new BigDecimal("80.00"));
        budget.put("indirect", new BigDecimal("20.00"));
        first.put("budget", budget);
        first.put("members", List.of("张三", "李四"));

        Map<String, Object> second = new LinkedHashMap<>();
        second.put("members", List.of("张三", "李四"));
        second.put("budget", Map.of("indirect", new BigDecimal("20.00"),
                "direct", new BigDecimal("80.00")));
        second.put("title", "智能感知");

        String canonical = hasher.canonicalJson(first);

        assertThat(canonical).isEqualTo(hasher.canonicalJson(second));
        assertThat(canonical).isEqualTo(
                "{\"budget\":{\"direct\":80.00,\"indirect\":20.00},\"members\":[\"张三\",\"李四\"],"
                        + "\"title\":\"智能感知\"}");
    }

    @Test
    void canonicalJsonPreservesArrayOrder() {
        String first = hasher.canonicalJson(Map.of("items", List.of("a", "b")));
        String second = hasher.canonicalJson(Map.of("items", List.of("b", "a")));

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void sha256MatchesStandardVector() {
        assertThat(hasher.sha256("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void formHashIsStableAndUsesSixtyFourHexChars() {
        Map<String, Object> formData = new LinkedHashMap<>();
        formData.put("title", "面向复杂工况的智能感知方法研究");
        formData.put("budget", new BigDecimal("100000.00"));

        String canonical = hasher.canonicalJson(formData);
        String hash = hasher.sha256(canonical);

        assertThat(hash).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(hash).isEqualTo(hasher.sha256(hasher.canonicalJson(formData)));
    }
}
