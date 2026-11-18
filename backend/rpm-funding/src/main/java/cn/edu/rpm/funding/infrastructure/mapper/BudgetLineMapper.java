package cn.edu.rpm.funding.infrastructure.mapper;

import cn.edu.rpm.funding.infrastructure.entity.BudgetLineEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * {@code budget_line} 表 Mapper（T-FUND-002）。
 */
@Mapper
public interface BudgetLineMapper extends BaseMapper<BudgetLineEntity> {
}
