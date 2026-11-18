package cn.edu.rpm.funding.infrastructure.repository;

import cn.edu.rpm.funding.infrastructure.entity.BudgetLineEntity;
import cn.edu.rpm.funding.infrastructure.mapper.BudgetLineMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * {@code budget_line} 预算科目仓储实现（T-FUND-002）。
 */
@Repository
public class BudgetLineRepositoryImpl implements BudgetLineRepository {

    private final BudgetLineMapper budgetLineMapper;

    public BudgetLineRepositoryImpl(BudgetLineMapper budgetLineMapper) {
        this.budgetLineMapper = budgetLineMapper;
    }

    @Override
    public void insert(BudgetLineEntity line) {
        budgetLineMapper.insert(line);
    }

    @Override
    public Optional<BudgetLineEntity> findById(String id) {
        return Optional.ofNullable(budgetLineMapper.selectById(id));
    }

    @Override
    public List<BudgetLineEntity> findByBudgetVersionId(String budgetVersionId) {
        return budgetLineMapper.selectList(new LambdaQueryWrapper<BudgetLineEntity>()
                .eq(BudgetLineEntity::getBudgetVersionId, budgetVersionId)
                .orderByAsc(BudgetLineEntity::getSubjectCode));
    }

    @Override
    public int update(BudgetLineEntity line) {
        return budgetLineMapper.updateById(line);
    }
}
