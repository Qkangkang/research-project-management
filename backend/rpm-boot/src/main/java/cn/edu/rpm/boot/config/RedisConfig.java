package cn.edu.rpm.boot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis 装配（T-ENV-006）。
 *
 * <p>一期仅声明 {@link StringRedisTemplate}（权限快照、报表快照等 JSON 值
 * 由业务侧自行序列化）。连接地址/超时经 {@code spring.data.redis.*} 配置，
 * 必须带超时（C-CHECK）。</p>
 *
 * <p>使用约定（RULE-CA1/CA2）：业务缓存必须设置 TTL，禁止永不过期；
 * Redis 不可用时业务侧必须降级回源 PostgreSQL，不得返回错误或放行匿名访问。</p>
 */
@Configuration(proxyBeanMethods = false)
public class RedisConfig {

    /**
     * 字符串模板：key/value/hashKey 均为 String 序列化，便于跨服务核对缓存内容。
     *
     * @param connectionFactory Lettuce 连接工厂（Spring Boot 自动装配，含超时配置）
     * @return 字符串模板实例
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}
