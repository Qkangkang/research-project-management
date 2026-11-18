package cn.edu.rpm.iam.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

/**
 * JWT Token 生成与解析（V10 认证模块）。
 *
 * <p>使用 jjwt 0.12.x，HS256 签名算法。Token 包含：userId, employeeNo, roles。
 * 技术文档 §10.3：Token 可撤销（通过 Redis 黑名单实现，本类不处理撤销逻辑）。</p>
 */
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    private final SecretKey secretKey;
    private final long accessTokenValidityMs;
    private final long refreshTokenValidityMs;
    private final String issuer;

    /**
     * 构造 JWT Provider。
     *
     * @param secret                   签名密钥（至少 32 字节）
     * @param accessTokenValidityMs    访问 Token 有效期（毫秒）
     * @param refreshTokenValidityMs   刷新 Token 有效期（毫秒）
     * @param issuer                   签发者标识
     */
    public JwtTokenProvider(String secret, long accessTokenValidityMs,
                           long refreshTokenValidityMs, String issuer) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("JWT 密钥长度不足 32 字节");
        }
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenValidityMs = accessTokenValidityMs;
        this.refreshTokenValidityMs = refreshTokenValidityMs;
        this.issuer = issuer;
    }

    /**
     * 生成访问 Token。
     *
     * @param userId     用户 ID
     * @param employeeNo 工号
     * @param claims     附加声明（如 roles）
     * @return JWT Token
     */
    public String createAccessToken(String userId, String employeeNo, Map<String, Object> claims) {
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(accessTokenValidityMs);

        return Jwts.builder()
                .subject(userId)
                .claim("employeeNo", employeeNo)
                .claim("type", "access")
                .claims(claims)
                .issuer(issuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(secretKey)
                .compact();
    }

    /**
     * 生成刷新 Token。
     *
     * @param userId     用户 ID
     * @param employeeNo 工号
     * @return Refresh Token
     */
    public String createRefreshToken(String userId, String employeeNo) {
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(refreshTokenValidityMs);

        return Jwts.builder()
                .subject(userId)
                .claim("employeeNo", employeeNo)
                .claim("type", "refresh")
                .issuer(issuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(secretKey)
                .compact();
    }

    /**
     * 解析 Token。
     *
     * @param token JWT Token
     * @return Claims（失败返回 null）
     */
    public Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            log.warn("Token 解析失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 解析并校验访问 Token（签名 + 有效期 + type=access）。
     *
     * <p>双 Token 语义隔离：refresh token 生命周期长（7 天），禁止当作 access token
     * 直接调用 API；泄漏的 access token 也不能用于 {@code /auth/refresh} 滚动续期。</p>
     *
     * @param token JWT Token
     * @return 校验通过的有效声明（否则 null）
     */
    public Claims parseAccessToken(String token) {
        return parseTokenOfType(token, "access");
    }

    /**
     * 校验刷新 Token（签名 + 有效期 + type=refresh）。
     *
     * @param token JWT Token
     * @return 是否为有效的 refresh token
     */
    public boolean validateRefreshToken(String token) {
        return parseTokenOfType(token, "refresh") != null;
    }

    private Claims parseTokenOfType(String token, String expectedType) {
        Claims claims = parseToken(token);
        if (claims == null) {
            return null;
        }
        Date expiration = claims.getExpiration();
        if (expiration == null || !expiration.after(new Date())) {
            return null;
        }
        return expectedType.equals(claims.get("type", String.class)) ? claims : null;
    }

    /**
     * 验证 Token 是否有效。
     *
     * @param token JWT Token
     * @return 是否有效
     */
    public boolean validateToken(String token) {
        Claims claims = parseToken(token);
        if (claims == null) {
            return false;
        }
        // 检查过期时间
        Date expiration = claims.getExpiration();
        return expiration != null && expiration.after(new Date());
    }

    /**
     * 从 Token 提取用户 ID。
     *
     * @param token JWT Token
     * @return 用户 ID（失败返回 null）
     */
    public String getUserId(String token) {
        Claims claims = parseToken(token);
        return claims != null ? claims.getSubject() : null;
    }

    /**
     * 从 Token 提取工号。
     *
     * @param token JWT Token
     * @return 工号（失败返回 null）
     */
    public String getEmployeeNo(String token) {
        Claims claims = parseToken(token);
        return claims != null ? claims.get("employeeNo", String.class) : null;
    }
}
