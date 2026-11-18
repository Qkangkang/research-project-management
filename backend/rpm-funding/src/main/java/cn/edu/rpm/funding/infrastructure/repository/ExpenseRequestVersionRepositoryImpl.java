package cn.edu.rpm.funding.infrastructure.repository;

import cn.edu.rpm.funding.infrastructure.entity.ExpenseRequestVersionEntity;
import cn.edu.rpm.funding.infrastructure.mapper.ExpenseRequestVersionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * {@code expense_request_version} 不可变版本仓储实现（T-FUND-005）。
 */
@Repository
public class ExpenseRequestVersionRepositoryImpl implements ExpenseRequestVersionRepository {

    private final ExpenseRequestVersionMapper expenseRequestVersionMapper;

    public ExpenseRequestVersionRepositoryImpl(
            ExpenseRequestVersionMapper expenseRequestVersionMapper) {
        this.expenseRequestVersionMapper = expenseRequestVersionMapper;
    }

    @Override
    public void insert(ExpenseRequestVersionEntity version) {
        expenseRequestVersionMapper.insert(version);
    }

    @Override
    public Optional<ExpenseRequestVersionEntity> findById(String id) {
        return Optional.ofNullable(expenseRequestVersionMapper.selectById(id));
    }

    @Override
    public Optional<ExpenseRequestVersionEntity> findByExpenseRequestIdAndVersionNo(
            String expenseRequestId, int versionNo) {
        return Optional.ofNullable(expenseRequestVersionMapper.selectOne(
                new LambdaQueryWrapper<ExpenseRequestVersionEntity>()
                        .eq(ExpenseRequestVersionEntity::getExpenseRequestId, expenseRequestId)
                        .eq(ExpenseRequestVersionEntity::getVersionNo, versionNo)));
    }

    @Override
    public List<ExpenseRequestVersionEntity> findByExpenseRequestId(String expenseRequestId) {
        return expenseRequestVersionMapper.selectList(
                new LambdaQueryWrapper<ExpenseRequestVersionEntity>()
                        .eq(ExpenseRequestVersionEntity::getExpenseRequestId, expenseRequestId)
                        .orderByDesc(ExpenseRequestVersionEntity::getVersionNo));
    }

    @Override
    public Optional<ExpenseRequestVersionEntity> findBySubmitIdempotencyKey(String submitIdempotencyKey) {
        return Optional.ofNullable(expenseRequestVersionMapper.selectOne(
                new LambdaQueryWrapper<ExpenseRequestVersionEntity>()
                        .eq(ExpenseRequestVersionEntity::getSubmitIdempotencyKey, submitIdempotencyKey)));
    }
}
