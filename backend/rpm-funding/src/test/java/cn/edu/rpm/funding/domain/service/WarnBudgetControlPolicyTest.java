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
 * {@link WarnBudgetControlPolicy} 纯单元测试（T-FUND-014；TC-FUND-017、TC-FUND-019）。
 *
 * <p>覆盖：余额不足抛 {@code RPM-FUND-001}；执行率将超 80% 时放行并生成风险提示；
 * 执行率不超过 80% 时不提示；放行后余额不为负。</p>
 */
class WarnBudgetControlPolicyTest {

    private static final String LINE_ID = "44444444444444444444444444444444";

    private final WarnBudgetControlPolicy policy = new WarnBudgetControlPolicy();

    private final ExpenseContext context = new ExpenseContext(
            "8d7c6b5a4e3f4a2b9c1d0e2f3a4b5c6d", LINE_ID);

    @Test
    void insufficientAvailableRejectsWithFund001() {
        BudgetBalanceEntity balance = balance("100000.00", "95000.00");

        assertThatThrownBy(() -> policy.decide(balance, new BigDecimal("10000.00"), context))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FUND_001);
    }

    @Test
    void executionRateAboveThresholdAllowsWithWarning() {
        BudgetBalanceEntity balance = balance("100000.00", "50000.00");

        BudgetDecision decision = policy.decide(balance, new BigDecimal("40000.00"), context);

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.hasWarning()).isTrue();
        assertThat(decision.riskWarning()).contains("80%");
        assertThat(balance.availableAmount()).isEqualByComparingTo("50000.00");
    }

    @Test
    void executionRateAtThresholdAllowsWithoutWarning() {
        BudgetBalanceEntity balance = balance("100000.00", "50000.00");

        BudgetDecision decision = policy.decide(balance, new BigDecimal("30000.00"), context);

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.hasWarning()).isFalse();
    }

    @Test
    void allowedDecisionNeverDrivesBalanceNegative() {
        BudgetBalanceEntity balance = balance("100000.00", "99000.00");

        BudgetDecision decision = policy.decide(balance, new BigDecimal("1000.00"), context);

        assertThat(decision.allowed()).isTrue();
        assertThat(balance.availableAmount().compareTo(BigDecimal.ZERO)).isGreaterThanOrEqualTo(0);
    }

    private static BudgetBalanceEntity balance(String approved, String reserved) {
        BudgetBalanceEntity balance = new BudgetBalanceEntity();
        balance.setBudgetLineId(LINE_ID);
        balance.setApprovedAmount(new BigDecimal(approved));
        balance.setReservedAmount(new BigDecimal(reserved));
        return balance;
    }
}
