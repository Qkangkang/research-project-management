package cn.edu.rpm.project.infrastructure.mapper;

import cn.edu.rpm.project.infrastructure.entity.MilestoneEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * {@code milestone} 表 Mapper（T-PRJ-004）。
 */
@Mapper
public interface MilestoneMapper extends BaseMapper<MilestoneEntity> {

    /**
     * 按主键加行级排他锁查询里程碑（{@code SELECT ... FOR UPDATE}）。
     *
     * <p>延期申请在锁内校验「仅一条 PENDING」与完成状态，避免并发重复申请
     * （TC-EXED-005）；调用方必须处于事务中。</p>
     *
     * @param id 里程碑 ID
     * @return 加锁命中的里程碑；不存在返回 {@code null}
     */
    MilestoneEntity selectByIdForUpdate(@Param("id") String id);
}
