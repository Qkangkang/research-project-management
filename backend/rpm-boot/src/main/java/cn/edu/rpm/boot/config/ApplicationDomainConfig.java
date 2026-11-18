package cn.edu.rpm.boot.config;

import cn.edu.rpm.application.domain.service.ApplyFormTemplateResolver;
import cn.edu.rpm.application.domain.service.ApplicationContentHasher;
import cn.edu.rpm.application.infrastructure.repository.ApplyFormTemplateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 申报领域服务装配（Batch-9；T-APP-013 / T-APP-014）。
 *
 * <p>领域服务保持纯 Java（不标注 {@code @Service}，不依赖 Spring 生命周期），
 * 由 rpm-boot 统一装配为 Bean，便于纯单测直接构造。</p>
 */
@Configuration
public class ApplicationDomainConfig {

    /**
     * 表单模板解析与校验服务。
     *
     * @param templateRepository 表单模板仓储
     * @param objectMapper       JSON 解析器
     * @return 表单模板解析器
     */
    @Bean
    public ApplyFormTemplateResolver applyFormTemplateResolver(
            ApplyFormTemplateRepository templateRepository, ObjectMapper objectMapper) {
        return new ApplyFormTemplateResolver(templateRepository, objectMapper);
    }

    /**
     * 申报内容规范化与摘要服务。
     *
     * @return 内容摘要器
     */
    @Bean
    public ApplicationContentHasher applicationContentHasher() {
        return new ApplicationContentHasher();
    }
}
