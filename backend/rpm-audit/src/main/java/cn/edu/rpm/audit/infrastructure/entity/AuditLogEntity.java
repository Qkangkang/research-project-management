package cn.edu.rpm.audit.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * {@code audit_log} 审计日志只追加 PO（T-AUD-001）。
 *
 * <p>对应 V01 基线 DDL 与《02-数据模型与数据库设计.md》§3.6。该表不继承
 * {@link cn.edu.rpm.common.entity.BaseEntity}：审计日志没有 {@code updated_at} /
 * {@code created_by} / {@code updated_by} / {@code version}，且数据库触发器
 * {@code trg_audit_log_immutable} 拒绝一切 {@code UPDATE/DELETE}（TC-IAM-020）。</p>
 *
 * <p>字段口径：{@code before_digest}/{@code after_digest} 为 64 位 SHA-256 十六进制摘要，
 * 不保存业务明文；{@code occurred_at} 为动作发生时刻，{@code created_at} 为落库时刻
 * （由数据库默认值或审计填充器写入）。</p>
 */
@TableName("audit_log")
public class AuditLogEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键；插入时由 MyBatis-Plus 生成 32 位十六进制 uuid。 */
    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    /** 全链路 TraceID（接入层生成，禁止为空）。 */
    @TableField("trace_id")
    private String traceId;

    /** 真实操作人用户 ID。 */
    @TableField("actor_id")
    private String actorId;

    /** 代理人/委托人用户 ID；非代理操作为空。 */
    @TableField("on_behalf_of")
    private String onBehalfOf;

    /** 受控动作码（如 {@code APPLICATION_SUBMIT}、{@code FILE_DOWNLOADED}）。 */
    @TableField("action")
    private String action;

    /** 对象类型（如 {@code APPLICATION}、{@code EXPENSE_REQUEST}）。 */
    @TableField("object_type")
    private String objectType;

    /** 对象 ID；系统级动作可为空。 */
    @TableField("object_id")
    private String objectId;

    /** 对象版本号；无版本语义的动作可为空。 */
    @TableField("object_version")
    private Integer objectVersion;

    /** 变更前摘要（SHA-256 十六进制，64 位）；无变更前状态时为空。 */
    @TableField("before_digest")
    private String beforeDigest;

    /** 变更后摘要（SHA-256 十六进制，64 位）；查询类动作为空。 */
    @TableField("after_digest")
    private String afterDigest;

    /** 原因/说明（管理员修改、退回等场景必填）。 */
    @TableField("reason")
    private String reason;

    /** 客户端 IP（PostgreSQL {@code inet} 列）。 */
    @TableField("ip")
    private String ip;

    /** 客户端 User-Agent。 */
    @TableField("user_agent")
    private String userAgent;

    /** 动作发生时刻。 */
    @TableField("occurred_at")
    private OffsetDateTime occurredAt;

    /** 落库时刻；INSERT 时由审计填充器写入。 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getActorId() {
        return actorId;
    }

    public void setActorId(String actorId) {
        this.actorId = actorId;
    }

    public String getOnBehalfOf() {
        return onBehalfOf;
    }

    public void setOnBehalfOf(String onBehalfOf) {
        this.onBehalfOf = onBehalfOf;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getObjectType() {
        return objectType;
    }

    public void setObjectType(String objectType) {
        this.objectType = objectType;
    }

    public String getObjectId() {
        return objectId;
    }

    public void setObjectId(String objectId) {
        this.objectId = objectId;
    }

    public Integer getObjectVersion() {
        return objectVersion;
    }

    public void setObjectVersion(Integer objectVersion) {
        this.objectVersion = objectVersion;
    }

    public String getBeforeDigest() {
        return beforeDigest;
    }

    public void setBeforeDigest(String beforeDigest) {
        this.beforeDigest = beforeDigest;
    }

    public String getAfterDigest() {
        return afterDigest;
    }

    public void setAfterDigest(String afterDigest) {
        this.afterDigest = afterDigest;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(OffsetDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
