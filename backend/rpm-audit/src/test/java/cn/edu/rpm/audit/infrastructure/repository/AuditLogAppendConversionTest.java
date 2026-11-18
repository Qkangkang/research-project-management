package cn.edu.rpm.audit.infrastructure.repository;

import cn.edu.rpm.audit.domain.model.AuditEvent;
import cn.edu.rpm.audit.infrastructure.entity.AuditLogEntity;
import cn.edu.rpm.audit.infrastructure.mapper.AuditLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * {@link AuditLogRepositoryImpl#append} 领域事件到 PO 的转换测试（T-AUD-002）。
 *
 * <p>验证 TC-IAM-019 的字段完整性：{@code actor_id}/{@code trace_id}/{@code action}/
 * {@code object_id}/{@code object_version}/前后摘要/IP/时间全部逐字段落位；UUID 以
 * 32 位无连字符文本写入，时间以 {@code Asia/Shanghai} 偏移落库。</p>
 */
@ExtendWith(MockitoExtension.class)
class AuditLogAppendConversionTest {

    private static final UUID ACTOR_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OBJECT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Mock
    private AuditLogMapper auditLogMapper;

    @Captor
    private ArgumentCaptor<AuditLogEntity> entityCaptor;

    private AuditLogRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        repository = new AuditLogRepositoryImpl(auditLogMapper);
    }

    @Test
    void appendProjectsEveryAuditEventFieldToColumns() {
        AuditEvent event = new AuditEvent(
                "trace-2026-0003",
                ACTOR_ID,
                null,
                "EXPENSE_APPROVED",
                "EXPENSE_REQUEST",
                OBJECT_ID,
                5,
                "c".repeat(64),
                "d".repeat(64),
                "财务终审通过",
                "10.0.0.10",
                "Mozilla/5.0",
                Instant.parse("2026-11-17T04:00:00Z"));

        repository.append(event);

        verify(auditLogMapper).insert(entityCaptor.capture());
        AuditLogEntity entry = entityCaptor.getValue();
        assertThat(entry.getTraceId()).isEqualTo("trace-2026-0003");
        assertThat(entry.getActorId()).isEqualTo("11111111111111111111111111111111");
        assertThat(entry.getOnBehalfOf()).isNull();
        assertThat(entry.getAction()).isEqualTo("EXPENSE_APPROVED");
        assertThat(entry.getObjectType()).isEqualTo("EXPENSE_REQUEST");
        assertThat(entry.getObjectId()).isEqualTo("33333333333333333333333333333333");
        assertThat(entry.getObjectVersion()).isEqualTo(5);
        assertThat(entry.getBeforeDigest()).isEqualTo("c".repeat(64));
        assertThat(entry.getAfterDigest()).isEqualTo("d".repeat(64));
        assertThat(entry.getReason()).isEqualTo("财务终审通过");
        assertThat(entry.getIp()).isEqualTo("10.0.0.10");
        assertThat(entry.getUserAgent()).isEqualTo("Mozilla/5.0");
        assertThat(entry.getOccurredAt().toInstant()).isEqualTo(Instant.parse("2026-11-17T04:00:00Z"));
        assertThat(entry.getOccurredAt().getOffset()).isEqualTo(ZoneOffset.ofHours(8));
    }
}
