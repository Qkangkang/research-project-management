package cn.edu.rpm.audit.infrastructure.support;

import cn.edu.rpm.audit.domain.model.AuditEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.Objects;

/**
 * 审计事件 JSON 编解码器（T-AUD-002）。
 *
 * <p>用于把 {@link AuditEvent} 序列化为 {@code audit_outbox.payload}（jsonb）文本。
 * 时间统一 ISO-8601 字符串、属性按字典序输出，保证同一事件多次序列化结果稳定，
 * 便于补写任务按 {@code event_id} 幂等重放。</p>
 */
public class AuditEventJsonCodec {

    private final ObjectMapper objectMapper;

    /**
     * 使用默认配置构造编解码器。
     */
    public AuditEventJsonCodec() {
        this(defaultMapper());
    }

    /**
     * 使用外部 {@link ObjectMapper} 构造编解码器（测试/定制序列化口径）。
     *
     * @param objectMapper Jackson 实例
     */
    public AuditEventJsonCodec(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper 不能为空");
    }

    /**
     * 将审计事件序列化为 JSON 文本。
     *
     * @param event 审计事件
     * @return JSON 文本
     * @throws IllegalStateException 序列化失败（事件结构固定，正常不可达）
     */
    public String toJson(AuditEvent event) {
        Objects.requireNonNull(event, "审计事件不能为空");
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("审计事件序列化失败", e);
        }
    }

    /**
     * 将 outbox 载荷反序列化为审计事件（补写任务用）。
     *
     * @param payload {@code audit_outbox.payload} JSON 文本
     * @return 审计事件
     * @throws IllegalArgumentException 载荷损坏/结构不符（记录构造器校验失败同样抛出）
     */
    public AuditEvent fromJson(String payload) {
        Objects.requireNonNull(payload, "payload 不能为空");
        try {
            return objectMapper.readValue(payload, AuditEvent.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("审计事件反序列化失败: " + e.getMessage(), e);
        }
    }

    private static ObjectMapper defaultMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        return mapper;
    }
}
