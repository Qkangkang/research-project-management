package cn.edu.rpm.funding.infrastructure.repository;

import cn.edu.rpm.funding.domain.model.BudgetRebuildResult;
import cn.edu.rpm.funding.infrastructure.entity.BudgetBalanceEntity;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * {@code budget_balance} 预算余额投影仓储（T-FUND-003）。
 */
public interface BudgetBalanceRepository {

    /**
     * 插入余额投影行。
     *
     * @param balance 待插入的余额 PO
     */
    void insert(BudgetBalanceEntity balance);

    /**
     * 按预算科目查询唯一余额行。
     *
     * @param budgetLineId 预算科目 ID
     * @return 命中的余额行；不存在返回空
     */
    Optional<BudgetBalanceEntity> findByBudgetLineId(String budgetLineId);

    /**
     * 按预算科目加行级排他锁查询余额行（资金事件投影入口）。
     *
     * @param budgetLineId 预算科目 ID
     * @return 加锁命中的余额行；不存在返回空
     */
    Optional<BudgetBalanceEntity> selectForUpdate(String budgetLineId);

    /**
     * 按科目 ID 集合批量查询余额行（单次 IN 查询，避免逐行查询）。
     *
     * @param budgetLineIds 预算科目 ID 集合
     * @return 余额行列表；无命中返回空列表
     */
    List<BudgetBalanceEntity> findByBudgetLineIds(Collection<String> budgetLineIds);

    /**
     * 全量重放项目资金事件并重建余额投影（T-FUND-018；TC-FUND-035、TC-FUND-036）。
     *
     * <p>以项目当前生效预算版本的科目为范围，按事件发生顺序重放九类事件；
     * 与在线投影逐项比对，存在偏差时以重放结果修正并计入结果。</p>
     *
     * @param projectId 项目 ID
     * @return 重建结果（科目数、修正数、最大偏差）
     */
    BudgetRebuildResult rebuild(String projectId);

    /**
     * 按乐观锁更新余额投影。
     *
     * @param balance 携带主键与旧版本的余额 PO
     * @return 受影响行数；0 表示版本冲突或记录不存在
     */
    int update(BudgetBalanceEntity balance);
}
