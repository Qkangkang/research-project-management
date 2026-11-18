package cn.edu.rpm.boot.config;

import cn.edu.rpm.iam.domain.port.UserRoleQueryPort;
import cn.edu.rpm.iam.application.dto.AuthContext;
import cn.edu.rpm.iam.application.service.AuthenticationService;
import cn.edu.rpm.iam.domain.service.FunctionPermissionResolver;
import cn.edu.rpm.iam.domain.service.PasswordEncoderService;
import cn.edu.rpm.iam.infrastructure.mapper.IamLoginLogMapper;
import cn.edu.rpm.iam.infrastructure.mapper.IamRoleMapper;
import cn.edu.rpm.iam.infrastructure.mapper.IamUserMapper;
import cn.edu.rpm.iam.infrastructure.security.JwtAuthenticationFilter;
import cn.edu.rpm.iam.infrastructure.security.JwtTokenProvider;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 认证模块装配（V10）。
 *
 * <p>集中装配 JWT Provider、认证过滤器、密码服务、认证应用服务。</p>
 */
@Configuration(proxyBeanMethods = false)
public class AuthConfig {

    /** Access Token 有效期（秒），默认 8 小时。 */
    private static final long DEFAULT_ACCESS_TOKEN_VALIDITY_SEC = 8 * 3600;

    /** Refresh Token 有效期（秒），默认 7 天。 */
    private static final long DEFAULT_REFRESH_TOKEN_VALIDITY_MS = 7L * 24 * 3600 * 1000;

    /**
     * JWT Token Provider。
     *
     * <p>密钥来源分环境处理：</p>
     * <ul>
     *   <li>prod（及未识别 profile）：{@code JWT_SECRET} 缺失即 fail-fast，禁止回退到内置密钥——
     *       否则任何读过仓库的人都能伪造任意用户（含 ADMIN）的 Token；</li>
     *   <li>dev/test：允许回退到开发密钥，但打印醒目告警。</li>
     * </ul>
     */
    @Bean
    public JwtTokenProvider jwtTokenProvider(
            @Value("${rpm.security.jwt.secret:}") String secret,
            org.springframework.core.env.Environment environment) {
        boolean strict = true;
        for (String profile : environment.getActiveProfiles()) {
            if ("dev".equals(profile) || "test".equals(profile)) {
                strict = false;
                break;
            }
        }
        if (secret == null || secret.isBlank()) {
            if (strict) {
                throw new IllegalStateException(
                        "生产环境必须通过环境变量 JWT_SECRET 提供签名密钥（≥32 字节），拒绝使用内置开发密钥启动");
            }
            LoggerFactory.getLogger(AuthConfig.class).warn(
                    ">>> 未配置 JWT_SECRET，使用开发专用默认密钥（禁止用于生产） <<<");
            secret = "rpm-default-jwt-secret-key-for-development-only-change-in-production-min-32bytes";
        }
        return new JwtTokenProvider(
                secret,
                DEFAULT_ACCESS_TOKEN_VALIDITY_SEC * 1000,
                DEFAULT_REFRESH_TOKEN_VALIDITY_MS,
                "research-project-management");
    }

    /**
     * 密码服务（纯 Java，不依赖 Spring）。
     */
    @Bean
    public PasswordEncoderService passwordEncoderService() {
        return new PasswordEncoderService();
    }

    /**
     * 认证应用服务。
     */
    @Bean
    public AuthenticationService authenticationService(
            IamUserMapper userMapper,
            IamLoginLogMapper loginLogMapper,
            IamRoleMapper roleMapper,
            PasswordEncoderService passwordEncoderService,
            JwtTokenProvider jwtProvider,
            UserRoleQueryPort userRoleQueryPort,
            FunctionPermissionResolver functionPermissionResolver) {
        return new AuthenticationService(
                userMapper, loginLogMapper, roleMapper, passwordEncoderService,
                jwtProvider, userRoleQueryPort, functionPermissionResolver,
                DEFAULT_ACCESS_TOKEN_VALIDITY_SEC);
    }

    /**
     * JWT 认证过滤器（注册到 Spring，自动应用到所有请求）。
     *
     * <p>通过 {@link FilterRegistrationBean} 注册，确保在 DispatcherServlet 之前执行。
     * 白名单路径（/api/v1/auth/login, /actuator/health 等）无需 Token。</p>
     */
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthFilterRegistration(
            JwtTokenProvider jwtProvider,
            FunctionPermissionResolver functionPermissionResolver,
            UserRoleQueryPort userRoleQueryPort) {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
                jwtProvider, functionPermissionResolver, userRoleQueryPort);
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.addUrlPatterns("/api/v1/*");
        registration.setOrder(10);
        return registration;
    }

    /**
     * 审计操作人过滤器：必须在 JWT 认证过滤器之后执行，把登录用户 ID 写入审计上下文。
     *
     * @return 过滤器注册
     */
    @Bean
    public FilterRegistrationBean<AuditActorFilter> auditActorFilterRegistration() {
        FilterRegistrationBean<AuditActorFilter> registration =
                new FilterRegistrationBean<>(new AuditActorFilter());
        registration.addUrlPatterns("/api/v1/*");
        registration.setOrder(20);
        return registration;
    }
}
