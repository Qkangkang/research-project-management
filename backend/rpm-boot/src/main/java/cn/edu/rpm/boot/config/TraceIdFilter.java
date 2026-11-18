package cn.edu.rpm.boot.config;

import cn.edu.rpm.common.api.Result;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 链路追踪过滤器（RULE-C1；技术文档 06 可观测性）。
 *
 * <p>职责：为每个入站请求确定 TraceID 并写入 SLF4J MDC，供
 * {@code Result.trace_id}、业务日志、审计事件与 Outbox 事件统一取用；
 * 同时通过响应头 {@code X-Trace-Id} 回传给调用方，前后端可凭同一 ID
 * 在日志与审计表中定位一次请求。</p>
 *
 * <p>取值规则：优先采纳调用方透传的 {@code X-Trace-Id}（白名单校验，
 * 防止日志注入/超长攻击），否则服务端生成。</p>
 */
public class TraceIdFilter implements Filter {

    /** TraceID 透传请求/响应头名称。 */
    public static final String TRACE_HEADER = "X-Trace-Id";

    /** 合法 TraceID：8-64 位字母/数字/点/下划线/连字符（拒绝换行等日志注入字符与超长值）。 */
    private static final Pattern SAFE_TRACE_ID = Pattern.compile("^[A-Za-z0-9._-]{8,64}$");

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        String incoming = req.getHeader(TRACE_HEADER);
        String traceId = isSafe(incoming) ? incoming : generate();
        MDC.put(Result.MDC_TRACE_KEY, traceId);
        resp.setHeader(TRACE_HEADER, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(Result.MDC_TRACE_KEY);
        }
    }

    private static boolean isSafe(String traceId) {
        return traceId != null && SAFE_TRACE_ID.matcher(traceId).matches();
    }

    private static String generate() {
        return "srv-" + UUID.randomUUID().toString().replace("-", "");
    }
}
