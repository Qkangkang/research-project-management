package cn.edu.rpm.funding.domain.service;

import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.funding.domain.model.BudgetDecision;
import cn.edu.rpm.funding.domain.model.ExpenseContext;
import cn.edu.rpm.funding.infrastructure.entity.BudgetBalanceEntity;
import cn.edu.rpm.funding.infrastructure.entity.BudgetVersionEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * {@code WARN} 预算控制策略（T-FUND-014；技术文档 05 §1.3、07 §4.2）。
 *
 * <p>可用余额不足仍拒绝（{@code RPM-FUND-001}，不允许负余额）；余额足够但本笔支出
 * 将使科目执行率严格超过 80% 时放行并返回风险提示（TC-FUND-017、TC-FUND-019）。</p>
 */
@Component
public class WarnBudgetControlPolicy implements BudgetControlPolicy {

    /** 执行率预警阈值（百分比）。 */
    static final BigDecimal WARN_THRESHOLD_PERCENT = new BigDecimal("80");

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    @Override
    public String controlLevel() {
        return BudgetVersionEntity.CONTROL_WARN;
    }

    /**
     * 余额不足拒绝；执行率将超过阈值时放行并提示。
     *
     * @param balance 科目余额投影
     * @param amount  本次申请金额
     * @param context 支出上下文
     * @return 放行结果（可能携带风险提示）
     * @throws BizException 可用余额不足 {@code RPM-FUND-001}
     */
    @Override
    public BudgetDecision decide(BudgetBalanceEntity balance, BigDecimal amount, ExpenseContext context) {
        Objects.requireNonNull(balance, "balance 不能为空");
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(ErrorCode.COMMON_400, "支出金额必须为正数");
        }
        if (balance.availableAmount().compareTo(amount) < 0) {
            throw new BizException(ErrorCode.FUND_001, "可用余额不足，支出被拒绝");
        }

        BigDecimal executionRate = executionRateAfter(balance, amount);
        if (executionRate.compareTo(WARN_THRESHOLD_PERCENT) > 0) {
            return BudgetDecision.allowWithWarning(
                    "科目执行率将达到 " + executionRate.setScale(1, RoundingMode.HALF_UP)
                            + "%，超过 80% 预警阈值");
        }
        return BudgetDecision.allow();
    }

    private static BigDecimal executionRateAfter(BudgetBalanceEntity balance, BigDecimal amount) {
        BigDecimal approved = balance.getApprovedAmount();
        if (approved == null || approved.compareTo(BigDecimal.ZERO) <= 0) {
            return ONE_HUNDRED;
        }
        BigDecimal occupied = balance.getReservedAmount()
                .add(balance.getApprovedUnpaidAmount())
                .add(balance.getPaidAmount())
                .add(amount);
        return occupied.multiply(ONE_HUNDRED).divide(approved, 4, RoundingMode.HALF_UP);
    }
}
