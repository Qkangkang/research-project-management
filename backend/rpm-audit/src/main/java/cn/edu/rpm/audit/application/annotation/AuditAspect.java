package cn.edu.rpm.audit.application.annotation;

import cn.edu.rpm.audit.application.service.AuditDigestService;
import cn.edu.rpm.audit.domain.model.AuditEvent;
import cn.edu.rpm.audit.domain.service.AuditRecorder;
import cn.edu.rpm.common.api.Result;
import cn.edu.rpm.iam.application.context.AuthContextHolder;
import cn.edu.rpm.iam.application.dto.AuthContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Objects;
import java.util.UUID;

/**
 * 审计切面（T-AUD-003；技术文档 03 §4.2）。
 *
 * <p>环绕标注 {@link Audited} 的方法：</p>
 * <ul>
 *   <li>方法正常返回后组装 {@link AuditEvent} 并调用 {@link AuditRecorder#record}；</li>
 *   <li>方法抛出异常时记录告警并原样抛出，不写审计——业务事务回滚后不应留下"未发生"的日志；</li>
 *   <li>{@code beforeDigest}/{@code afterDigest} 分别取方法参数与返回值摘要，不落明文；</li>
 *   <li>{@code objectId} 取方法参数中第一个 {@link UUID}，否则取返回值中的 {@link UUID}，
 *       都没有则留空（系统级动作）；</li>
 *   <li>操作人取 {@link AuthContextHolder} 当前上下文；仅有一个委托人时记录
 *       {@code onBehalfOf}，多个委托人不猜测身份，留空由业务显式审计。</li>
 * </ul>
 *
 * <p>HTTP 元数据约定：接入层过滤器将 TraceID、客户端 IP、User-Agent 写入
 * MDC（key 分别为 {@code traceId}、{@link #MDC_CLIENT_IP}、{@link #MDC_USER_AGENT}），
 * 本切面读取后随审计落库。TraceID 缺失时使用 {@code untracked} 标记便于排查。</p>
 */
@Aspect
@Component
public class AuditAspect {

    private static final Logger LOG = LoggerFactory.getLogger(AuditAspect.class);

    /** MDC 中的客户端 IP key（接入层过滤器写入）。 */
    public static final String MDC_CLIENT_IP = "clientIp";

    /** MDC 中的 User-Agent key（接入层过滤器写入）。 */
    public static final String MDC_USER_AGENT = "userAgent";

    /** TraceID 缺失时的占位值（DDL {@code audit_log.trace_id} 非空）。 */
    public static final String UNTRACKED_TRACE_ID = "untracked";

    private final AuditRecorder auditRecorder;

    private final AuditDigestService auditDigestService;

    private final Clock clock;

    public AuditAspect(AuditRecorder auditRecorder, AuditDigestService auditDigestService, Clock clock) {
        this.auditRecorder = Objects.requireNonNull(auditRecorder, "auditRecorder 不能为空");
        this.auditDigestService = Objects.requireNonNull(auditDigestService, "auditDigestService 不能为空");
        this.clock = Objects.requireNonNull(clock, "clock 不能为空");
    }

    /**
     * 关键方法环绕审计。
     *
     * @param joinPoint 被拦截方法
     * @param audited   方法上的审计声明
     * @return 原方法返回值
     * @throws Throwable 原方法异常或审计写入异常（均向上传播）
     */
    @Around("@annotation(audited)")
    public Object around(ProceedingJoinPoint joinPoint, Audited audited) throws Throwable {
        Object[] args = joinPoint.getArgs();
        String beforeDigest = auditDigestService.digest(args);
        String traceId = currentTraceId();
        Object result;
        try {
            result = joinPoint.proceed();
        } catch (Throwable ex) {
            LOG.warn("审计标注方法执行失败，不写审计 method={} action={} traceId={}",
                    joinPoint.getSignature().toShortString(), audited.action(), traceId);
            throw ex;
        }
        AuthContext authContext = AuthContextHolder.current();
        Instant occurredAt = clock.instant();
        AuditEvent event = new AuditEvent(
                traceId,
                authContext == null ? null : authContext.userId(),
                onBehalfOf(authContext),
                audited.action(),
                audited.objectType(),
                resolveObjectId(args, result),
                null,
                beforeDigest,
                auditDigestService.digest(result),
                null,
                MDC.get(MDC_CLIENT_IP),
                MDC.get(MDC_USER_AGENT),
                occurredAt);
        auditRecorder.record(event);
        return result;
    }

    private static String currentTraceId() {
        String traceId = MDC.get(Result.MDC_TRACE_KEY);
        return traceId == null || traceId.isBlank() ? UNTRACKED_TRACE_ID : traceId;
    }

    private static UUID onBehalfOf(AuthContext authContext) {
        if (authContext == null || authContext.delegatedFromUserIds().size() != 1) {
            return null;
        }
        Iterator<UUID> iterator = authContext.delegatedFromUserIds().iterator();
        return iterator.next();
    }

    private static UUID resolveObjectId(Object[] args, Object result) {
        if (args != null) {
            UUID fromArgs = Arrays.stream(args)
                    .filter(UUID.class::isInstance)
                    .map(UUID.class::cast)
                    .findFirst()
                    .orElse(null);
            if (fromArgs != null) {
                return fromArgs;
            }
        }
        return result instanceof UUID uuid ? uuid : null;
    }
}
