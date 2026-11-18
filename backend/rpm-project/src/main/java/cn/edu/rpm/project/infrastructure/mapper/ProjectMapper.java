package cn.edu.rpm.project.infrastructure.mapper;

import cn.edu.rpm.project.infrastructure.entity.ProjectEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * {@code project} 表 Mapper（T-PRJ-001）。
 *
 * <p>{@link BaseMapper} 提供通用 CRUD；状态迁移、基线切换等写操作需要行级锁，
 * 悲观锁 SQL 统一维护在 {@code resources/mapper/ProjectMapper.xml}（禁止注解 SQL）。</p>
 */
@Mapper
public interface ProjectMapper extends BaseMapper<ProjectEntity> {

    /**
     * 按主键加行级排他锁查询项目（{@code SELECT ... FOR UPDATE}）。
     *
     * <p>用于状态迁移、交接、变更批准等需要串行化的写链路；调用方必须处于事务中。</p>
     *
     * @param id 项目 ID
     * @return 加锁命中的项目；不存在返回 {@code null}
     */
    ProjectEntity selectByIdForUpdate(@Param("id") String id);
}
