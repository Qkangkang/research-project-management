package cn.edu.rpm.audit.infrastructure.repository;

import cn.edu.rpm.audit.domain.port.AuditLogAppendPort;
import cn.edu.rpm.audit.infrastructure.entity.AuditLogEntity;

import java.util.List;
import java.util.Optional;

/**
 * {@code audit_log} 只追加仓储接口（T-AUD-001）。
 *
 * <p>契约边界：</p>
 * <ul>
 *   <li>只提供 {@code INSERT} 与查询，不提供任何 {@code UPDATE}/{@code DELETE} 方法；
 *       数据库触发器 {@code trg_audit_log_immutable} 对直连 SQL 同样拒绝（TC-IAM-020）；</li>
 *   <li>继承 {@link AuditLogAppendPort}，供领域服务 {@code AuditRecorder} 以领域对象追加，
 *       避免领域层依赖 PO/MyBatis（DEP-2）。</li>
 * </ul>
 */
public interface AuditLogRepository extends AuditLogAppendPort {

    /**
     * 插入一条审计日志。
     *
     * <p>主键 {@code id} 由 MyBatis-Plus 在执行期生成（ASSIGN_UUID）。</p>
     *
     * @param entry 待插入的审计日志 PO
     */
    void insert(AuditLogEntity entry);

    /**
     * 按 TraceID 查询审计日志（idx_audit_trace）。
     *
     * @param traceId 全链路 TraceID
     * @return 匹配记录，按发生时间升序；无记录返回空列表
     */
    List<AuditLogEntity> findByTraceId(String traceId);

    /**
     * 按主键查询审计日志。
     *
     * @param id 审计日志主键
     * @return 命中记录；不存在返回空 {@link Optional}
     */
    Optional<AuditLogEntity> findById(String id);
}
