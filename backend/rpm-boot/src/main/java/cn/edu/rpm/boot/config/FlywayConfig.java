package cn.edu.rpm.boot.config;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Flyway 装配（T-ENV-004）。
 *
 * <p>迁移脚本位于 {@code classpath:db/migration}（当前 V01 基线 74 张表；
 * V02～V07 由 Batch-2 陆续补充）。启动时执行 {@code migrate} 并输出已应用脚本数，
 * 便于上线门禁核对 {@code flyway_schema_history}。</p>
 *
 * <p>策略约定：不开启 {@code clean}（任何环境禁止清库）；校验失败直接阻断启动
 * （{@code validate-on-migrate: true}），避免带病运行。</p>
 */
@Configuration(proxyBeanMethods = false)
public class FlywayConfig {

    private static final Logger LOG = LoggerFactory.getLogger(FlywayConfig.class);

    /**
     * 自定义迁移策略：migrate 后记录已应用脚本数作为启动日志锚点。
     *
     * @return 迁移策略（启动阶段由 Spring Boot 自动执行）
     */
    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return (Flyway flyway) -> {
            flyway.migrate();
            int appliedCount = flyway.info().applied().length;
            LOG.info("Flyway 迁移完成，已应用脚本数={}（含 V01 基线 74 张业务表）", appliedCount);
        };
    }
}
