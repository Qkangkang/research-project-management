package cn.edu.rpm.project.infrastructure.mapper;

import cn.edu.rpm.project.infrastructure.entity.ChangeRequestEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * {@code change_request} 表 Mapper（T-PRJ-007）。
 */
@Mapper
public interface ChangeRequestMapper extends BaseMapper<ChangeRequestEntity> {

    /**
     * 按主键加行级排他锁查询变更单（审批回调幂等使用）。
     *
     * @param id 变更单 ID
     * @return 加锁命中的变更单；不存在返回 {@code null}
     */
    ChangeRequestEntity selectByIdForUpdate(@Param("id") String id);
}
