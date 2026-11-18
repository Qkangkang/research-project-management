package cn.edu.rpm.iam.application.dto;

import cn.edu.rpm.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AuthDecision} 纯单元测试（T-IAM-007 先行测试，技术文档 03 §2.1）。
 *
 * <p>验收锚点：放行判定携带 {@code ALLOW}；拒绝判定携带文档错误码
 * （TC-IAM-002/003 约定 {@code RPM-COMMON-403}）；脱敏字段集合不可变。</p>
 */
class AuthDecisionTest {

    @Test
    void allowCarriesAllowReasonAndNoMaskedFields() {
        AuthDecision decision = AuthDecision.allow();

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.reasonCode()).isEqualTo("ALLOW");
        assertThat(decision.maskedFields()).isEmpty();
    }

    @Test
    void denyCarriesDocumentedErrorCode() {
        AuthDecision decision = AuthDecision.deny(ErrorCode.COMMON_403);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reasonCode()).isEqualTo("RPM-COMMON-403");
        assertThat(decision.maskedFields()).isEmpty();
    }

    @Test
    void maskedFieldsAreImmutableCopy() {
        Set<String> source = new HashSet<>(Set.of("bankAccount"));

        AuthDecision decision = new AuthDecision(true, "ALLOW", source);
        source.add("idCard");

        assertThat(decision.maskedFields()).containsExactly("bankAccount");
    }

    @Test
    void normalizesNullMaskedFields() {
        assertThat(new AuthDecision(false, "RPM-COMMON-403", null).maskedFields()).isEmpty();
    }
}
