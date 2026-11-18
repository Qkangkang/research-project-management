package cn.edu.rpm.funding.infrastructure.repository;

import cn.edu.rpm.funding.infrastructure.entity.ExpenseRequestVersionEntity;

import java.util.List;
import java.util.Optional;

/**
 * {@code expense_request_version} 不可变版本仓储（T-FUND-005）。
 *
 * <p>只提供追加与查询方法，不提供更新/删除（版本不可变）。</p>
 */
public interface ExpenseRequestVersionRepository {

    /**
     * 追加支出版本。
     *
     * @param version 待插入的版本 PO
     */
    void insert(ExpenseRequestVersionEntity version);

    /**
     * 按主键查询版本。
     *
     * @param id 版本 ID
     * @return 命中的版本；不存在返回空
     */
    Optional<ExpenseRequestVersionEntity> findById(String id);

    /**
     * 按支出与版本号查询唯一版本（uk_expense_version_no）。
     *
     * @param expenseRequestId 支出 ID
     * @param versionNo        版本号
     * @return 命中的版本；不存在返回空
     */
    Optional<ExpenseRequestVersionEntity> findByExpenseRequestIdAndVersionNo(
            String expenseRequestId, int versionNo);

    /**
     * 按支出查询全部版本（版本号降序）。
     *
     * @param expenseRequestId 支出 ID
     * @return 版本列表；无命中返回空列表
     */
    List<ExpenseRequestVersionEntity> findByExpenseRequestId(String expenseRequestId);

    /**
     * 按提交幂等键查询版本（V04 唯一索引 uk_expense_version_submit_idem）。
     *
     * @param submitIdempotencyKey 提交幂等键
     * @return 命中的版本；不存在返回空
     */
    Optional<ExpenseRequestVersionEntity> findBySubmitIdempotencyKey(String submitIdempotencyKey);
}
