package cn.edu.rpm.boot.config;

import cn.edu.rpm.iam.application.context.AuthContextHolder;
import cn.edu.rpm.iam.application.dto.AuthContext;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

import java.io.IOException;

/**
 * 审计操作人传播过滤器。
 *
 * <p>在 JWT 认证过滤器写入 {@link AuthContextHolder} 之后执行，将当前登录用户 ID
 * 同步到 {@link AuditUserContext}，供 {@link AuditMetaObjectHandler} 填充
 * {@code created_by} / {@code updated_by}（数据库列为 {@code uuid}，不能写账号名或
 * {@code system} 之类的非 UUID 占位值）。请求结束必须清理线程上下文。</p>
 */
public class AuditActorFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        AuthContext auth = AuthContextHolder.current();
        if (auth != null && auth.userId() != null) {
            AuditUserContext.set(auth.userId().toString());
        }
        try {
            chain.doFilter(request, response);
        } finally {
            AuditUserContext.clear();
        }
    }
}
