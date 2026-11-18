package cn.edu.rpm.audit.domain.port;

import cn.edu.rpm.audit.domain.model.AuditEvent;

/**
 * 审计日志追加端口（T-AUD-002）。
 *
 * <p>领域层只依赖本端口，由 infrastructure 的 {@code AuditLogRepositoryImpl} 实现；
 * 端口不提供更新/删除能力，保证审计只追加（技术文档 03 §4.2）。</p>
 */
public interface AuditLogAppendPort {

    /**
     * 追加一条审计日志。
     *
     * <p>实现必须在调用方事务内执行（禁止新开事务）：业务回滚时审计一并回滚，
     * 审计写入失败时异常向上传播使业务事务回滚（技术文档 03 §4.3）。</p>
     *
     * @param event 审计事件
     */
    void append(AuditEvent event);
}
