package cn.edu.rpm.execution.infrastructure.mapper;

import cn.edu.rpm.execution.infrastructure.entity.ProgressReportEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * {@code progress_report} 表 Mapper（T-EXEC-001）。
 */
@Mapper
public interface ProgressReportMapper extends BaseMapper<ProgressReportEntity> {

    /**
     * 按主键加行级排他锁查询报告（提交/审核链路串行化）。
     *
     * @param id 报告 ID
     * @return 加锁命中的报告；不存在返回 {@code null}
     */
    ProgressReportEntity selectByIdForUpdate(@Param("id") String id);
}
