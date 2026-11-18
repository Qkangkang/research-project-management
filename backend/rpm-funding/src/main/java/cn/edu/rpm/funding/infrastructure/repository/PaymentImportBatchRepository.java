package cn.edu.rpm.funding.infrastructure.repository;

import cn.edu.rpm.funding.infrastructure.entity.PaymentImportBatchEntity;

import java.util.Optional;

/**
 * {@code payment_import_batch} 支付导入批次仓储（T-FUND-008）。
 */
public interface PaymentImportBatchRepository {

    /**
     * 插入导入批次。
     *
     * @param batch 待插入的批次 PO
     */
    void insert(PaymentImportBatchEntity batch);

    /**
     * 按主键查询。
     *
     * @param id 批次 ID
     * @return 命中的批次；不存在返回空
     */
    Optional<PaymentImportBatchEntity> findById(String id);

    /**
     * 按批次号查询（uk_payment_import_batch_no）。
     *
     * @param batchNo 批次号
     * @return 命中的批次；不存在返回空
     */
    Optional<PaymentImportBatchEntity> findByBatchNo(String batchNo);

    /**
     * 按乐观锁更新批次。
     *
     * @param batch 携带主键与旧版本的批次 PO
     * @return 受影响行数；0 表示版本冲突或记录不存在
     */
    int update(PaymentImportBatchEntity batch);
}
