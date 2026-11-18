package cn.edu.rpm.boot.config;

import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * 业务 SQL 的 schema 守卫。
 *
 * <p>Flowable 在打开自身 SqlSession 时会执行 {@code connection.setSchema("flowable")}，
 * 且不会在归还连接前恢复。Hikari 复用该连接后，业务 Mapper 的未限定表名
 * （如 {@code project}）会按 {@code flowable} schema 解析而报“表不存在”。
 * 本拦截器在每条业务 SQL 执行前把连接 schema 复位为 {@code public}，
 * 与 Flowable 的独立 schema 隔离共存。</p>
 */
@Intercepts({
        @Signature(type = Executor.class, method = "update",
                args = {MappedStatement.class, Object.class}),
        @Signature(type = Executor.class, method = "query",
                args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
        @Signature(type = Executor.class, method = "query",
                args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class,
                        CacheKey.class, BoundSql.class})
})
public class MyBatisSchemaGuardInterceptor implements Interceptor {

    private static final String BUSINESS_SCHEMA = "public";

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        if (invocation.getTarget() instanceof Executor executor && executor.getTransaction() != null) {
            resetSchema(executor.getTransaction().getConnection());
        }
        return invocation.proceed();
    }

    private void resetSchema(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            if (!BUSINESS_SCHEMA.equalsIgnoreCase(connection.getSchema())) {
                connection.setSchema(BUSINESS_SCHEMA);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("重置业务 SQL schema 失败", e);
        }
    }
}
