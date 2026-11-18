package cn.edu.rpm.workflow.infrastructure.config;

import cn.edu.rpm.workflow.domain.port.TemplateConditionMatcher;
import cn.edu.rpm.workflow.domain.port.WorkflowTemplateQueryPort;
import cn.edu.rpm.workflow.domain.service.WorkflowTemplateResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 工作流领域服务装配（T-WF-012）。
 *
 * <p>{@code WorkflowTemplateResolver} 为纯 Java 领域服务（不引 Spring 注解），
 * 在此显式装配；端口实现由组件扫描提供
 * （{@code WorkflowTemplateQueryAdapter} / {@code JacksonTemplateConditionMatcher}）。</p>
 */
@Configuration(proxyBeanMethods = false)
public class WorkflowDomainConfig {

    /**
     * 审批模板路由服务。
     *
     * @param templateQueryPort 模板查询端口
     * @param conditionMatcher  条件匹配器
     * @return 模板路由服务
     */
    @Bean
    public WorkflowTemplateResolver workflowTemplateResolver(WorkflowTemplateQueryPort templateQueryPort,
                                                             TemplateConditionMatcher conditionMatcher) {
        return new WorkflowTemplateResolver(templateQueryPort, conditionMatcher);
    }
}
