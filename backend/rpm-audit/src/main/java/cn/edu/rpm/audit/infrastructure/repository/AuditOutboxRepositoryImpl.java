package cn.edu.rpm.audit.infrastructure.repository;

import cn.edu.rpm.audit.domain.model.AuditEvent;
import cn.edu.rpm.audit.domain.model.AuditOutboxStatus;
import cn.edu.rpm.audit.infrastructure.entity.AuditLogEntity;
import cn.edu.rpm.audit.infrastructure.entity.AuditOutboxEntity;
import cn.edu.rpm.audit.infrastructure.mapper.AuditLogMapper;
import cn.edu.rpm.audit.infrastructure.mapper.AuditOutboxMapper;
import cn.edu.rpm.audit.infrastructure.support.AuditEventJsonCodec;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code audit_outbox} 仓储实现（T-AUD-002）。
 *
 * <p>入队动作只做事件序列化与 PO 组装：{@code event_id} 每次入队生成 UUID，
 * {@code status=NEW}，{@code attempts=0}，{@code next_retry_at} 取数据库默认 now()。</p>
 *
 * <p>补写（{@link #relay}）以独立事务执行：行锁领取（FOR UPDATE SKIP LOCKED，
 * 多实例互不重复领取）→ 载荷反序列化 → 以 {@code event_id} 作为审计主键
 * 幂等插入 {@code audit_log}（ON CONFLICT DO NOTHING）→ 标记 SENT。
 * 领取成功但标记前崩溃时，重放不会产生重复审计行。</p>
 */
@Repository
public class AuditOutboxRepositoryImpl implements AuditOutboxRepository {

    /** 展示/落库时区口径（与 AuditLogRepositoryImpl 一致）。 */
    private static final ZoneId ZONE_ASIA_SHANGHAI = ZoneId.of("Asia/Shanghai");

    private final AuditOutboxMapper auditOutboxMapper;

    private final AuditLogMapper auditLogMapper;

    private final AuditEventJsonCodec jsonCodec;

    public AuditOutboxRepositoryImpl(AuditOutboxMapper auditOutboxMapper,
                                     AuditLogMapper auditLogMapper) {
        this.auditOutboxMapper = auditOutboxMapper;
        this.auditLogMapper = auditLogMapper;
        this.jsonCodec = new AuditEventJsonCodec();
    }

    @Override
    public void enqueue(AuditEvent event) {
        Objects.requireNonNull(event, "审计事件不能为空");
        AuditOutboxEntity entry = new AuditOutboxEntity();
        entry.setEventId(UUID.randomUUID().toString().replace("-", ""));
        entry.setPayload(jsonCodec.toJson(event));
        entry.setTraceId(event.traceId());
        entry.setStatus(AuditOutboxStatus.NEW.name());
        entry.setAttempts(0);
        insert(entry);
    }

    @Override
    public void insert(AuditOutboxEntity entry) {
        auditOutboxMapper.insert(entry);
    }

    @Override
    public Optional<AuditOutboxEntity> findById(String id) {
        return Optional.ofNullable(auditOutboxMapper.selectById(id));
    }

    @Override
    public List<String> findPendingIds(int maxRows) {
        return auditOutboxMapper.selectPendingIds(maxRows);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean relay(String id) {
        AuditOutboxEntity entry = auditOutboxMapper.lockById(id);
        if (entry == null || AuditOutboxStatus.SENT.name().equals(entry.getStatus())) {
            // 被其他实例持有（SKIP LOCKED 未命中）或已被处理，跳过
            return false;
        }
        AuditEvent event = jsonCodec.fromJson(entry.getPayload());
        auditLogMapper.insertIgnoreWithId(toAuditLogEntity(entry.getEventId(), event));
        auditOutboxMapper.markSent(id);
        return true;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String id, int maxAttempts, String lastError) {
        auditOutboxMapper.markFailure(id, maxAttempts, truncate(lastError, 2048));
    }

    /** 补写专用投影：以 outbox {@code event_id} 为审计主键，保证重放幂等。 */
    private static AuditLogEntity toAuditLogEntity(String forcedId, AuditEvent event) {
        AuditLogEntity entry = new AuditLogEntity();
        entry.setId(forcedId);
        entry.setTraceId(event.traceId());
        entry.setActorId(uuidText(event.actorId()));
        entry.setOnBehalfOf(uuidText(event.onBehalfOf()));
        entry.setAction(event.action());
        entry.setObjectType(event.objectType());
        entry.setObjectId(uuidText(event.objectId()));
        entry.setObjectVersion(event.objectVersion());
        entry.setBeforeDigest(event.beforeDigest());
        entry.setAfterDigest(event.afterDigest());
        entry.setReason(event.reason());
        entry.setIp(event.ip());
        entry.setUserAgent(event.userAgent());
        entry.setOccurredAt(event.occurredAt().atZone(ZONE_ASIA_SHANGHAI).toOffsetDateTime());
        return entry;
    }

    private static String uuidText(UUID value) {
        return value == null ? null : value.toString().replace("-", "");
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
