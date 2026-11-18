package cn.edu.rpm.audit.domain.service;

import cn.edu.rpm.audit.domain.model.AuditEvent;
import cn.edu.rpm.audit.domain.port.AuditLogAppendPort;
import cn.edu.rpm.audit.domain.port.AuditOutboxPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * {@link AuditRecorder} 纯单元测试（T-AUD-002 先行测试；TC-IAM-019 / TC-IAM-021 / TC-IAM-022）。
 *
 * <p>断言口径：</p>
 * <ul>
 *   <li>关键动作：审计字段完整落入 {@code audit_log}（actor/trace/action/object/版本/前后摘要/IP/时间）；</li>
 *   <li>关键审计失败：异常向上传播，不吞异常，使调用方业务事务整体回滚（TC-IAM-022）；</li>
 *   <li>非关键动作：只写 {@code audit_outbox}，不直写 {@code audit_log}；</li>
 *   <li>事务传播：记录器不声明独立事务（无 {@code @Transactional}），由调用方事务统一提交/回滚（TC-IAM-021）。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class AuditRecorderTest {

    private static final UUID ACTOR_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ON_BEHALF_OF = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID OBJECT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final Instant OCCURRED_AT = Instant.parse("2026-11-17T02:00:00Z");
    private static final String BEFORE_DIGEST = "a".repeat(64);
    private static final String AFTER_DIGEST = "b".repeat(64);

    @Mock
    private AuditLogAppendPort auditLogAppendPort;

    @Mock
    private AuditOutboxPort auditOutboxPort;

    @Captor
    private ArgumentCaptor<AuditEvent> eventCaptor;

    private AuditRecorder recorder;

    @BeforeEach
    void setUp() {
        recorder = new AuditRecorder(auditLogAppendPort, auditOutboxPort);
    }

    private static AuditEvent criticalEvent() {
        return new AuditEvent(
                "trace-2026-0001",
                ACTOR_ID,
                ON_BEHALF_OF,
                "APPLICATION_SUBMIT",
                "APPLICATION",
                OBJECT_ID,
                7,
                BEFORE_DIGEST,
                AFTER_DIGEST,
                "提交申报",
                "10.0.0.8",
                "Mozilla/5.0",
                OCCURRED_AT);
    }

    @Test
    void recordAppendsCriticalAuditWithEveryDocumentedField() {
        AuditEvent event = criticalEvent();

        recorder.record(event);

        verify(auditLogAppendPort).append(eventCaptor.capture());
        AuditEvent captured = eventCaptor.getValue();
        assertThat(captured.traceId()).isEqualTo("trace-2026-0001");
        assertThat(captured.actorId()).isEqualTo(ACTOR_ID);
        assertThat(captured.onBehalfOf()).isEqualTo(ON_BEHALF_OF);
        assertThat(captured.action()).isEqualTo("APPLICATION_SUBMIT");
        assertThat(captured.objectType()).isEqualTo("APPLICATION");
        assertThat(captured.objectId()).isEqualTo(OBJECT_ID);
        assertThat(captured.objectVersion()).isEqualTo(7);
        assertThat(captured.beforeDigest()).isEqualTo(BEFORE_DIGEST);
        assertThat(captured.afterDigest()).isEqualTo(AFTER_DIGEST);
        assertThat(captured.reason()).isEqualTo("提交申报");
        assertThat(captured.ip()).isEqualTo("10.0.0.8");
        assertThat(captured.userAgent()).isEqualTo("Mozilla/5.0");
        assertThat(captured.occurredAt()).isEqualTo(OCCURRED_AT);
        verifyNoInteractions(auditOutboxPort);
    }

    @Test
    void recordPropagatesAppendFailureSoBusinessTransactionRollsBack() {
        AuditEvent event = criticalEvent();
        org.mockito.Mockito.doThrow(new DataAccessResourceFailureException("audit table unavailable"))
                .when(auditLogAppendPort).append(any());

        assertThatThrownBy(() -> recorder.record(event))
                .isInstanceOf(DataAccessResourceFailureException.class)
                .hasMessageContaining("audit table unavailable");
        verify(auditOutboxPort, never()).enqueue(any());
    }

    @Test
    void recordNonCriticalWritesOutboxOnly() {
        AuditEvent event = criticalEvent();

        recorder.recordNonCritical(event);

        verify(auditOutboxPort).enqueue(eventCaptor.capture());
        assertThat(eventCaptor.getValue().action()).isEqualTo("APPLICATION_SUBMIT");
        verifyNoInteractions(auditLogAppendPort);
    }

    @Test
    void recordRejectsNullEventInsteadOfSilentlySkippingAudit() {
        assertThatThrownBy(() -> recorder.record(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("审计事件不能为空");

        assertThatThrownBy(() -> recorder.recordNonCritical(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("审计事件不能为空");
        verifyNoInteractions(auditLogAppendPort, auditOutboxPort);
    }

    @Test
    void recorderOpensNoIndependentTransaction() {
        boolean annotated = java.util.Arrays.stream(AuditRecorder.class.getDeclaredMethods())
                .anyMatch(method -> method.isAnnotationPresent(
                        org.springframework.transaction.annotation.Transactional.class));

        assertThat(annotated)
                .as("AuditRecorder 不得声明 @Transactional（含 REQUIRES_NEW），否则业务回滚时审计会残留")
                .isFalse();
    }
}
