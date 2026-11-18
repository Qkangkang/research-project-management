package cn.edu.rpm.common.domain.model;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 事务内 Outbox 待投递事件（T-APP-014，技术文档 04 §3.2 步骤 8）。
 *
 * <p>一期不引入 MQ：领域事件随业务写操作在同一数据库事务内写入
 * {@code integration_outbox}，由后台轮询任务投递（RULE-E2：写成功后才发布）。
 * {@code idempotencyKey} 对应 {@code uk_outbox_idempotency}，保证同一业务动作
 * 重复提交不会产生重复事件。</p>
 *
 * @param eventId        事件 ID（全局唯一，对应 {@code uk_outbox_event_id}）
 * @param aggregateType  聚合类型（如 {@code APPLICATION}）
 * @param aggregateId    聚合 ID
 * @param eventType      事件类型（如 {@code APPLICATION_SUBMITTED}）
 * @param payload        事件载荷（可序列化为 JSON 对象）
 * @param traceId        链路 TraceID；缺失时可为空
 * @param idempotencyKey 幂等键（业务动作级唯一）
 */
public record OutboxEvent(
        UUID eventId,
        String aggregateType,
        UUID aggregateId,
        String eventType,
        Map<String, Object> payload,
        String traceId,
        String idempotencyKey
) {

    /** 校验必填字段并复制载荷。 */
    public OutboxEvent {
        Objects.requireNonNull(eventId, "eventId 不能为空");
        requireText(aggregateType, "aggregateType");
        Objects.requireNonNull(aggregateId, "aggregateId 不能为空");
        requireText(eventType, "eventType");
        Objects.requireNonNull(payload, "payload 不能为空");
        requireText(idempotencyKey, "idempotencyKey");
        payload = Map.copyOf(payload);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
    }
}
