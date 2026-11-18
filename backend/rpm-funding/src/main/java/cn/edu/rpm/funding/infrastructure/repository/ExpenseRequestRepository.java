package cn.edu.rpm.funding.infrastructure.repository;

import cn.edu.rpm.funding.infrastructure.entity.ExpenseRequestEntity;

import java.util.List;
import java.util.Optional;

/**
 * {@code expense_request} 支出申请仓储（T-FUND-004）。
 */
public interface ExpenseRequestRepository {

    /**
     * 插入支出申请。
     *
     * @param expense 待插入的支出 PO
     */
    void insert(ExpenseRequestEntity expense);

    /**
     * 按主键查询。
     *
     * @param id 支出 ID
     * @return 命中的支出；不存在返回空
     */
    Optional<ExpenseRequestEntity> findById(String id);

    /**
     * 按主键加行级排他锁查询（提交/审批入口）。
     *
     * @param id 支出 ID
     * @return 加锁命中的支出；不存在返回空
     */
    Optional<ExpenseRequestEntity> selectForUpdate(String id);

    /**
     * 按支出单号查询（uk_expense_request_no）。
     *
     * @param requestNo 支出单号
     * @return 命中的支出；不存在返回空
     */
    Optional<ExpenseRequestEntity> findByRequestNo(String requestNo);

    /**
     * 按创建幂等键查询（V04 唯一索引 uk_expense_creation_idem）。
     *
     * @param idempotencyKey 创建幂等键
     * @return 命中的支出；不存在返回空
     */
    Optional<ExpenseRequestEntity> findByIdempotencyKey(String idempotencyKey);

    /**
     * 按项目与状态查询（创建时间降序）。
     *
     * @param projectId 项目 ID
     * @param status    状态
     * @return 支出列表；无命中返回空列表
     */
    List<ExpenseRequestEntity> findByProjectIdAndStatus(String projectId, String status);

    /**
     * 按项目查询全部支出（创建时间降序，T-FUND-019 列表）。
     *
     * @param projectId 项目 ID
     * @return 支出列表；无命中返回空列表
     */
    List<ExpenseRequestEntity> findByProjectId(String projectId);

    /**
     * 按乐观锁更新支出申请。
     *
     * @param expense 携带主键与旧版本的支出 PO
     * @return 受影响行数；0 表示版本冲突或记录不存在
     */
    int update(ExpenseRequestEntity expense);
}
