package cn.edu.rpm.audit.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * {@code audit_outbox} 非关键审计本地 outbox PO（T-AUD-002；V08）。
 *
 * <p>不继承 {@link cn.edu.rpm.common.entity.BaseEntity}：V08 表只有
 * {@code created_at}/{@code updated_at}/{@code version}，没有 {@code created_by}/{@code updated_by}。
 * {@code payload} 为 jsonb 文本（JDBC URL 的 {@code stringtype=unspecified} 使 String
 * 参数可直接写入 jsonb 列，读取时 pgjdbc 返回 JSON 文本）。</p>
 */
@TableName("audit_outbox")
public class AuditOutboxEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键；插入时由 MyBatis-Plus 生成 uuid。 */
    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    /** 事件幂等键（uk_audit_outbox_event_id），补写任务按此去重。 */
    @TableField("event_id")
    private String eventId;

    /** 审计事件 JSON 载荷（jsonb）。 */
    @TableField("payload")
    private String payload;

    /** 全链路 TraceID。 */
    @TableField("trace_id")
    private String traceId;

    /** 状态：NEW / SENT / FAILED / DEAD（ck_audit_outbox_status）。 */
    @TableField("status")
    private String status;

    /** 已尝试补写次数。 */
    @TableField("attempts")
    private Integer attempts;

    /** 下次重试时间。 */
    @TableField("next_retry_at")
    private OffsetDateTime nextRetryAt;

    /** 最近一次失败原因。 */
    @TableField("last_error")
    private String lastError;

    /** 创建时间；INSERT 时由审计填充器写入。 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    /** 更新时间；INSERT/UPDATE 时由审计填充器写入。 */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;

    /** 乐观锁版本号。 */
    @Version
    @TableField("version")
    private Integer version = 0;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getAttempts() {
        return attempts;
    }

    public void setAttempts(Integer attempts) {
        this.attempts = attempts;
    }

    public OffsetDateTime getNextRetryAt() {
        return nextRetryAt;
    }

    public void setNextRetryAt(OffsetDateTime nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }
}
