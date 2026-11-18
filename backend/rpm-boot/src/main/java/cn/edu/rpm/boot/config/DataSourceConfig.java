package cn.edu.rpm.boot.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * PostgreSQL 数据源与 HikariCP 连接池装配（T-ENV-003）。
 *
 * <p>显式声明 {@link HikariDataSource} Bean：URL/凭据取自 {@code spring.datasource.*}
 * （环境变量注入），连接池参数经 {@code spring.datasource.hikari.*} 绑定
 * （获取连接超时 30s、校验超时 5s，满足 RULE-DB4 禁止无超时数据库操作）。</p>
 */
@Configuration(proxyBeanMethods = false)
public class DataSourceConfig {

    /**
     * 构建应用主数据源（HikariCP）。
     *
     * <p>池参数（max-pool-size、connection-timeout 等）由
     * {@code spring.datasource.hikari.*} 配置项直接绑定到返回的 {@link HikariDataSource}。</p>
     *
     * @param properties Spring Boot 从 {@code spring.datasource.*} 装配的基础属性
     * @return 应用级唯一数据源
     */
    @Bean
    @ConfigurationProperties("spring.datasource.hikari")
    public DataSource dataSource(DataSourceProperties properties) {
        return DataSourceBuilder.create(properties.getClassLoader())
                .type(HikariDataSource.class)
                .driverClassName(properties.determineDriverClassName())
                .url(properties.determineUrl())
                .username(properties.determineUsername())
                .password(properties.determinePassword())
                .build();
    }
}
