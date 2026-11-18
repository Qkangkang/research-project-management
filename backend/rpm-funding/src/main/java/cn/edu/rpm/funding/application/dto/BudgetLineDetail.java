package cn.edu.rpm.funding.application.dto;

import java.math.BigDecimal;

/**
 * 预算科目与余额投影视图（T-FUND-017；技术文档 05 §3.1、TC-FUND-009）。
 *
 * @param budgetLineId         科目 ID
 * @param subjectCode          科目编码
 * @param subjectName          科目名称
 * @param amount               科目预算金额
 * @param approvedAmount       批准额
 * @param reservedAmount       在审占用
 * @param approvedUnpaidAmount 已批未付
 * @param paidAmount           已付
 * @param availableAmount      可用余额
 */
public record BudgetLineDetail(
        String budgetLineId,
        String subjectCode,
        String subjectName,
        BigDecimal amount,
        BigDecimal approvedAmount,
        BigDecimal reservedAmount,
        BigDecimal approvedUnpaidAmount,
        BigDecimal paidAmount,
        BigDecimal availableAmount
) {
}
