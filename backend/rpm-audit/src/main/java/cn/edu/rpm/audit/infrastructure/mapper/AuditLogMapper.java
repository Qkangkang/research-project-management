package cn.edu.rpm.audit.infrastructure.mapper;

import cn.edu.rpm.audit.infrastructure.entity.AuditLogEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * {@code audit_log} 表 Mapper（T-AUD-001）。
 *
 * <p>继承 MyBatis-Plus {@link BaseMapper} 通用能力；仓储层只使用插入与条件查询，
 * 不暴露 {@code update}/{@code delete}（数据库触发器 {@code trg_audit_log_immutable}
 * 为最终兜底）。补写专用的幂等插入在 {@code resources/mapper/AuditLogMapper.xml}。</p>
 */
@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLogEntity> {

    /**
     * 以指定主键幂等插入审计行（ON CONFLICT DO NOTHING）。
     *
     * <p>Outbox 补写用 outbox 的 {@code event_id} 作为审计主键：领取成功但
     * 标记 SENT 前崩溃的重放不会产生重复审计行。</p>
     *
     * @param entry 已设置主键的审计 PO
     * @return 影响行数（0 表示该事件已补写过）
     */
    int insertIgnoreWithId(@Param("entry") AuditLogEntity entry);
}
