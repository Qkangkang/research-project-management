package cn.edu.rpm.application.infrastructure.mapper;

import cn.edu.rpm.application.infrastructure.entity.ApplicationEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * {@code application} 表 Mapper（T-APP-003）。
 *
 * <p>{@link BaseMapper} 提供通用 CRUD；悲观锁查询 {@code SELECT ... FOR UPDATE}
 * 需携带行级锁语义，SQL 统一维护在 {@code resources/mapper/ApplicationMapper.xml}
 * （禁止注解 SQL，便于 DBA 审阅与索引对齐）。</p>
 */
@Mapper
public interface ApplicationMapper extends BaseMapper<ApplicationEntity> {

    /**
     * 按主键加行级排他锁查询申报主档（{@code SELECT ... FOR UPDATE}）。
     *
     * <p>用于申报提交链路：事务内先锁主档，串行化同一申报的并发提交，
     * 与 {@code application.version} 乐观锁共同保证「最多一个版本提交成功」
     * （TC-APP-009）。行锁仅在当前事务内有效，调用方必须处于事务中。</p>
     *
     * @param id 申报主档 ID
     * @return 加锁命中的主档；记录不存在返回 {@code null}
     */
    ApplicationEntity selectByIdForUpdate(@Param("id") String id);
}
