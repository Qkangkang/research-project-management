package cn.edu.rpm.funding.infrastructure.repository;

import cn.edu.rpm.funding.infrastructure.entity.PaymentImportRowEntity;

import java.util.List;
import java.util.Optional;

/**
 * {@code payment_import_row} 支付导入行仓储（T-FUND-008）。
 */
public interface PaymentImportRowRepository {

    /**
     * 插入导入行。
     *
     * @param row 待插入的行 PO
     */
    void insert(PaymentImportRowEntity row);

    /**
     * 按主键查询。
     *
     * @param id 行 ID
     * @return 命中的行；不存在返回空
     */
    Optional<PaymentImportRowEntity> findById(String id);

    /**
     * 按批次与行号查询（uk_payment_import_row）。
     *
     * @param batchId 批次 ID
     * @param rowNo   行号
     * @return 命中的行；不存在返回空
     */
    Optional<PaymentImportRowEntity> findByBatchIdAndRowNo(String batchId, int rowNo);

    /**
     * 按批次查询全部行（行号升序）。
     *
     * @param batchId 批次 ID
     * @return 行列表；无命中返回空列表
     */
    List<PaymentImportRowEntity> findByBatchId(String batchId);

    /**
     * 按主键更新行（行级状态、匹配支出与错误码；该表无版本列）。
     *
     * @param row 携带主键的行 PO
     * @return 受影响行数；0 表示记录不存在
     */
    int update(PaymentImportRowEntity row);
}
