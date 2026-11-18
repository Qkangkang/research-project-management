package cn.edu.rpm.funding.infrastructure.repository;

import cn.edu.rpm.funding.infrastructure.entity.PaymentImportRowEntity;
import cn.edu.rpm.funding.infrastructure.mapper.PaymentImportRowMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * {@code payment_import_row} 支付导入行仓储实现（T-FUND-008）。
 */
@Repository
public class PaymentImportRowRepositoryImpl implements PaymentImportRowRepository {

    private final PaymentImportRowMapper paymentImportRowMapper;

    public PaymentImportRowRepositoryImpl(PaymentImportRowMapper paymentImportRowMapper) {
        this.paymentImportRowMapper = paymentImportRowMapper;
    }

    @Override
    public void insert(PaymentImportRowEntity row) {
        paymentImportRowMapper.insert(row);
    }

    @Override
    public Optional<PaymentImportRowEntity> findById(String id) {
        return Optional.ofNullable(paymentImportRowMapper.selectById(id));
    }

    @Override
    public Optional<PaymentImportRowEntity> findByBatchIdAndRowNo(String batchId, int rowNo) {
        return Optional.ofNullable(paymentImportRowMapper.selectOne(
                new LambdaQueryWrapper<PaymentImportRowEntity>()
                        .eq(PaymentImportRowEntity::getBatchId, batchId)
                        .eq(PaymentImportRowEntity::getRowNo, rowNo)));
    }

    @Override
    public List<PaymentImportRowEntity> findByBatchId(String batchId) {
        return paymentImportRowMapper.selectList(new LambdaQueryWrapper<PaymentImportRowEntity>()
                .eq(PaymentImportRowEntity::getBatchId, batchId)
                .orderByAsc(PaymentImportRowEntity::getRowNo));
    }

    @Override
    public int update(PaymentImportRowEntity row) {
        return paymentImportRowMapper.updateById(row);
    }
}
