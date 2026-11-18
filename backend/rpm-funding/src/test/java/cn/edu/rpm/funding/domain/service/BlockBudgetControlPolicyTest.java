package cn.edu.rpm.funding.domain.service;

import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.funding.domain.model.BudgetDecision;
import cn.edu.rpm.funding.domain.model.ExpenseContext;
import cn.edu.rpm.funding.infrastructure.entity.BudgetBalanceEntity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link BlockBudgetControlPolicy} 纯单元测试（T-FUND-013；TC-FUND-016）。
 *
 * <p>覆盖：可用 10000.00 申请 80000.00 抛 {@code RPM-FUND-001}；
 * 可用 100000.00 申请 80000.00 放行且无提示；金额非正返回 {@code RPM-COMMON-400}。</p>
 */
class BlockBudgetControlPolicyTest {

    private final BlockBudgetControlPolicy policy = new BlockBudgetControlPolicy();

    private final ExpenseContext context = new ExpenseContext(
            "8d7c6b5a4e3f4a2b9c1d0e2f3a4b5c6d", "22222222222222222222222222222222");

    @Test
    void insufficientAvailableRejectsWithFund001() {
        BudgetBalanceEntity balance = balance("10000.00");

        assertThatThrownBy(() -> policy.decide(balance, new BigDecimal("80000.00"), context))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FUND_001);
    }

    @Test
    void sufficientAvailableAllowsWithoutWarning() {
        BudgetBalanceEntity balance = balance("100000.00");

        BudgetDecision decision = policy.decide(balance, new BigDecimal("80000.00"), context);

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.hasWarning()).isFalse();
        assertThat(balance.availableAmount()).isEqualByComparingTo("100000.00");
    }

    @Test
    void nonPositiveAmountRejectsWithCommon400() {
        BudgetBalanceEntity balance = balance("100000.00");

        assertThatThrownBy(() -> policy.decide(balance, BigDecimal.ZERO, context))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.COMMON_400);
    }

    private static BudgetBalanceEntity balance(String approved) {
        BudgetBalanceEntity balance = new BudgetBalanceEntity();
        balance.setBudgetLineId("44444444444444444444444444444444");
        balance.setApprovedAmount(new BigDecimal(approved));
        return balance;
    }
}
