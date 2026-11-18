package cn.edu.rpm.boot.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 装配（T-ENV-005）。
 *
 * <p>Mapper 扫描范围限定 {@code cn.edu.rpm} 根包下标注
 * {@link Mapper} 的接口，避免误注册非 Mapper 接口。</p>
 *
 * <p>内置插件：</p>
 * <ul>
 *   <li>PostgreSQL 分页拦截器 — 支撑列表查询分页；</li>
 *   <li>乐观锁拦截器 — 配合实体 {@code @Version} 字段（BaseEntity，Batch-2 引入）
 *       实现并发版本控制（RULE-DB1 的乐观锁出口）。</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@MapperScan(basePackages = "cn.edu.rpm", annotationClass = Mapper.class)
public class MyBatisPlusConfig {

    /**
     * MyBatis-Plus 拦截器链：分页 + 乐观锁。
     *
     * @return 拦截器实例
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.POSTGRE_SQL));
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }

    /**
     * 业务 SQL schema 守卫：隔离 Flowable 对连接 schema 的副作用。
     *
     * @return MyBatis 拦截器
     */
    @Bean
    public Interceptor myBatisSchemaGuardInterceptor() {
        return new MyBatisSchemaGuardInterceptor();
    }
}
