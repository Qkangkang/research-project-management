package cn.edu.rpm.funding.domain.service;

import cn.edu.rpm.funding.domain.model.BudgetDecision;
import cn.edu.rpm.funding.domain.model.ExpenseContext;
import cn.edu.rpm.funding.infrastructure.entity.BudgetBalanceEntity;

import java.math.BigDecimal;

/**
 * 预算控制策略（T-FUND-012；技术文档 05 §1.3、01 §1.5 策略模式）。
 *
 * <p>按预算版本/科目的控制级别分派：{@code BLOCK} 直接拒绝、{@code WARN} 放行并提示、
 * {@code APPROVAL} 引导先走预算调整或特批。任何策略都不得允许负可用余额。</p>
 */
public interface BudgetControlPolicy {

    /**
     * 支持的控制级别。
     *
     * @return {@code BLOCK} / {@code WARN} / {@code APPROVAL}
     */
    String controlLevel();

    /**
     * 判定本次支出金额是否可提交。
     *
     * @param balance 科目余额投影（调用方已按科目加锁或只读加载）
     * @param amount  本次申请金额（正数，两位小数）
     * @param context 支出上下文
     * @return 放行结果与可选风险提示
     */
    BudgetDecision decide(BudgetBalanceEntity balance, BigDecimal amount, ExpenseContext context);
}
