package cn.edu.rpm.boot.config;

import cn.edu.rpm.common.domain.service.NumberGenerator;
import cn.edu.rpm.project.domain.service.ProjectNumberGenerator;
import cn.edu.rpm.project.infrastructure.repository.ProjectRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 项目领域服务装配（Batch-12；T-PRJ-010 / T-PRJ-018 / T-PRJ-016）。
 *
 * <p>{@link ProjectNumberGenerator} 为纯 Java 类（不标注 Spring 注解），在此装配；
 * 公共工具 {@code CanonicalJsonHasher} 由 {@link CommonDomainConfig} 统一装配。
 * 其余项目领域服务（状态机、成员、风险、里程碑、延期申请、查询、立项创建）
 * 以 {@code @Service} 标注并由 {@code scanBasePackages = "cn.edu.rpm"} 扫描。</p>
 */
@Configuration(proxyBeanMethods = false)
public class ProjectDomainConfig {

    /**
     * 项目编号生成器（{@code KY-{年}-{6位}}，生成后查重）。
     *
     * @param numberGenerator   通用编号生成器
     * @param projectRepository 项目仓储（查重用）
     * @return 项目编号生成器
     */
    @Bean
    public ProjectNumberGenerator projectNumberGenerator(NumberGenerator numberGenerator,
                                                         ProjectRepository projectRepository) {
        return new ProjectNumberGenerator(numberGenerator, projectRepository);
    }
}
