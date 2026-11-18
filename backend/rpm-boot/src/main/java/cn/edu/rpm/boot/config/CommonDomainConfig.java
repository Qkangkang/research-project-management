package cn.edu.rpm.boot.config;

import cn.edu.rpm.common.util.CanonicalJsonHasher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 跨模块公共工具装配。
 *
 * <p>{@link CanonicalJsonHasher} 位于 rpm-common，为无状态纯 Java 类（不标注 Spring 注解），
 * 由项目基线快照、延期证据序列化、成果版本哈希、资金等模块共用；在此统一装配为单一 Bean，
 * 禁止在各领域装配类（ProjectDomainConfig、OutputDomainConfig 等）中重复声明，
 * 避免 {@code BeanDefinitionOverrideException}。</p>
 */
@Configuration(proxyBeanMethods = false)
public class CommonDomainConfig {

    /**
     * 规范化 JSON 与 SHA-256 摘要工具（全模块共享，无状态，可安全单例）。
     *
     * @return 规范化 JSON 工具
     */
    @Bean
    public CanonicalJsonHasher canonicalJsonHasher() {
        return new CanonicalJsonHasher();
    }
}
