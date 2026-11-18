package cn.edu.rpm.funding.infrastructure.mapper;

import cn.edu.rpm.funding.infrastructure.entity.ExpenseSpecialApprovalEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * {@code expense_special_approval} 表 Mapper（T-FUND-011）。
 */
@Mapper
public interface ExpenseSpecialApprovalMapper extends BaseMapper<ExpenseSpecialApprovalEntity> {

    /**
     * 按主键加行级排他锁查询特批（审批回调串行化）。
     *
     * @param id 特批 ID
     * @return 加锁命中的特批；不存在返回 {@code null}
     */
    ExpenseSpecialApprovalEntity selectByIdForUpdate(@Param("id") String id);
}
