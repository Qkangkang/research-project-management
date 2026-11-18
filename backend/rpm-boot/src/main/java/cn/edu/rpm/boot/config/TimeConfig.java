package cn.edu.rpm.boot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * 时间基准装配（Batch-5）。
 *
 * <p>审计切面（{@code AuditAspect}）与文件下载签名（{@code FileAccessService}）统一注入
 * {@link Clock}，禁止在业务代码中散落 {@code Instant.now()}，保证同一请求内时间口径一致、
 * 领域服务可注入固定时钟做边界测试。</p>
 *
 * <p>时区取展示时区 {@code Asia/Shanghai}（技术文档 02 §1 原则 2）。</p>
 */
@Configuration(proxyBeanMethods = false)
public class TimeConfig {

    /**
     * 全局时间基准。
     *
     * @return 使用 {@code Asia/Shanghai} 时区的系统时钟（含毫秒精度）
     */
    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("Asia/Shanghai"));
    }
}
