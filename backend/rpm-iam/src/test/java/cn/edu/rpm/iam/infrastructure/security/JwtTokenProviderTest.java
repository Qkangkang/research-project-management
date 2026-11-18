package cn.edu.rpm.iam.infrastructure.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JwtTokenProvider 单元测试（V10 认证模块）。
 */
class JwtTokenProviderTest {

    private static final String SECRET =
            "test-secret-key-must-be-at-least-32-bytes-long-for-hs256";
    private static final long ACCESS_MS = 3600_000L;   // 1h
    private static final long REFRESH_MS = 86400_000L; // 24h

    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider(SECRET, ACCESS_MS, REFRESH_MS, "test-issuer");
    }

    // ─────── createAccessToken ───────

    @Test
    void createAccessToken_returnsNonBlankToken() {
        String token = provider.createAccessToken("user-001", "EMP001", Map.of("roles", List.of("ADMIN")));
        assertThat(token).isNotBlank();
    }

    @Test
    void createAccessToken_claimsCanBeParsed() {
        String token = provider.createAccessToken("user-001", "EMP001", Map.of("roles", List.of("ADMIN")));
        Claims claims = provider.parseToken(token);

        assertThat(claims).isNotNull();
        assertThat(claims.getSubject()).isEqualTo("user-001");
        assertThat(claims.get("employeeNo", String.class)).isEqualTo("EMP001");
        assertThat(claims.get("type", String.class)).isEqualTo("access");
    }

    @Test
    void getUserId_extractsSubject() {
        String token = provider.createAccessToken("user-001", "EMP001", Map.of());
        assertThat(provider.getUserId(token)).isEqualTo("user-001");
    }

    @Test
    void getEmployeeNo_extractsClaim() {
        String token = provider.createAccessToken("user-001", "EMP001", Map.of());
        assertThat(provider.getEmployeeNo(token)).isEqualTo("EMP001");
    }

    // ─────── createRefreshToken ───────

    @Test
    void createRefreshToken_typeIsRefresh() {
        String token = provider.createRefreshToken("user-001", "EMP001");
        Claims claims = provider.parseToken(token);
        assertThat(claims.get("type", String.class)).isEqualTo("refresh");
    }

    // ─────── validateToken ───────

    @Test
    void validateToken_validToken_returnsTrue() {
        String token = provider.createAccessToken("user-001", "EMP001", Map.of());
        assertThat(provider.validateToken(token)).isTrue();
    }

    @Test
    void validateToken_invalidSignature_returnsFalse() {
        JwtTokenProvider otherProvider = new JwtTokenProvider(
                "different-secret-key-must-be-32-bytes-long-too", ACCESS_MS, REFRESH_MS, "test");
        String token = otherProvider.createAccessToken("user-001", "EMP001", Map.of());
        assertThat(provider.validateToken(token)).isFalse();
    }

    @Test
    void validateToken_malformedToken_returnsFalse() {
        assertThat(provider.validateToken("not.a.jwt.token")).isFalse();
    }

    @Test
    void validateToken_nullToken_returnsFalse() {
        assertThat(provider.validateToken(null)).isFalse();
    }

    @Test
    void validateToken_expiredToken_returnsFalse() throws InterruptedException {
        // 创建 100ms 后过期的 Token
        JwtTokenProvider shortLived = new JwtTokenProvider(SECRET, 100L, 100L, "test-issuer");
        String token = shortLived.createAccessToken("user-001", "EMP001", Map.of());
        Thread.sleep(200);
        assertThat(shortLived.validateToken(token)).isFalse();
    }

    // ─────── 构造参数校验 ───────

    @Test
    void constructor_shortSecret_throwsException() {
        assertThatThrownBy(() -> new JwtTokenProvider("short-key", ACCESS_MS, REFRESH_MS, "test"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32 字节");
    }
}
