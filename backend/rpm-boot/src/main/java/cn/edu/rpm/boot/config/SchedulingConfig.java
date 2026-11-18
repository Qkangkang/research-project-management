package cn.edu.rpm.boot.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 后台任务调度装配。
 *
 * <p>形态 A 单体内以 Spring {@code @Scheduled} 承载轻量后台任务
 * （审计 Outbox 补写、物化视图刷新等），与主应用同进程部署；
 * 未引入 MQ/独立任务进程是明确的技术决策（见设计决策说明）。</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class SchedulingConfig {
}
