package cn.edu.rpm.audit.infrastructure.repository;

import cn.edu.rpm.audit.domain.model.AuditEvent;
import cn.edu.rpm.audit.infrastructure.entity.AuditLogEntity;
import cn.edu.rpm.audit.infrastructure.entity.AuditOutboxEntity;
import cn.edu.rpm.audit.infrastructure.mapper.AuditLogMapper;
import cn.edu.rpm.audit.infrastructure.mapper.AuditOutboxMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AuditOutboxRepositoryImpl} 纯单元测试（T-AUD-002 先行测试）。
 *
 * <p>断言非关键审计入队后状态为 {@code NEW}、事件幂等键非空、jsonb 载荷包含完整事件字段；
 * 空事件直接拒绝，避免写入无载荷的 outbox 记录。</p>
 */
@ExtendWith(MockitoExtension.class)
class AuditOutboxRepositoryImplTest {

    private static final UUID ACTOR_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OBJECT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Mock
    private AuditOutboxMapper auditOutboxMapper;

    @Mock
    private AuditLogMapper auditLogMapper;

    @Captor
    private ArgumentCaptor<AuditOutboxEntity> entityCaptor;

    @Captor
    private ArgumentCaptor<AuditLogEntity> auditLogCaptor;

    private AuditOutboxRepositoryImpl repository;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, AuditOutboxEntity.class);
    }

    @BeforeEach
    void setUp() {
        repository = new AuditOutboxRepositoryImpl(auditOutboxMapper, auditLogMapper);
    }

    private static AuditEvent event() {
        return new AuditEvent(
                "trace-2026-0002",
                ACTOR_ID,
                null,
                "REPORT_EXPORTED",
                "EXPORT_TASK",
                OBJECT_ID,
                null,
                null,
                null,
                "导出项目清单",
                "10.0.0.9",
                "curl/8.0",
                Instant.parse("2026-11-17T03:30:00Z"));
    }

    @Test
    void enqueueWritesNewOutboxEntryWithSerializedPayload() {
        repository.enqueue(event());

        verify(auditOutboxMapper).insert(entityCaptor.capture());
        AuditOutboxEntity entry = entityCaptor.getValue();
        assertThat(entry.getEventId()).hasSize(32).matches("[0-9a-f]{32}");
        assertThat(entry.getTraceId()).isEqualTo("trace-2026-0002");
        assertThat(entry.getStatus()).isEqualTo("NEW");
        assertThat(entry.getAttempts()).isZero();
        assertThat(entry.getPayload())
                .contains("\"traceId\":\"trace-2026-0002\"")
                .contains("\"action\":\"REPORT_EXPORTED\"")
                .contains("\"objectType\":\"EXPORT_TASK\"")
                .contains("\"occurredAt\":\"2026-11-17T03:30:00Z\"");
    }

    @Test
    void enqueueRejectsNullEvent() {
        assertThatThrownBy(() -> repository.enqueue(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("审计事件不能为空");
        verify(auditOutboxMapper, never()).insert(org.mockito.ArgumentMatchers.<AuditOutboxEntity>any());
    }

    @Test
    void findByIdReturnsEmptyWhenOutboxEntryMissing() {
        when(auditOutboxMapper.selectById("no-such-outbox")).thenReturn(null);

        assertThat(repository.findById("no-such-outbox")).isEmpty();
    }

    @Test
    void outboxTableMapsV08Columns() {
        TableInfo tableInfo = TableInfoHelper.getTableInfo(AuditOutboxEntity.class);

        assertThat(tableInfo.getTableName()).isEqualTo("audit_outbox");
        assertThat(tableInfo.getFieldList())
                .extracting(com.baomidou.mybatisplus.core.metadata.TableFieldInfo::getColumn)
                .containsExactlyInAnyOrder(
                        "event_id", "payload", "trace_id", "status", "attempts", "next_retry_at",
                        "last_error", "created_at", "updated_at", "version");
    }

    @Test
    void relaySkipsWhenRowLockedOrAlreadySent() {
        when(auditOutboxMapper.lockById("locked-id")).thenReturn(null);

        assertThat(repository.relay("locked-id")).isFalse();
        verify(auditLogMapper, never()).insertIgnoreWithId(org.mockito.ArgumentMatchers.any());
        verify(auditOutboxMapper, never()).markSent(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void relayWritesAuditWithEventIdAsPrimaryKeyThenMarksSent() {
        AuditOutboxEntity entry = new AuditOutboxEntity();
        entry.setEventId("7f".repeat(16));
        entry.setStatus("NEW");
        entry.setPayload("""
                {"traceId":"trace-2026-0002","action":"REPORT_EXPORTED","objectType":"EXPORT_TASK",
                 "occurredAt":"2026-11-17T03:30:00Z"}""");
        when(auditOutboxMapper.lockById(entry.getEventId())).thenReturn(entry);

        assertThat(repository.relay(entry.getEventId())).isTrue();

        // 幂等口径：审计主键 = outbox event_id（重放时 ON CONFLICT DO NOTHING）
        verify(auditLogMapper).insertIgnoreWithId(auditLogCaptor.capture());
        assertThat(auditLogCaptor.getValue().getId()).isEqualTo(entry.getEventId());
        assertThat(auditLogCaptor.getValue().getAction()).isEqualTo("REPORT_EXPORTED");
        verify(auditOutboxMapper).markSent(entry.getEventId());
    }

    @Test
    void recordFailureDelegatesWithTruncatedError() {
        repository.recordFailure("bad-id", 5, "x".repeat(3000));

        // last_error 截断至 2048，避免超出列宽导致失败登记本身失败
        verify(auditOutboxMapper).markFailure("bad-id", 5, "x".repeat(2048));
    }
}
