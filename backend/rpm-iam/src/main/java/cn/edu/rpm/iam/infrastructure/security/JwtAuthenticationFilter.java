package cn.edu.rpm.iam.infrastructure.security;

import cn.edu.rpm.iam.application.context.AuthContextHolder;
import cn.edu.rpm.iam.application.dto.AuthContext;
import cn.edu.rpm.iam.domain.model.RoleAssignmentView;
import cn.edu.rpm.iam.domain.port.UserRoleQueryPort;
import cn.edu.rpm.iam.domain.service.FunctionPermissionResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * JWT 认证过滤器（V10 认证模块）。
 *
 * <p>职责：</p>
 * <ol>
 *   <li>从 Authorization: Bearer {token} 解析 JWT；</li>
 *   <li>查询用户当前有效角色列表，逐角色调用 {@link FunctionPermissionResolver}
 *       加载权限快照（含 Redis 缓存），合并后写入 {@link AuthContextHolder}；</li>
 *   <li>请求结束后清理上下文。</li>
 * </ol>
 */
public class JwtAuthenticationFilter implements jakarta.servlet.Filter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER = "Bearer ";

    private final JwtTokenProvider jwtProvider;
    private final FunctionPermissionResolver permissionResolver;
    private final UserRoleQueryPort userRoleQueryPort;

    public JwtAuthenticationFilter(JwtTokenProvider jwtProvider,
                                   FunctionPermissionResolver permissionResolver,
                                   UserRoleQueryPort userRoleQueryPort) {
        this.jwtProvider = jwtProvider;
        this.permissionResolver = permissionResolver;
        this.userRoleQueryPort = userRoleQueryPort;
    }

    @Override
    public void doFilter(jakarta.servlet.ServletRequest request,
                         jakarta.servlet.ServletResponse response,
                         FilterChain chain) throws IOException, jakarta.servlet.ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        try {
            String token = extractToken(req);
            // 只接受 type=access 的 Token：refresh token 禁止直接调用业务 API
            io.jsonwebtoken.Claims claims = token != null ? jwtProvider.parseAccessToken(token) : null;
            if (claims != null) {
                String userId = claims.getSubject();
                String employeeNo = claims.get("employeeNo", String.class);

                if (userId != null) {
                    Set<String> permissionCodes = loadPermissions(userId);
                    AuthContext ctx = new AuthContext(
                            UUID.fromString(userId),
                            null,
                            employeeNo,
                            permissionCodes,
                            Set.of(),
                            Instant.now());
                    AuthContextHolder.set(ctx);
                    log.debug("认证成功 userId={} permissions={}", userId, permissionCodes.size());
                }
            }
        } catch (Exception e) {
            log.debug("Token 解析或权限加载异常，按匿名处理: {}", e.getMessage());
        }

        try {
            chain.doFilter(request, response);
        } finally {
            AuthContextHolder.clear();
        }
    }

    /**
     * 加载用户在当前时点的全部权限码（合并所有有效角色的权限快照）。
     *
     * <p>{@link FunctionPermissionResolver#resolvePermissions} 要求传具体 roleId，
     * 此处遍历用户的全部有效角色，逐一解析后合并为完整权限集。</p>
     */
    private Set<String> loadPermissions(String userId) {
        try {
            OffsetDateTime now = OffsetDateTime.now();
            List<RoleAssignmentView> roles = userRoleQueryPort.findValidAssignments(userId, now);
            if (roles == null || roles.isEmpty()) {
                return Set.of();
            }
            Set<String> merged = new HashSet<>();
            for (RoleAssignmentView role : roles) {
                Set<String> codes = permissionResolver.resolvePermissions(userId, role.roleId(), now);
                merged.addAll(codes);
            }
            return merged;
        } catch (Exception e) {
            log.warn("权限加载失败，降级为空权限集: userId={} error={}", userId, e.getMessage());
            return Set.of();
        }
    }

    private String extractToken(HttpServletRequest req) {
        String header = req.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER)) {
            return header.substring(BEARER.length()).trim();
        }
        return null;
    }
}
