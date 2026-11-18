package cn.edu.rpm.funding.infrastructure.repository;

import cn.edu.rpm.funding.infrastructure.entity.BudgetLineEntity;

import java.util.List;
import java.util.Optional;

/**
 * {@code budget_line} 预算科目仓储（T-FUND-002）。
 */
public interface BudgetLineRepository {

    /**
     * 插入预算科目。
     *
     * @param line 待插入的科目 PO
     */
    void insert(BudgetLineEntity line);

    /**
     * 按主键查询预算科目。
     *
     * @param id 科目 ID
     * @return 命中的科目；不存在返回空
     */
    Optional<BudgetLineEntity> findById(String id);

    /**
     * 按预算版本查询全部科目（科目编码升序）。
     *
     * @param budgetVersionId 预算版本 ID
     * @return 科目列表；无命中返回空列表
     */
    List<BudgetLineEntity> findByBudgetVersionId(String budgetVersionId);

    /**
     * 按乐观锁更新预算科目。
     *
     * @param line 携带主键与旧版本的科目 PO
     * @return 受影响行数；0 表示版本冲突或记录不存在
     */
    int update(BudgetLineEntity line);
}
