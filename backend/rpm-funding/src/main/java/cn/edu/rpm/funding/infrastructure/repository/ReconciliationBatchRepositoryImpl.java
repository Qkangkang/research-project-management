package cn.edu.rpm.funding.infrastructure.repository;

import cn.edu.rpm.funding.infrastructure.entity.ReconciliationBatchEntity;
import cn.edu.rpm.funding.infrastructure.mapper.ReconciliationBatchMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * {@code reconciliation_batch} 对账批次仓储实现（T-FUND-009）。
 */
@Repository
public class ReconciliationBatchRepositoryImpl implements ReconciliationBatchRepository {

    private final ReconciliationBatchMapper reconciliationBatchMapper;

    public ReconciliationBatchRepositoryImpl(ReconciliationBatchMapper reconciliationBatchMapper) {
        this.reconciliationBatchMapper = reconciliationBatchMapper;
    }

    @Override
    public void insert(ReconciliationBatchEntity batch) {
        reconciliationBatchMapper.insert(batch);
    }

    @Override
    public Optional<ReconciliationBatchEntity> findById(String id) {
        return Optional.ofNullable(reconciliationBatchMapper.selectById(id));
    }

    @Override
    public Optional<ReconciliationBatchEntity> findByBatchNo(String batchNo) {
        return Optional.ofNullable(reconciliationBatchMapper.selectOne(
                new LambdaQueryWrapper<ReconciliationBatchEntity>()
                        .eq(ReconciliationBatchEntity::getBatchNo, batchNo)));
    }

    @Override
    public List<ReconciliationBatchEntity> findByPeriod(LocalDate periodStart, LocalDate periodEnd) {
        return reconciliationBatchMapper.selectList(new LambdaQueryWrapper<ReconciliationBatchEntity>()
                .eq(ReconciliationBatchEntity::getPeriodStart, periodStart)
                .eq(ReconciliationBatchEntity::getPeriodEnd, periodEnd)
                .orderByDesc(ReconciliationBatchEntity::getStartedAt));
    }

    @Override
    public List<ReconciliationBatchEntity> findAll() {
        return reconciliationBatchMapper.selectList(new LambdaQueryWrapper<ReconciliationBatchEntity>()
                .orderByDesc(ReconciliationBatchEntity::getStartedAt));
    }

    @Override
    public int update(ReconciliationBatchEntity batch) {
        return reconciliationBatchMapper.updateById(batch);
    }
}
