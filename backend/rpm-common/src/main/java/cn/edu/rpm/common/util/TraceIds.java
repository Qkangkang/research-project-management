package cn.edu.rpm.common.util;

import cn.edu.rpm.common.api.Result;
import org.slf4j.MDC;

/**
 * TraceID 读取工具（RULE-C1；技术文档 06 可观测性）。
 *
 * <p>接入层过滤器把 TraceID 写入 MDC（key 见 {@link Result#MDC_TRACE_KEY}），
 * 应用服务写审计/Outbox 时统一经本类取值；缺失时使用 {@code untracked} 占位
 * （与 AuditAspect 同一口径），保证非空审计字段可落库。</p>
 */
public final class TraceIds {

    /** TraceID 缺失时的占位值。 */
    public static final String UNTRACKED = "untracked";

    private TraceIds() {
    }

    /**
     * 读取当前线程 TraceID。
     *
     * @return MDC 中的 TraceID；缺失或空白返回 {@link #UNTRACKED}
     */
    public static String currentOrUntracked() {
        String traceId = MDC.get(Result.MDC_TRACE_KEY);
        return traceId == null || traceId.isBlank() ? UNTRACKED : traceId;
    }
}
