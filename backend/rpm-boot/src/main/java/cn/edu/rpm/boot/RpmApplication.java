package cn.edu.rpm.boot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 科研项目全生命周期管理系统启动入口（形态 A 模块化单体）。
 *
 * <p>{@code scanBasePackages} 必须覆盖根包 {@code cn.edu.rpm}：
 * 各业务模块（rpm-iam、rpm-application 等）的 Bean 位于 {@code cn.edu.rpm.{module}} 包下，
 * 而本类位于 {@code cn.edu.rpm.boot}，不显式声明则默认只扫描 boot 包。</p>
 *
 * <p>本地启动：{@code ./mvnw spring-boot:run}（需先启动 PostgreSQL 与 Redis）；
 * 健康检查：{@code GET /actuator/health}。</p>
 */
@SpringBootApplication(scanBasePackages = "cn.edu.rpm")
public class RpmApplication {

    /**
     * 应用入口。
     *
     * @param args Spring Boot 命令行参数（如 {@code --spring.profiles.active=prod}）
     */
    public static void main(String[] args) {
        SpringApplication.run(RpmApplication.class, args);
    }
}
