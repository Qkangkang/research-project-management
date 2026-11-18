package cn.edu.rpm.audit.application.service;

import cn.edu.rpm.audit.infrastructure.repository.AuditOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 非关键审计 Outbox 补写任务（T-AUD-002 后续批次；技术文档 03 §4.3）。
 *
 * <p>职责：周期性把 {@code audit_outbox} 中 NEW/FAILED 的事件补写进
 * {@code audit_log}，补完非关键审计的「只写不消费」缺口：</p>
 * <ul>
 *   <li>领取：按 {@code status + next_retry_at}（部分索引 {@code idx_audit_outbox_claim}），
 *       行锁 {@code FOR UPDATE SKIP LOCKED}，多实例部署互不重复领取；</li>
 *   <li>幂等：以 outbox {@code event_id} 作为审计主键（ON CONFLICT DO NOTHING），
 *       「领取成功但标记 SENT 前崩溃」的重放不产生重复审计行；</li>
 *   <li>退避：失败按 1m → 5m → 15m → 1h → 6h 重试，5 次后置 DEAD 停止（人工介入）。</li>
 * </ul>
 *
 * <p>单条失败不影响同批其他记录；任务本身异常不向外抛出（调度线程不被打死）。</p>
 */
@Service
public class AuditOutboxRelayService {

    private static final Logger log = LoggerFactory.getLogger(AuditOutboxRelayService.class);

    private final AuditOutboxRepository outboxRepository;

    private final int batchSize;

    private final int maxAttempts;

    public AuditOutboxRelayService(AuditOutboxRepository outboxRepository,
                                   @Value("${rpm.audit.outbox.batch-size:100}") int batchSize,
                                   @Value("${rpm.audit.outbox.max-attempts:5}") int maxAttempts) {
        this.outboxRepository = outboxRepository;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
    }

    /**
     * 定时补写一轮。fixedDelay 语义：上一轮跑完才开始计时，天然避免任务堆积。
     */
    @Scheduled(fixedDelayString = "${rpm.audit.outbox.relay-interval-ms:5000}",
            initialDelayString = "${rpm.audit.outbox.relay-initial-delay-ms:10000}")
    public void relayScheduled() {
        try {
            int processed = relayBatch();
            if (processed > 0) {
                log.info("审计 Outbox 补写完成 processed={}", processed);
            }
        } catch (Exception e) {
            // 兜底：本轮整体失败不影响下一轮调度
            log.error("审计 Outbox 补写任务异常", e);
        }
    }

    /**
     * 补写一批待处理事件（暴露为公共方法便于测试与运维触发）。
     *
     * @return 本轮成功补写条数
     */
    public int relayBatch() {
        List<String> pendingIds = outboxRepository.findPendingIds(batchSize);
        int processed = 0;
        for (String id : pendingIds) {
            try {
                if (outboxRepository.relay(id)) {
                    processed++;
                }
            } catch (Exception e) {
                log.warn("审计 Outbox 单条补写失败 id={} error={}", id, e.toString());
                recordFailureSafely(id, e);
            }
        }
        return processed;
    }

    private void recordFailureSafely(String id, Exception cause) {
        try {
            // 退避间隔（1m → 5m → 15m → 1h）由 SQL 按 attempts 原子计算
            outboxRepository.recordFailure(id, maxAttempts, cause.toString());
        } catch (Exception e) {
            log.error("审计 Outbox 失败登记写入失败 id={}", id, e);
        }
    }
}
