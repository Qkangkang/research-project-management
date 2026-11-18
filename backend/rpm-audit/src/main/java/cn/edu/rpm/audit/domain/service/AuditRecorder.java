package cn.edu.rpm.audit.domain.service;

import cn.edu.rpm.audit.domain.model.AuditEvent;
import cn.edu.rpm.audit.domain.port.AuditLogAppendPort;
import cn.edu.rpm.audit.domain.port.AuditOutboxPort;

import java.util.Objects;

/**
 * 审计记录器（T-AUD-002；技术文档 03 §4.1 / §4.2 / §4.3）。
 *
 * <p>纯 Java 领域服务，不声明事务注解：关键动作由调用方业务事务包裹，
 * 审计与业务同提交、同回滚（TC-IAM-021）；审计写入失败时本类不吞异常，
 * 由异常驱动业务事务回滚（TC-IAM-022）。非关键动作用 {@link #recordNonCritical}
 * 写本地 outbox，由后续补写任务异步落 {@code audit_log}。</p>
 */
public class AuditRecorder {

    private final AuditLogAppendPort auditLogAppendPort;

    private final AuditOutboxPort auditOutboxPort;

    /**
     * 构造审计记录器。
     *
     * @param auditLogAppendPort 审计日志只追加端口
     * @param auditOutboxPort    非关键审计 outbox 端口
     */
    public AuditRecorder(AuditLogAppendPort auditLogAppendPort, AuditOutboxPort auditOutboxPort) {
        this.auditLogAppendPort = Objects.requireNonNull(auditLogAppendPort, "auditLogAppendPort 不能为空");
        this.auditOutboxPort = Objects.requireNonNull(auditOutboxPort, "auditOutboxPort 不能为空");
    }

    /**
     * 记录关键动作审计（申报提交、审批、资金、验收、归档、导出、批量授权等）。
     *
     * <p>必须在业务事务内调用；本方法不做异常捕获与降级——写失败即抛出，
     * 让业务事务回滚，杜绝"业务成功但无审计"。</p>
     *
     * @param event 审计事件
     * @throws NullPointerException 事件为空
     */
    public void record(AuditEvent event) {
        AuditEvent auditEvent = Objects.requireNonNull(event, "审计事件不能为空");
        auditLogAppendPort.append(auditEvent);
    }

    /**
     * 记录非关键动作审计：写入本地 {@code audit_outbox}，与业务写同一事务。
     *
     * <p>补写任务按 {@code status + next_retry_at} 领取并异步写入 {@code audit_log}；
     * 本方法不直接写审计表，避免非关键动作因审计设施抖动阻塞主链路。</p>
     *
     * @param event 审计事件
     * @throws NullPointerException 事件为空
     */
    public void recordNonCritical(AuditEvent event) {
        AuditEvent auditEvent = Objects.requireNonNull(event, "审计事件不能为空");
        auditOutboxPort.enqueue(auditEvent);
    }
}
