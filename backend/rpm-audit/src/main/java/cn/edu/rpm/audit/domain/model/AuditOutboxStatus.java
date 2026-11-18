package cn.edu.rpm.audit.domain.model;

/**
 * 非关键审计 outbox 状态（V08 {@code ck_audit_outbox_status}）。
 *
 * <p>状态机：{@code NEW → SENT}；重试中 {@code NEW → FAILED → SENT/DEAD}。
 * 补写任务在后续批次实现，本批次只产生 {@link #NEW} 状态记录。</p>
 */
public enum AuditOutboxStatus {

    /** 待补写。 */
    NEW,

    /** 已补写。 */
    SENT,

    /** 补写失败，等待重试。 */
    FAILED,

    /** 重试耗尽，进入死信。 */
    DEAD
}
