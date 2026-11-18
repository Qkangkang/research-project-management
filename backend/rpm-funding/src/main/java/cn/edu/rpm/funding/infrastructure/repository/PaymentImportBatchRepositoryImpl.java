package cn.edu.rpm.funding.infrastructure.repository;

import cn.edu.rpm.funding.infrastructure.entity.PaymentImportBatchEntity;
import cn.edu.rpm.funding.infrastructure.mapper.PaymentImportBatchMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * {@code payment_import_batch} 支付导入批次仓储实现（T-FUND-008）。
 */
@Repository
public class PaymentImportBatchRepositoryImpl implements PaymentImportBatchRepository {

    private final PaymentImportBatchMapper paymentImportBatchMapper;

    public PaymentImportBatchRepositoryImpl(PaymentImportBatchMapper paymentImportBatchMapper) {
        this.paymentImportBatchMapper = paymentImportBatchMapper;
    }

    @Override
    public void insert(PaymentImportBatchEntity batch) {
        paymentImportBatchMapper.insert(batch);
    }

    @Override
    public Optional<PaymentImportBatchEntity> findById(String id) {
        return Optional.ofNullable(paymentImportBatchMapper.selectById(id));
    }

    @Override
    public Optional<PaymentImportBatchEntity> findByBatchNo(String batchNo) {
        return Optional.ofNullable(paymentImportBatchMapper.selectOne(
                new LambdaQueryWrapper<PaymentImportBatchEntity>()
                        .eq(PaymentImportBatchEntity::getBatchNo, batchNo)));
    }

    @Override
    public int update(PaymentImportBatchEntity batch) {
        return paymentImportBatchMapper.updateById(batch);
    }
}
