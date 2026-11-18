package cn.edu.rpm.audit.domain.model;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 审计事件（T-AUD-002；技术文档 03 §4.1 原样契约）。
 *
 * <p>只承载审计判定所需的元数据与前后摘要，不承载业务正文/敏感明文。
 * {@code traceId}/{@code action}/{@code objectType}/{@code occurredAt} 必填；
 * 操作人、代理身份、对象 ID、摘要、IP、User-Agent 可为空（系统级动作）。</p>
 *
 * @param traceId       全链路 TraceID
 * @param actorId       真实操作人用户 ID
 * @param onBehalfOf    代理人/委托人用户 ID
 * @param action        受控动作码
 * @param objectType    对象类型
 * @param objectId      对象 ID
 * @param objectVersion 对象版本号
 * @param beforeDigest  变更前摘要（SHA-256 十六进制，64 位）
 * @param afterDigest   变更后摘要（SHA-256 十六进制，64 位）
 * @param reason        原因/说明
 * @param ip            客户端 IP
 * @param userAgent     客户端 User-Agent
 * @param occurredAt    动作发生时刻
 */
public record AuditEvent(
        String traceId,
        UUID actorId,
        UUID onBehalfOf,
        String action,
        String objectType,
        UUID objectId,
        Integer objectVersion,
        String beforeDigest,
        String afterDigest,
        String reason,
        String ip,
        String userAgent,
        Instant occurredAt
) {

    private static final Pattern SHA256_HEX = Pattern.compile("^[0-9a-fA-F]{64}$");

    /** 校验必填字段与摘要格式（摘要非法时快速失败，避免落库触发器报错掩盖根因）。 */
    public AuditEvent {
        requireText(traceId, "traceId");
        requireText(action, "action");
        requireText(objectType, "objectType");
        if (occurredAt == null) {
            throw new IllegalArgumentException("occurredAt 不能为空");
        }
        requireDigestShape(beforeDigest, "beforeDigest");
        requireDigestShape(afterDigest, "afterDigest");
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
    }

    private static void requireDigestShape(String value, String field) {
        if (value != null && !SHA256_HEX.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " 必须为 64 位十六进制 SHA-256 摘要");
        }
    }
}
