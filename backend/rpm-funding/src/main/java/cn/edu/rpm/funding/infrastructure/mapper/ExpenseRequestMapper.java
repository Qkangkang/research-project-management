package cn.edu.rpm.funding.infrastructure.mapper;

import cn.edu.rpm.funding.infrastructure.entity.ExpenseRequestEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * {@code expense_request} 表 Mapper（T-FUND-004）。
 */
@Mapper
public interface ExpenseRequestMapper extends BaseMapper<ExpenseRequestEntity> {

    /**
     * 按主键加行级排他锁查询支出（提交/审批链路串行化）。
     *
     * @param id 支出 ID
     * @return 加锁命中的支出；不存在返回 {@code null}
     */
    ExpenseRequestEntity selectByIdForUpdate(@Param("id") String id);
}
