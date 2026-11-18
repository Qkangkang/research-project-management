package cn.edu.rpm.common.util;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link CanonicalJsonHasher} 纯单元测试（T-PRJ-018 支撑）。
 */
class CanonicalJsonHasherTest {

    private final CanonicalJsonHasher hasher = new CanonicalJsonHasher();

    @Test
    void canonicalJsonIsIndependentOfKeyInsertionOrder() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("title", "智能感知");
        first.put("period", Map.of("start", "2026-12-01", "end", "2029-11-30"));

        Map<String, Object> second = new LinkedHashMap<>();
        second.put("period", Map.of("end", "2029-11-30", "start", "2026-12-01"));
        second.put("title", "智能感知");

        assertThat(hasher.canonicalJson(first)).isEqualTo(hasher.canonicalJson(second));
    }

    @Test
    void canonicalJsonPreservesArrayOrder() {
        assertThat(hasher.canonicalJson(Map.of("milestones", List.of("a", "b"))))
                .isNotEqualTo(hasher.canonicalJson(Map.of("milestones", List.of("b", "a"))));
    }

    @Test
    void sha256MatchesStandardVector() {
        assertThat(hasher.sha256("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void snapshotHashIsStableAndSixtyFourHexChars() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("projectType", "YEAR_PLAN");
        snapshot.put("title", "面向复杂工况的智能感知方法研究");

        String hash = hasher.sha256(hasher.canonicalJson(snapshot));

        assertThat(hash).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(hash).isEqualTo(hasher.sha256(hasher.canonicalJson(snapshot)));
    }
}
