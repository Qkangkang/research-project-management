package cn.edu.rpm.boot.infrastructure.outbox;

import cn.edu.rpm.common.domain.model.OutboxEvent;
import cn.edu.rpm.common.domain.port.OutboxAppendPort;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.Objects;

/**
 * {@code integration_outbox} 追加适配器（T-APP-014，临时基础设施）。
 *
 * <p>业务模块通过 {@link OutboxAppendPort} 依赖本适配器，由 rpm-boot 装配，
 * 避免业务模块直接访问跨模块表。一期 OutboxPublisher 在后续 Batch 提供，
 * 本适配器只负责「与业务同事务追加 NEW 事件」；流程超时 5 秒（RULE-DB4），
 * 写失败按 {@code RPM-SYS-002} 抛出并回滚业务事务（禁止业务成功但事件丢失）。</p>
 *
 * <p>SQL 使用 {@code ?::jsonb} 显式转换载荷；{@code idempotency_key} 唯一索引
 * 兜底同一业务动作重复追加。</p>
 */
@Repository
public class JdbcOutboxAppendAdapter implements OutboxAppendPort {

    private static final int QUERY_TIMEOUT_SECONDS = 5;

    private static final String INSERT_EVENT = """
            INSERT INTO integration_outbox
                (event_id, aggregate_type, aggregate_id, event_type, payload, trace_id, idempotency_key)
            VALUES (?, ?, ?, ?, ?::jsonb, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper;

    /**
     * 构造适配器。
     *
     * @param dataSource   数据源（与业务事务同一数据源）
     * @param objectMapper JSON 序列化器
     */
    public JdbcOutboxAppendAdapter(DataSource dataSource, ObjectMapper objectMapper) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.jdbcTemplate.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper 不能为空");
    }

    @Override
    public void append(OutboxEvent event) {
        Objects.requireNonNull(event, "event 不能为空");
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event.payload());
        } catch (JsonProcessingException ex) {
            throw new BizException(ErrorCode.SYS_002, "Outbox 事件载荷无法序列化");
        }
        String traceId = event.traceId() == null || event.traceId().isBlank()
                ? null : event.traceId();
        try {
            jdbcTemplate.update(INSERT_EVENT,
                    event.eventId(),
                    event.aggregateType(),
                    event.aggregateId(),
                    event.eventType(),
                    payload,
                    traceId,
                    event.idempotencyKey());
        } catch (DataAccessException ex) {
            throw new BizException(ErrorCode.SYS_002, "Outbox 事件写入失败");
        }
    }
}
