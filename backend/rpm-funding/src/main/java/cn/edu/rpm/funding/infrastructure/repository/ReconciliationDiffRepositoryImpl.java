package cn.edu.rpm.funding.infrastructure.repository;

import cn.edu.rpm.funding.infrastructure.entity.ReconciliationDiffEntity;
import cn.edu.rpm.funding.infrastructure.mapper.ReconciliationDiffMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * {@code reconciliation_diff} 对账差异仓储实现（T-FUND-010）。
 */
@Repository
public class ReconciliationDiffRepositoryImpl implements ReconciliationDiffRepository {

    private final ReconciliationDiffMapper reconciliationDiffMapper;

    public ReconciliationDiffRepositoryImpl(ReconciliationDiffMapper reconciliationDiffMapper) {
        this.reconciliationDiffMapper = reconciliationDiffMapper;
    }

    @Override
    public void insert(ReconciliationDiffEntity diff) {
        reconciliationDiffMapper.insert(diff);
    }

    @Override
    public Optional<ReconciliationDiffEntity> findById(String id) {
        return Optional.ofNullable(reconciliationDiffMapper.selectById(id));
    }

    @Override
    public List<ReconciliationDiffEntity> findByBatchId(String batchId) {
        return reconciliationDiffMapper.selectList(new LambdaQueryWrapper<ReconciliationDiffEntity>()
                .eq(ReconciliationDiffEntity::getBatchId, batchId)
                .orderByAsc(ReconciliationDiffEntity::getCreatedAt));
    }

    @Override
    public List<ReconciliationDiffEntity> findByBatchIdAndStatus(String batchId, String status) {
        return reconciliationDiffMapper.selectList(new LambdaQueryWrapper<ReconciliationDiffEntity>()
                .eq(ReconciliationDiffEntity::getBatchId, batchId)
                .eq(ReconciliationDiffEntity::getStatus, status)
                .orderByAsc(ReconciliationDiffEntity::getCreatedAt));
    }

    @Override
    public List<ReconciliationDiffEntity> findUnresolvedByProjectId(String projectId) {
        return reconciliationDiffMapper.selectList(new LambdaQueryWrapper<ReconciliationDiffEntity>()
                .eq(ReconciliationDiffEntity::getProjectId, projectId)
                .in(ReconciliationDiffEntity::getStatus,
                        ReconciliationDiffEntity.STATUS_OPEN, ReconciliationDiffEntity.STATUS_PROCESSING)
                .orderByAsc(ReconciliationDiffEntity::getCreatedAt));
    }

    @Override
    public int update(ReconciliationDiffEntity diff) {
        return reconciliationDiffMapper.updateById(diff);
    }
}
