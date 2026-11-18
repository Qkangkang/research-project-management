package cn.edu.rpm.funding.domain.service;

import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.funding.domain.model.BudgetDecision;
import cn.edu.rpm.funding.domain.model.ExpenseContext;
import cn.edu.rpm.funding.infrastructure.entity.BudgetBalanceEntity;
import cn.edu.rpm.funding.infrastructure.entity.BudgetVersionEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * {@code BLOCK} 预算控制策略（T-FUND-013；技术文档 05 §1.3）。
 *
 * <p>可用余额不足直接拒绝，抛 {@code RPM-FUND-001}；足够则放行且不产生提示。
 * 这是默认级别，最安全的控制口径。</p>
 */
@Component
public class BlockBudgetControlPolicy implements BudgetControlPolicy {

    @Override
    public String controlLevel() {
        return BudgetVersionEntity.CONTROL_BLOCK;
    }

    /**
     * 可用余额不足拒绝，足够放行。
     *
     * @param balance 科目余额投影
     * @param amount  本次申请金额
     * @param context 支出上下文
     * @return 放行结果
     * @throws BizException 可用余额不足 {@code RPM-FUND-001}
     */
    @Override
    public BudgetDecision decide(BudgetBalanceEntity balance, BigDecimal amount, ExpenseContext context) {
        Objects.requireNonNull(balance, "balance 不能为空");
        requirePositive(amount);
        if (balance.availableAmount().compareTo(amount) < 0) {
            throw new BizException(ErrorCode.FUND_001, "可用余额不足，支出被阻断");
        }
        return BudgetDecision.allow();
    }

    private static void requirePositive(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(ErrorCode.COMMON_400, "支出金额必须为正数");
        }
    }
}
