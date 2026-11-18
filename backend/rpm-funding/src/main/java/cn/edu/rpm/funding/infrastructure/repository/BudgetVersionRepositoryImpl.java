package cn.edu.rpm.funding.infrastructure.repository;

import cn.edu.rpm.funding.infrastructure.entity.BudgetVersionEntity;
import cn.edu.rpm.funding.infrastructure.mapper.BudgetVersionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * {@code budget_version} 预算版本仓储实现（T-FUND-001）。
 */
@Repository
public class BudgetVersionRepositoryImpl implements BudgetVersionRepository {

    private final BudgetVersionMapper budgetVersionMapper;

    public BudgetVersionRepositoryImpl(BudgetVersionMapper budgetVersionMapper) {
        this.budgetVersionMapper = budgetVersionMapper;
    }

    @Override
    public void insert(BudgetVersionEntity version) {
        budgetVersionMapper.insert(version);
    }

    @Override
    public Optional<BudgetVersionEntity> findById(String id) {
        return Optional.ofNullable(budgetVersionMapper.selectById(id));
    }

    @Override
    public List<BudgetVersionEntity> findByProjectIdAndStatus(String projectId, String status) {
        return budgetVersionMapper.selectList(new LambdaQueryWrapper<BudgetVersionEntity>()
                .eq(BudgetVersionEntity::getProjectId, projectId)
                .eq(BudgetVersionEntity::getStatus, status)
                .orderByDesc(BudgetVersionEntity::getVersionNo));
    }

    @Override
    public List<BudgetVersionEntity> findByProjectId(String projectId) {
        return budgetVersionMapper.selectList(new LambdaQueryWrapper<BudgetVersionEntity>()
                .eq(BudgetVersionEntity::getProjectId, projectId)
                .orderByDesc(BudgetVersionEntity::getVersionNo));
    }

    @Override
    public Optional<BudgetVersionEntity> findEffectiveByProjectId(String projectId) {
        return Optional.ofNullable(budgetVersionMapper.selectOne(
                new LambdaQueryWrapper<BudgetVersionEntity>()
                        .eq(BudgetVersionEntity::getProjectId, projectId)
                        .eq(BudgetVersionEntity::getStatus, BudgetVersionEntity.STATUS_EFFECTIVE)));
    }

    @Override
    public int update(BudgetVersionEntity version) {
        return budgetVersionMapper.updateById(version);
    }
}
