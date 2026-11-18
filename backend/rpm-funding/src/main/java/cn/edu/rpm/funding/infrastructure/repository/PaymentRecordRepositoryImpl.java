package cn.edu.rpm.funding.infrastructure.repository;

import cn.edu.rpm.funding.infrastructure.entity.PaymentRecordEntity;
import cn.edu.rpm.funding.infrastructure.mapper.PaymentRecordMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * {@code payment_record} 支付记录仓储实现（T-FUND-007）。
 */
@Repository
public class PaymentRecordRepositoryImpl implements PaymentRecordRepository {

    private final PaymentRecordMapper paymentRecordMapper;

    public PaymentRecordRepositoryImpl(PaymentRecordMapper paymentRecordMapper) {
        this.paymentRecordMapper = paymentRecordMapper;
    }

    @Override
    public void insert(PaymentRecordEntity payment) {
        paymentRecordMapper.insert(payment);
    }

    @Override
    public Optional<PaymentRecordEntity> findById(String id) {
        return Optional.ofNullable(paymentRecordMapper.selectById(id));
    }

    @Override
    public Optional<PaymentRecordEntity> findByExternalPaymentNo(String externalPaymentNo) {
        return Optional.ofNullable(paymentRecordMapper.selectOne(
                new LambdaQueryWrapper<PaymentRecordEntity>()
                        .eq(PaymentRecordEntity::getExternalPaymentNo, externalPaymentNo)));
    }

    @Override
    public Optional<PaymentRecordEntity> findByExternalEventId(String externalEventId) {
        return Optional.ofNullable(paymentRecordMapper.selectOne(
                new LambdaQueryWrapper<PaymentRecordEntity>()
                        .eq(PaymentRecordEntity::getExternalEventId, externalEventId)
                        .orderByAsc(PaymentRecordEntity::getCreatedAt)
                        .last("LIMIT 1")));
    }

    @Override
    public List<PaymentRecordEntity> findByPaidAtBetween(java.time.OffsetDateTime from,
                                                         java.time.OffsetDateTime to) {
        return paymentRecordMapper.selectList(new LambdaQueryWrapper<PaymentRecordEntity>()
                .ge(PaymentRecordEntity::getPaidAt, from)
                .lt(PaymentRecordEntity::getPaidAt, to)
                .orderByAsc(PaymentRecordEntity::getPaidAt));
    }

    @Override
    public List<PaymentRecordEntity> findByExpenseRequestId(String expenseRequestId) {
        return paymentRecordMapper.selectList(new LambdaQueryWrapper<PaymentRecordEntity>()
                .eq(PaymentRecordEntity::getExpenseRequestId, expenseRequestId)
                .orderByAsc(PaymentRecordEntity::getPaidAt));
    }

    @Override
    public int update(PaymentRecordEntity payment) {
        return paymentRecordMapper.updateById(payment);
    }
}
