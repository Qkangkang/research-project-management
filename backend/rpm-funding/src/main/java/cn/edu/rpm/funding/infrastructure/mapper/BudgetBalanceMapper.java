package cn.edu.rpm.funding.infrastructure.mapper;

import cn.edu.rpm.funding.infrastructure.entity.BudgetBalanceEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * {@code budget_balance} 表 Mapper（T-FUND-003）。
 */
@Mapper
public interface BudgetBalanceMapper extends BaseMapper<BudgetBalanceEntity> {

    /**
     * 按预算科目加行级排他锁查询余额投影（资金事件投影串行化）。
     *
     * @param budgetLineId 预算科目 ID
     * @return 加锁命中的余额行；不存在返回 {@code null}
     */
    BudgetBalanceEntity selectByBudgetLineIdForUpdate(@Param("budgetLineId") String budgetLineId);
}
