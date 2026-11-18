package cn.edu.rpm.iam.domain.service;

import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link GrantValidityGuard} 纯单元测试（T-IAM-012 先行测试，技术文档 03 §2.5）。
 *
 * <p>覆盖验证用例：</p>
 * <ul>
 *   <li>TC-IAM-007 边界：{@code valid_to=2026-11-17T18:00:00+08:00}，请求时间正好
 *       {@code 18:00:00} → 判定过期，抛 {@code RPM-COMMON-403}；</li>
 *   <li>TC-IAM-008 正常：有效期内（如 {@code 17:59:59}）→ 放行。</li>
 * </ul>
 */
class GrantValidityGuardTest {

    private static final Instant VALID_FROM = Instant.parse("2026-11-17T09:00:00+08:00");
    private static final Instant VALID_TO = Instant.parse("2026-11-17T18:00:00+08:00");

    private final GrantValidityGuard guard = new GrantValidityGuard();

    @Test
    void requestExactlyAtValidToIsRejectedWith403() {
        assertThatThrownBy(() -> guard.check(VALID_FROM, VALID_TO, VALID_TO))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode()).isEqualTo(ErrorCode.COMMON_403));
    }

    @Test
    void requestOneSecondBeforeValidToIsValid() {
        assertThatCode(() -> guard.check(VALID_FROM, VALID_TO, VALID_TO.minusSeconds(1)))
                .doesNotThrowAnyException();
    }

    @Test
    void requestExactlyAtValidFromIsValid() {
        assertThatCode(() -> guard.check(VALID_FROM, VALID_TO, VALID_FROM))
                .doesNotThrowAnyException();
    }

    @Test
    void requestBeforeValidFromIsRejected() {
        assertThatThrownBy(() -> guard.check(VALID_FROM, VALID_TO, VALID_FROM.minusSeconds(1)))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode()).isEqualTo(ErrorCode.COMMON_403));
    }

    @Test
    void nullValidToIsRejected() {
        assertThatThrownBy(() -> guard.check(VALID_FROM, null, VALID_FROM.plusSeconds(1)))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode()).isEqualTo(ErrorCode.COMMON_403));
    }

    @Test
    void isValidMirrorsCheckBoundaries() {
        assertThat(guard.isValid(VALID_FROM, VALID_TO, VALID_TO)).isFalse();
        assertThat(guard.isValid(VALID_FROM, VALID_TO, VALID_TO.minusSeconds(1))).isTrue();
        assertThat(guard.isValid(VALID_FROM, VALID_TO, VALID_FROM)).isTrue();
        assertThat(guard.isValid(VALID_FROM, null, VALID_FROM)).isFalse();
    }
}
