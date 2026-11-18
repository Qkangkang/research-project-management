package cn.edu.rpm.funding.infrastructure.repository;

import cn.edu.rpm.funding.infrastructure.entity.ExpenseSpecialApprovalEntity;
import cn.edu.rpm.funding.infrastructure.mapper.ExpenseSpecialApprovalMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * {@code expense_special_approval} 支出特批仓储实现（T-FUND-011）。
 */
@Repository
public class ExpenseSpecialApprovalRepositoryImpl implements ExpenseSpecialApprovalRepository {

    private final ExpenseSpecialApprovalMapper expenseSpecialApprovalMapper;

    public ExpenseSpecialApprovalRepositoryImpl(ExpenseSpecialApprovalMapper expenseSpecialApprovalMapper) {
        this.expenseSpecialApprovalMapper = expenseSpecialApprovalMapper;
    }

    @Override
    public void insert(ExpenseSpecialApprovalEntity approval) {
        expenseSpecialApprovalMapper.insert(approval);
    }

    @Override
    public Optional<ExpenseSpecialApprovalEntity> findById(String id) {
        return Optional.ofNullable(expenseSpecialApprovalMapper.selectById(id));
    }

    @Override
    public Optional<ExpenseSpecialApprovalEntity> selectForUpdate(String id) {
        return Optional.ofNullable(expenseSpecialApprovalMapper.selectByIdForUpdate(id));
    }

    @Override
    public List<ExpenseSpecialApprovalEntity> findByExpenseRequestIdAndStatus(String expenseRequestId,
                                                                             String status) {
        return expenseSpecialApprovalMapper.selectList(new LambdaQueryWrapper<ExpenseSpecialApprovalEntity>()
                .eq(ExpenseSpecialApprovalEntity::getExpenseRequestId, expenseRequestId)
                .eq(ExpenseSpecialApprovalEntity::getStatus, status)
                .orderByDesc(ExpenseSpecialApprovalEntity::getCreatedAt));
    }

    @Override
    public List<ExpenseSpecialApprovalEntity> findByExpenseRequestId(String expenseRequestId) {
        return expenseSpecialApprovalMapper.selectList(new LambdaQueryWrapper<ExpenseSpecialApprovalEntity>()
                .eq(ExpenseSpecialApprovalEntity::getExpenseRequestId, expenseRequestId)
                .orderByDesc(ExpenseSpecialApprovalEntity::getCreatedAt));
    }

    @Override
    public List<ExpenseSpecialApprovalEntity> findApprovedExpiringBefore(LocalDate date) {
        return expenseSpecialApprovalMapper.selectList(new LambdaQueryWrapper<ExpenseSpecialApprovalEntity>()
                .eq(ExpenseSpecialApprovalEntity::getStatus, ExpenseSpecialApprovalEntity.STATUS_APPROVED)
                .lt(ExpenseSpecialApprovalEntity::getValidUntil, date)
                .orderByAsc(ExpenseSpecialApprovalEntity::getValidUntil));
    }

    @Override
    public Optional<ExpenseSpecialApprovalEntity> findByWfInstanceId(String wfInstanceId) {
        return Optional.ofNullable(expenseSpecialApprovalMapper.selectOne(
                new LambdaQueryWrapper<ExpenseSpecialApprovalEntity>()
                        .eq(ExpenseSpecialApprovalEntity::getWfInstanceId, wfInstanceId)));
    }

    @Override
    public int update(ExpenseSpecialApprovalEntity approval) {
        return expenseSpecialApprovalMapper.updateById(approval);
    }
}
