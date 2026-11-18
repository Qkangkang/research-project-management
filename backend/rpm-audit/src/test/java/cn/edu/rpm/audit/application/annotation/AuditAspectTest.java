package cn.edu.rpm.audit.application.annotation;

import cn.edu.rpm.audit.application.service.AuditDigestService;
import cn.edu.rpm.audit.domain.model.AuditEvent;
import cn.edu.rpm.audit.domain.service.AuditRecorder;
import cn.edu.rpm.common.api.Result;
import cn.edu.rpm.iam.application.context.AuthContextHolder;
import cn.edu.rpm.iam.application.dto.AuthContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.lang.annotation.Annotation;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link AuditAspect} 纯单元测试（T-AUD-003 先行测试）。
 *
 * <p>不启动 Spring 容器：直接以 Mockito 模拟 {@link ProceedingJoinPoint}，
 * 断言标注 {@link Audited} 的方法执行后产生审计记录，动作/对象类型来自注解，
 * 操作人/代理身份来自 {@link AuthContextHolder}，TraceID/IP/UserAgent 来自 MDC，
 * 前后摘要分别对应方法参数与返回值。</p>
 */
@ExtendWith(MockitoExtension.class)
class AuditAspectTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID DELEGATOR_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID OBJECT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final Instant NOW = Instant.parse("2026-11-17T05:00:00Z");

    @Mock
    private AuditRecorder auditRecorder;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Captor
    private ArgumentCaptor<AuditEvent> eventCaptor;

    private AuditDigestService digestService;

    private AuditAspect aspect;

    @BeforeEach
    void setUp() {
        digestService = new AuditDigestService();
        aspect = new AuditAspect(auditRecorder, digestService,
                Clock.fixed(NOW, ZoneOffset.ofHours(8)));
        AuthContextHolder.set(new AuthContext(USER_ID, null, "EMP-0001",
                Set.of("application:submit"), Set.of(DELEGATOR_ID), NOW));
        MDC.put(Result.MDC_TRACE_KEY, "trace-2026-0004");
        MDC.put(AuditAspect.MDC_CLIENT_IP, "10.0.0.11");
        MDC.put(AuditAspect.MDC_USER_AGENT, "Mozilla/5.0 (audit)");
    }

    @AfterEach
    void tearDown() {
        AuthContextHolder.clear();
        MDC.clear();
    }

    @Test
    void annotatedMethodExecutionProducesAuditWithCorrectDigests() throws Throwable {
        Object[] args = new Object[]{OBJECT_ID, "SUBMITTED"};
        when(joinPoint.getArgs()).thenReturn(args);
        when(joinPoint.proceed()).thenReturn("application-version-1");

        Object result = aspect.around(joinPoint, auditedAnnotation());

        assertThat(result).isEqualTo("application-version-1");
        verify(auditRecorder).record(eventCaptor.capture());
        AuditEvent event = eventCaptor.getValue();
        assertThat(event.action()).isEqualTo("APPLICATION_SUBMIT");
        assertThat(event.objectType()).isEqualTo("APPLICATION");
        assertThat(event.traceId()).isEqualTo("trace-2026-0004");
        assertThat(event.actorId()).isEqualTo(USER_ID);
        assertThat(event.onBehalfOf()).isEqualTo(DELEGATOR_ID);
        assertThat(event.objectId()).isEqualTo(OBJECT_ID);
        assertThat(event.objectVersion()).isNull();
        assertThat(event.beforeDigest()).isEqualTo(digestService.digest(args));
        assertThat(event.afterDigest()).isEqualTo(digestService.digest("application-version-1"));
        assertThat(event.beforeDigest()).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(event.afterDigest()).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(event.ip()).isEqualTo("10.0.0.11");
        assertThat(event.userAgent()).isEqualTo("Mozilla/5.0 (audit)");
        assertThat(event.occurredAt()).isEqualTo(NOW);
    }

    @Test
    void businessExceptionPropagatesWithoutWritingAudit() throws Throwable {
        org.aspectj.lang.Signature signature = org.mockito.Mockito.mock(org.aspectj.lang.Signature.class);
        when(signature.toShortString()).thenReturn("SampleAppService.submit(..)");
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(new Object[]{OBJECT_ID});
        when(joinPoint.proceed()).thenThrow(new IllegalStateException("workflow unavailable"));

        assertThatThrownBy(() -> aspect.around(joinPoint, auditedAnnotation()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("workflow unavailable");
        verifyNoInteractions(auditRecorder);
    }

    @Test
    void missingAuthContextRecordsNullActorAndMissingTraceUsesUntrackedMarker() throws Throwable {
        AuthContextHolder.clear();
        MDC.remove(Result.MDC_TRACE_KEY);
        MDC.remove(AuditAspect.MDC_CLIENT_IP);
        MDC.remove(AuditAspect.MDC_USER_AGENT);
        when(joinPoint.getArgs()).thenReturn(new Object[]{"payload"});
        when(joinPoint.proceed()).thenReturn(null);

        aspect.around(joinPoint, auditedAnnotation());

        verify(auditRecorder).record(eventCaptor.capture());
        AuditEvent event = eventCaptor.getValue();
        assertThat(event.actorId()).isNull();
        assertThat(event.onBehalfOf()).isNull();
        assertThat(event.traceId()).isEqualTo("untracked");
        assertThat(event.objectId()).isNull();
        assertThat(event.afterDigest()).isNull();
        assertThat(event.ip()).isNull();
        assertThat(event.userAgent()).isNull();
    }

    @Test
    void multipleDelegatorsDoNotGuessOnBehalfOfIdentity() throws Throwable {
        UUID secondDelegator = UUID.fromString("44444444-4444-4444-4444-444444444444");
        AuthContextHolder.set(new AuthContext(USER_ID, null, "EMP-0001",
                Set.of(), Set.of(DELEGATOR_ID, secondDelegator), NOW));
        when(joinPoint.getArgs()).thenReturn(new Object[]{"x"});
        when(joinPoint.proceed()).thenReturn("ok");

        aspect.around(joinPoint, auditedAnnotation());

        verify(auditRecorder).record(eventCaptor.capture());
        assertThat(eventCaptor.getValue().onBehalfOf()).isNull();
    }

    @Test
    void auditedAnnotationIsRuntimeVisibleOnMethods() throws Exception {
        Annotation annotation = AuditAspectTest.class
                .getDeclaredMethod("sampleAnnotatedMethod", UUID.class, String.class).getAnnotation(Audited.class);

        assertThat(annotation).isInstanceOf(Audited.class);
        Audited audited = (Audited) annotation;
        assertThat(audited.action()).isEqualTo("APPLICATION_SUBMIT");
        assertThat(audited.objectType()).isEqualTo("APPLICATION");
    }

    @Test
    void recorderFailureIsNotSwallowed() throws Throwable {
        when(joinPoint.getArgs()).thenReturn(new Object[]{"x"});
        when(joinPoint.proceed()).thenReturn("ok");
        org.mockito.Mockito.doThrow(new org.springframework.dao.DataAccessResourceFailureException("audit down"))
                .when(auditRecorder).record(org.mockito.ArgumentMatchers.any());

        assertThatThrownBy(() -> aspect.around(joinPoint, auditedAnnotation()))
                .isInstanceOf(org.springframework.dao.DataAccessResourceFailureException.class)
                .hasMessageContaining("audit down");
        verify(auditRecorder, never()).recordNonCritical(org.mockito.ArgumentMatchers.any());
    }

    private static Audited auditedAnnotation() throws NoSuchMethodException {
        return AuditAspectTest.class.getDeclaredMethod("sampleAnnotatedMethod", UUID.class, String.class).getAnnotation(Audited.class);
    }

    /** 测试用标注方法，仅用于读取注解元数据。 */
    @Audited(action = "APPLICATION_SUBMIT", objectType = "APPLICATION")
    void sampleAnnotatedMethod(UUID id, String payload) {
        // 测试夹具：不执行任何逻辑
    }
}
