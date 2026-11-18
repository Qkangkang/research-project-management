package cn.edu.rpm.common.domain.port;

import cn.edu.rpm.common.domain.model.OutboxEvent;

/**
 * Outbox 事件追加端口（T-APP-014）。
 *
 * <p>业务模块只依赖本端口，不直接访问 {@code integration_outbox} 表
 * （遵守「禁止跨模块直接访问表」）；适配器实现位于装配层（rpm-boot），
 * 后续 Batch 的 OutboxPublisher 复用同一张表实现轮询投递。</p>
 */
public interface OutboxAppendPort {

    /**
     * 在当前业务事务内追加一条待投递事件。
     *
     * <p>实现必须参与调用方事务（不新开事务），保证业务回滚时事件一并回滚；
     * {@code idempotencyKey} 撞唯一索引时抛
     * {@link org.springframework.dao.DuplicateKeyException}。</p>
     *
     * @param event 待投递事件
     */
    void append(OutboxEvent event);
}
