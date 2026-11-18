package cn.edu.rpm.funding.infrastructure.repository;

import cn.edu.rpm.funding.infrastructure.entity.ReconciliationBatchEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * {@code reconciliation_batch} 对账批次仓储（T-FUND-009）。
 */
public interface ReconciliationBatchRepository {

    /**
     * 插入对账批次。
     *
     * @param batch 待插入的批次 PO
     */
    void insert(ReconciliationBatchEntity batch);

    /**
     * 按主键查询。
     *
     * @param id 批次 ID
     * @return 命中的批次；不存在返回空
     */
    Optional<ReconciliationBatchEntity> findById(String id);

    /**
     * 按批次号查询（uk_reconciliation_batch_no）。
     *
     * @param batchNo 批次号
     * @return 命中的批次；不存在返回空
     */
    Optional<ReconciliationBatchEntity> findByBatchNo(String batchNo);

    /**
     * 按对账期间查询批次（起止日期均相等）。
     *
     * @param periodStart 期间起
     * @param periodEnd   期间止
     * @return 批次列表，按开始时间倒序
     */
    List<ReconciliationBatchEntity> findByPeriod(LocalDate periodStart, LocalDate periodEnd);

    /**
     * 查询全部对账批次（开始时间倒序）。
     *
     * @return 批次列表
     */
    List<ReconciliationBatchEntity> findAll();

    /**
     * 按乐观锁更新批次。
     *
     * @param batch 携带主键与旧版本的批次 PO
     * @return 受影响行数；0 表示版本冲突或记录不存在
     */
    int update(ReconciliationBatchEntity batch);
}
