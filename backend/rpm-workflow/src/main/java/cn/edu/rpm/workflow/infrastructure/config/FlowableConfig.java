package cn.edu.rpm.workflow.infrastructure.config;

import org.flowable.engine.RepositoryService;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.spring.boot.EngineConfigurationConfigurer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Flowable 7.2.0 流程引擎配置（T-WF-001；技术文档 06 §7.2、README §4 V03）。
 *
 * <p>配置口径：</p>
 * <ul>
 *   <li>引擎表独立存放于 {@code flowable} schema；ACT_* 表只允许由 Flyway
 *       （V03 + V09 对齐脚本）管理；</li>
 *   <li>{@code databaseSchemaUpdate=false}：禁止引擎启动时自动建表/改表，
 *       与「数据库变更只走 Flyway」的硬约束一致；</li>
 *   <li>引擎启动时依据 {@code ACT_GE_PROPERTY.schema.version} 校验版本，
 *       V09 已写入与 7.2.0 官方脚本一致的版本元数据。</li>
 * </ul>
 *
 * <p>说明：技术文档 06 §7.2 写作 {@code spring.flowable.*}，Flowable Spring Boot
 * 实际属性前缀为 {@code flowable.*}（见 {@code FlowableProperties}）；两者意图一致，
 * 且本类以 Java 配置再显式设置一次，避免属性前缀差异导致隔离失效。</p>
 */
@Configuration
public class FlowableConfig implements EngineConfigurationConfigurer<SpringProcessEngineConfiguration> {

    private static final Logger log = LoggerFactory.getLogger(FlowableConfig.class);

    /** Flowable 引擎独立 schema（V03 / V09）。 */
    public static final String FLOWABLE_SCHEMA = "flowable";

    /** 测试流程定义部署名；重复启动时据此避免重复部署。 */
    public static final String TEST_PROCESS_DEPLOYMENT_NAME = "rpm-test-approval";

    /** 测试流程定义类路径（两节点：形式审查 → 决策，供 Batch-7 集成测试使用）。 */
    public static final String TEST_PROCESS_RESOURCE = "process-definitions/test-approval.bpmn20.xml";

    /** 支出审批流程定义部署名；重复启动时据此避免重复部署。 */
    public static final String EXPENSE_PROCESS_DEPLOYMENT_NAME = "rpm-expense-approval";

    /** 支出审批流程定义类路径（系统标准流程，所有环境均需部署）。 */
    public static final String EXPENSE_PROCESS_RESOURCE = "process-definitions/expense-approval.bpmn20.xml";

    /**
     * 将自动装配的流程引擎指向独立 schema，并关闭启动自动建表。
     *
     * @param configuration Flowable 自动装配的 Spring 流程引擎配置
     */
    @Override
    public void configure(SpringProcessEngineConfiguration configuration) {
        configuration.setDatabaseSchema(FLOWABLE_SCHEMA);
        configuration.setDatabaseSchemaUpdate(SpringProcessEngineConfiguration.DB_SCHEMA_UPDATE_FALSE);
    }

    /**
     * 部署用于开发/联调的测试流程定义（{@code rpm.workflow.test-process.deploy-enabled=true} 时生效）。
     *
     * <p>仅在开发/测试环境开启；生产环境保持关闭，避免测试流程进入
     * {@code ACT_RE_DEPLOYMENT}。部署前按部署名查重，应用重启不会重复部署。</p>
     *
     * @param repositoryService Flowable 仓储服务
     * @return 启动后执行的部署任务
     */
    @Bean
    @ConditionalOnProperty(prefix = "rpm.workflow.test-process", name = "deploy-enabled",
            havingValue = "true")
    public ApplicationRunner testProcessDefinitionDeployer(RepositoryService repositoryService) {
        return args -> {
            long existing = repositoryService.createDeploymentQuery()
                    .deploymentName(TEST_PROCESS_DEPLOYMENT_NAME)
                    .count();
            if (existing > 0) {
                log.info("测试审批流程已部署，跳过重复部署: deploymentName={}", TEST_PROCESS_DEPLOYMENT_NAME);
                return;
            }
            String deploymentId = repositoryService.createDeployment()
                    .name(TEST_PROCESS_DEPLOYMENT_NAME)
                    .addClasspathResource(TEST_PROCESS_RESOURCE)
                    .deploy()
                    .getId();
            log.info("测试审批流程部署完成: deploymentId={}, resource={}", deploymentId, TEST_PROCESS_RESOURCE);
        };
    }

    /**
     * 部署系统标准支出审批流程定义。
     *
     * @param repositoryService Flowable 仓储服务
     * @return 启动后执行的部署任务
     */
    @Bean
    public ApplicationRunner expenseProcessDefinitionDeployer(RepositoryService repositoryService) {
        return args -> {
            long existing = repositoryService.createDeploymentQuery()
                    .deploymentName(EXPENSE_PROCESS_DEPLOYMENT_NAME)
                    .count();
            if (existing > 0) {
                log.info("支出审批流程已部署，跳过重复部署: deploymentName={}",
                        EXPENSE_PROCESS_DEPLOYMENT_NAME);
                return;
            }
            String deploymentId = repositoryService.createDeployment()
                    .name(EXPENSE_PROCESS_DEPLOYMENT_NAME)
                    .addClasspathResource(EXPENSE_PROCESS_RESOURCE)
                    .deploy()
                    .getId();
            log.info("支出审批流程部署完成: deploymentId={}, resource={}",
                    deploymentId, EXPENSE_PROCESS_RESOURCE);
        };
    }
}
