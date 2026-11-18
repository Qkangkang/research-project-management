package cn.edu.rpm.funding.infrastructure.repository;

import cn.edu.rpm.funding.infrastructure.entity.ExpenseRequestEntity;
import cn.edu.rpm.funding.infrastructure.mapper.ExpenseRequestMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * {@code expense_request} 支出申请仓储实现（T-FUND-004）。
 */
@Repository
public class ExpenseRequestRepositoryImpl implements ExpenseRequestRepository {

    private final ExpenseRequestMapper expenseRequestMapper;

    public ExpenseRequestRepositoryImpl(ExpenseRequestMapper expenseRequestMapper) {
        this.expenseRequestMapper = expenseRequestMapper;
    }

    @Override
    public void insert(ExpenseRequestEntity expense) {
        expenseRequestMapper.insert(expense);
    }

    @Override
    public Optional<ExpenseRequestEntity> findById(String id) {
        return Optional.ofNullable(expenseRequestMapper.selectById(id));
    }

    @Override
    public Optional<ExpenseRequestEntity> selectForUpdate(String id) {
        return Optional.ofNullable(expenseRequestMapper.selectByIdForUpdate(id));
    }

    @Override
    public Optional<ExpenseRequestEntity> findByRequestNo(String requestNo) {
        return Optional.ofNullable(expenseRequestMapper.selectOne(
                new LambdaQueryWrapper<ExpenseRequestEntity>()
                        .eq(ExpenseRequestEntity::getRequestNo, requestNo)));
    }

    @Override
    public Optional<ExpenseRequestEntity> findByIdempotencyKey(String idempotencyKey) {
        return Optional.ofNullable(expenseRequestMapper.selectOne(
                new LambdaQueryWrapper<ExpenseRequestEntity>()
                        .eq(ExpenseRequestEntity::getIdempotencyKey, idempotencyKey)));
    }

    @Override
    public List<ExpenseRequestEntity> findByProjectIdAndStatus(String projectId, String status) {
        return expenseRequestMapper.selectList(new LambdaQueryWrapper<ExpenseRequestEntity>()
                .eq(ExpenseRequestEntity::getProjectId, projectId)
                .eq(ExpenseRequestEntity::getStatus, status)
                .orderByDesc(ExpenseRequestEntity::getCreatedAt));
    }

    @Override
    public List<ExpenseRequestEntity> findByProjectId(String projectId) {
        return expenseRequestMapper.selectList(new LambdaQueryWrapper<ExpenseRequestEntity>()
                .eq(ExpenseRequestEntity::getProjectId, projectId)
                .orderByDesc(ExpenseRequestEntity::getCreatedAt));
    }

    @Override
    public int update(ExpenseRequestEntity expense) {
        return expenseRequestMapper.updateById(expense);
    }
}
