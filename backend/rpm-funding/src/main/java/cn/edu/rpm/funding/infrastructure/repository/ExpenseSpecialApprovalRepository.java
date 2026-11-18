package cn.edu.rpm.funding.infrastructure.repository;

import cn.edu.rpm.funding.infrastructure.entity.ExpenseSpecialApprovalEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * {@code expense_special_approval} 支出特批仓储（T-FUND-011）。
 */
public interface ExpenseSpecialApprovalRepository {

    /**
     * 插入特批申请。
     *
     * @param approval 待插入的特批 PO
     */
    void insert(ExpenseSpecialApprovalEntity approval);

    /**
     * 按主键查询。
     *
     * @param id 特批 ID
     * @return 命中的特批；不存在返回空
     */
    Optional<ExpenseSpecialApprovalEntity> findById(String id);

    /**
     * 按主键加行级排他锁查询（特批审批回调入口）。
     *
     * @param id 特批 ID
     * @return 加锁命中的特批；不存在返回空
     */
    Optional<ExpenseSpecialApprovalEntity> selectForUpdate(String id);

    /**
     * 按支出与状态查询特批。
     *
     * @param expenseRequestId 支出 ID
     * @param status           特批状态
     * @return 特批列表，按创建时间倒序
     */
    List<ExpenseSpecialApprovalEntity> findByExpenseRequestIdAndStatus(String expenseRequestId, String status);

    /**
     * 按支出查询全部特批（创建时间倒序）。
     *
     * @param expenseRequestId 支出 ID
     * @return 特批列表；无命中返回空列表
     */
    List<ExpenseSpecialApprovalEntity> findByExpenseRequestId(String expenseRequestId);

    /**
     * 查询有效期早于指定日期且仍为 {@code APPROVED} 的特批（到期任务）。
     *
     * @param date 业务日期（不含）
     * @return 到期特批列表；无命中返回空列表
     */
    List<ExpenseSpecialApprovalEntity> findApprovedExpiringBefore(LocalDate date);

    /**
     * 按审批实例 ID 查询特批。
     *
     * @param wfInstanceId 审批实例 ID
     * @return 命中的特批；不存在返回空
     */
    Optional<ExpenseSpecialApprovalEntity> findByWfInstanceId(String wfInstanceId);

    /**
     * 按乐观锁更新特批。
     *
     * @param approval 携带主键与旧版本的特批 PO
     * @return 受影响行数；0 表示版本冲突或记录不存在
     */
    int update(ExpenseSpecialApprovalEntity approval);
}
