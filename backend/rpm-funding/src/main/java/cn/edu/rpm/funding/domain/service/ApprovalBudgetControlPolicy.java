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
 * {@code APPROVAL} 预算控制策略（T-FUND-015；技术文档 05 §1.3）。
 *
 * <p>可用余额不足不直接提交，抛 {@code RPM-FUND-006}，提示先走预算调整或特批；
 * 余额充足则放行。特批通过新预算版本落地，不在余额表上直接制造负数。</p>
 */
@Component
public class ApprovalBudgetControlPolicy implements BudgetControlPolicy {

    @Override
    public String controlLevel() {
        return BudgetVersionEntity.CONTROL_APPROVAL;
    }

    /**
     * 可用余额不足提示先走预算调整/特批，足够放行。
     *
     * @param balance 科目余额投影
     * @param amount  本次申请金额
     * @param context 支出上下文
     * @return 放行结果
     * @throws BizException 需先走预算调整或特批 {@code RPM-FUND-006}
     */
    @Override
    public BudgetDecision decide(BudgetBalanceEntity balance, BigDecimal amount, ExpenseContext context) {
        Objects.requireNonNull(balance, "balance 不能为空");
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(ErrorCode.COMMON_400, "支出金额必须为正数");
        }
        if (balance.availableAmount().compareTo(amount) < 0) {
            throw new BizException(ErrorCode.FUND_006, "可用余额不足，请先走预算调整或特批");
        }
        return BudgetDecision.allow();
    }
}
