package cn.edu.rpm.audit.infrastructure.repository;

import cn.edu.rpm.audit.domain.port.AuditOutboxPort;
import cn.edu.rpm.audit.infrastructure.entity.AuditOutboxEntity;

import java.util.List;
import java.util.Optional;

/**
 * {@code audit_outbox} 仓储接口（T-AUD-002）。
 *
 * <p>继承 {@link AuditOutboxPort} 供领域记录器按领域事件入队；本接口额外暴露
 * PO 级插入/查询，以及补写任务（{@code AuditOutboxRelayService}）所需的
 * 领取、幂等补写与失败登记能力。</p>
 */
public interface AuditOutboxRepository extends AuditOutboxPort {

    /**
     * 插入一条 outbox 记录。
     *
     * @param entry 待插入 PO
     */
    void insert(AuditOutboxEntity entry);

    /**
     * 按主键查询 outbox 记录。
     *
     * @param id 主键
     * @return 命中记录；不存在返回空 {@link Optional}
     */
    Optional<AuditOutboxEntity> findById(String id);

    /**
     * 查询待补写记录主键（NEW/FAILED 且到达 {@code next_retry_at}）。
     *
     * @param maxRows 单批上限
     * @return 主键列表（入队顺序）
     */
    List<String> findPendingIds(int maxRows);

    /**
     * 补写单条记录：行锁领取（SKIP LOCKED）→ 反序列化 → 以 {@code event_id}
     * 幂等写入 {@code audit_log} → 标记 SENT，四步同一事务。
     *
     * @param id outbox 主键
     * @return {@code true} 已补写；{@code false} 被其他实例持有或状态已变化（跳过）
     * @throws IllegalArgumentException 载荷损坏（事务回滚后由调用方登记失败）
     */
    boolean relay(String id);

    /**
     * 登记一次补写失败：attempts+1，达到上限置 DEAD，否则 FAILED 并按已失败次数退避重试。
     *
     * @param id          outbox 主键
     * @param maxAttempts 最大尝试次数（含本次）
     * @param lastError   失败原因
     */
    void recordFailure(String id, int maxAttempts, String lastError);
}
