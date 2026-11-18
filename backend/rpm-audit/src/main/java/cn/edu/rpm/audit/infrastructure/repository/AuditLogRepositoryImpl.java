package cn.edu.rpm.audit.infrastructure.repository;

import cn.edu.rpm.audit.domain.model.AuditEvent;
import cn.edu.rpm.audit.infrastructure.entity.AuditLogEntity;
import cn.edu.rpm.audit.infrastructure.mapper.AuditLogMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Repository;

import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * {@code audit_log} 只追加仓储实现（T-AUD-001）。
 *
 * <p>基于 MyBatis-Plus {@link AuditLogMapper} 的薄委托层：查询条件以
 * {@link LambdaQueryWrapper} 按实体注解生成，无手写 SQL。领域事件到 PO 的转换
 * 在本层完成（技术栈未引入 MapStruct，按显式字段投影落地，禁止 Entity 跨层流转）。</p>
 *
 * <p>本类不捕获任何持久化异常：审计写入失败必须向上传播，使调用方业务事务回滚
 * （技术文档 03 §4.3，TC-IAM-022）。</p>
 */
@Repository
public class AuditLogRepositoryImpl implements AuditLogRepository {

    /** 展示/落库时区口径（Asia/Shanghai），事件 {@link java.time.Instant} 需转换为 OffsetDateTime。 */
    private static final ZoneId ZONE_ASIA_SHANGHAI = ZoneId.of("Asia/Shanghai");

    private final AuditLogMapper auditLogMapper;

    public AuditLogRepositoryImpl(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    @Override
    public void append(AuditEvent event) {
        Objects.requireNonNull(event, "审计事件不能为空");
        insert(toEntity(event));
    }

    @Override
    public void insert(AuditLogEntity entry) {
        auditLogMapper.insert(entry);
    }

    @Override
    public List<AuditLogEntity> findByTraceId(String traceId) {
        return auditLogMapper.selectList(new LambdaQueryWrapper<AuditLogEntity>()
                .eq(AuditLogEntity::getTraceId, traceId)
                .orderByAsc(AuditLogEntity::getOccurredAt));
    }

    @Override
    public Optional<AuditLogEntity> findById(String id) {
        return Optional.ofNullable(auditLogMapper.selectById(id));
    }

    /**
     * 领域审计事件 → 只追加 PO 的显式字段投影。
     *
     * @param event 审计事件
     * @return 待插入 PO（不设置主键，由 ASSIGN_UUID 生成）
     */
    private static AuditLogEntity toEntity(AuditEvent event) {
        AuditLogEntity entry = new AuditLogEntity();
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

    /**
     * uuid 值对象 → 数据库文本（32 位无连字符，与 ASSIGN_UUID 口径一致）。
     *
     * @param value uuid 值；可为空
     * @return 文本；输入为空返回 {@code null}
     */
    private static String uuidText(java.util.UUID value) {
        return value == null ? null : value.toString().replace("-", "");
    }
}
