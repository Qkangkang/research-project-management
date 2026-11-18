package cn.edu.rpm.boot.config;

import cn.edu.rpm.audit.application.service.AuditDigestService;
import cn.edu.rpm.audit.domain.port.AuditLogAppendPort;
import cn.edu.rpm.audit.domain.port.AuditOutboxPort;
import cn.edu.rpm.audit.domain.service.AuditRecorder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 审计领域服务装配（Batch-5 / T-AUD-002、T-AUD-003）。
 *
 * <p>{@code AuditRecorder} / {@code AuditDigestService} 为纯 Java 类（不引 Spring 注解），
 * 统一在本配置装配；审计只追加端口与 outbox 端口由组件扫描注入
 * （{@code AuditLogRepositoryImpl} / {@code AuditOutboxRepositoryImpl}）。</p>
 */
@Configuration(proxyBeanMethods = false)
public class AuditConfig {

    /**
     * 审计记录器（关键动作同事务直写 + 非关键动作本地 outbox）。
     *
     * @param auditLogAppendPort 审计日志只追加端口
     * @param auditOutboxPort    非关键审计 outbox 端口
     * @return 审计记录器
     */
    @Bean
    public AuditRecorder auditRecorder(AuditLogAppendPort auditLogAppendPort,
                                       AuditOutboxPort auditOutboxPort) {
        return new AuditRecorder(auditLogAppendPort, auditOutboxPort);
    }

    /**
     * 审计摘要服务（参数/返回值 SHA-256，不落明文）。
     *
     * @return 摘要服务
     */
    @Bean
    public AuditDigestService auditDigestService() {
        return new AuditDigestService();
    }
}
