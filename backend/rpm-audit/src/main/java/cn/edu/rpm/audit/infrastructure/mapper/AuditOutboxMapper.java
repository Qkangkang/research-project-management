package cn.edu.rpm.audit.infrastructure.mapper;

import cn.edu.rpm.audit.infrastructure.entity.AuditOutboxEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * {@code audit_outbox} 表 Mapper（T-AUD-002）。
 *
 * <p>继承 MyBatis-Plus {@link BaseMapper} 通用 CRUD；补写任务的领取/标记
 * SQL 在 {@code resources/mapper/AuditOutboxMapper.xml}（显式列清单，
 * {@code FOR UPDATE SKIP LOCKED} 领取口径见 V08 {@code idx_audit_outbox_claim}）。</p>
 */
@Mapper
public interface AuditOutboxMapper extends BaseMapper<AuditOutboxEntity> {

    /**
     * 查询待补写记录主键（NEW/FAILED 且到达重试时间），按入队顺序。
     *
     * @param maxRows 单批上限
     * @return 待处理主键列表
     */
    List<String> selectPendingIds(@Param("maxRows") int maxRows);

    /**
     * 行锁领取单条记录（FOR UPDATE SKIP LOCKED：被其他实例持有即跳过）。
     *
     * @param id 主键
     * @return 命中记录；被锁或不存在返回 {@code null}
     */
    AuditOutboxEntity lockById(@Param("id") String id);

    /**
     * 标记补写成功（status → SENT）。
     *
     * @param id 主键
     * @return 影响行数
     */
    int markSent(@Param("id") String id);

    /**
     * 记录一次补写失败：attempts+1，达到上限置 DEAD，否则 FAILED 并按已失败次数
     * 指数退避（1m → 5m → 15m → 1h，之后固定 1h），全部在 SQL 内原子计算。
     *
     * @param id          主键
     * @param maxAttempts 最大尝试次数（含本次）
     * @param lastError   失败原因（截断至 2048 字符以内由调用方保证）
     * @return 影响行数
     */
    int markFailure(@Param("id") String id,
                    @Param("maxAttempts") int maxAttempts,
                    @Param("lastError") String lastError);
}
