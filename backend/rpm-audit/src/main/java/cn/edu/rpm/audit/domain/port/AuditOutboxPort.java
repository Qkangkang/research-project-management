package cn.edu.rpm.audit.domain.port;

import cn.edu.rpm.audit.domain.model.AuditEvent;

/**
 * 非关键审计事件本地 outbox 端口（T-AUD-002；V08 {@code audit_outbox}）。
 *
 * <p>非关键动作的业务允许在审计直写失败时先落 outbox、后异步补写；
 * 事件与业务写处于同一事务，补写任务由后续批次实现。</p>
 */
public interface AuditOutboxPort {

    /**
     * 将非关键审计事件写入本地 outbox。
     *
     * @param event 审计事件
     */
    void enqueue(AuditEvent event);
}
