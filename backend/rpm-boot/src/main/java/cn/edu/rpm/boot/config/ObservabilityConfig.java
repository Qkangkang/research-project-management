package cn.edu.rpm.boot.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 可观测性装配：注册 {@link TraceIdFilter}。
 *
 * <p>必须排在所有业务过滤器（JWT 认证 order=10、审计操作人 order=20）之前，
 * 且拦截全部路径（含 /actuator），保证任何出口响应都携带 TraceID。</p>
 */
@Configuration(proxyBeanMethods = false)
public class ObservabilityConfig {

    @Bean
    public FilterRegistrationBean<TraceIdFilter> traceIdFilterRegistration() {
        FilterRegistrationBean<TraceIdFilter> registration =
                new FilterRegistrationBean<>(new TraceIdFilter());
        registration.addUrlPatterns("/*");
        registration.setOrder(0);
        return registration;
    }
}
