package cn.edu.rpm.funding.infrastructure.repository;

import cn.edu.rpm.funding.infrastructure.entity.ReconciliationDiffEntity;

import java.util.List;
import java.util.Optional;

/**
 * {@code reconciliation_diff} 对账差异仓储（T-FUND-010）。
 */
public interface ReconciliationDiffRepository {

    /**
     * 插入差异记录。
     *
     * @param diff 待插入的差异 PO
     */
    void insert(ReconciliationDiffEntity diff);

    /**
     * 按主键查询。
     *
     * @param id 差异 ID
     * @return 命中的差异；不存在返回空
     */
    Optional<ReconciliationDiffEntity> findById(String id);

    /**
     * 按批次查询全部差异。
     *
     * @param batchId 批次 ID
     * @return 差异列表，按创建时间正序
     */
    List<ReconciliationDiffEntity> findByBatchId(String batchId);

    /**
     * 按批次与状态查询差异。
     *
     * @param batchId 批次 ID
     * @param status  差异状态
     * @return 差异列表，按创建时间正序
     */
    List<ReconciliationDiffEntity> findByBatchIdAndStatus(String batchId, String status);

    /**
     * 按项目查询未结差异（{@code OPEN}/{@code PROCESSING}）。
     *
     * @param projectId 项目 ID
     * @return 未结差异列表，按创建时间正序
     */
    List<ReconciliationDiffEntity> findUnresolvedByProjectId(String projectId);

    /**
     * 按乐观锁更新差异。
     *
     * @param diff 携带主键与旧版本的差异 PO
     * @return 受影响行数；0 表示版本冲突或记录不存在
     */
    int update(ReconciliationDiffEntity diff);
}
