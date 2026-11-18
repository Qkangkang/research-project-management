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
 * {@link ApprovalBudgetControlPolicy} 纯单元测试（T-FUND-015；TC-FUND-018）。
 *
 * <p>覆盖：余额不足抛 {@code RPM-FUND-006}，提示先走预算调整或特批；余额充足放行。</p>
 */
class ApprovalBudgetControlPolicyTest {

    private static final String LINE_ID = "44444444444444444444444444444444";

    private final ApprovalBudgetControlPolicy policy = new ApprovalBudgetControlPolicy();

    private final ExpenseContext context = new ExpenseContext(
            "8d7c6b5a4e3f4a2b9c1d0e2f3a4b5c6d", LINE_ID);

    @Test
    void insufficientAvailableRejectsWithFund006() {
        BudgetBalanceEntity balance = balance("10000.00");

        assertThatThrownBy(() -> policy.decide(balance, new BigDecimal("80000.00"), context))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FUND_006);
    }

    @Test
    void sufficientAvailableAllows() {
        BudgetBalanceEntity balance = balance("100000.00");

        BudgetDecision decision = policy.decide(balance, new BigDecimal("80000.00"), context);

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.hasWarning()).isFalse();
    }

    private static BudgetBalanceEntity balance(String approved) {
        BudgetBalanceEntity balance = new BudgetBalanceEntity();
        balance.setBudgetLineId(LINE_ID);
        balance.setApprovedAmount(new BigDecimal(approved));
        return balance;
    }
}
