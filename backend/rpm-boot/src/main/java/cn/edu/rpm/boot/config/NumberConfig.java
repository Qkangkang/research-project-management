package cn.edu.rpm.boot.config;

import cn.edu.rpm.common.domain.port.NumberRuleQueryPort;
import cn.edu.rpm.common.domain.port.SequenceValuePort;
import cn.edu.rpm.common.domain.service.NumberGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * 编号生成装配（Batch-9 / T-APP-009）。
 *
 * <p>{@code NumberGenerator} 为纯 Java 领域服务（不引 Spring 注解），在此装配；
 * 规则查询与序列取值端口由 {@code rpm-boot} 基础设施适配器提供。</p>
 */
@Configuration(proxyBeanMethods = false)
public class NumberConfig {

    /**
     * 业务编号生成器。
     *
     * @param numberRuleQueryPort 编号规则查询端口
     * @param sequenceValuePort   数据库序列取值端口
     * @param clock               时间基准（决定日期段）
     * @return 编号生成器
     */
    @Bean
    public NumberGenerator numberGenerator(NumberRuleQueryPort numberRuleQueryPort,
                                           SequenceValuePort sequenceValuePort,
                                           Clock clock) {
        return new NumberGenerator(numberRuleQueryPort, sequenceValuePort, clock);
    }
}
