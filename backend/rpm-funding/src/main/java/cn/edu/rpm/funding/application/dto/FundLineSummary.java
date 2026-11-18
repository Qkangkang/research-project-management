package cn.edu.rpm.funding.application.dto;

import java.math.BigDecimal;

/**
 * 经费汇总科目行（T-FUND-023；TC-FUND-037）。
 *
 * @param budgetLineId 科目 ID
 * @param subjectCode  科目编码
 * @param subjectName  科目名称
 * @param approvedAmount 批准额
 * @param reservedAmount 在审占用
 * @param approvedUnpaidAmount 已批未付
 * @param paidAmount   已付
 * @param availableAmount 可用余额
 */
public record FundLineSummary(
        String budgetLineId,
        String subjectCode,
        String subjectName,
        BigDecimal approvedAmount,
        BigDecimal reservedAmount,
        BigDecimal approvedUnpaidAmount,
        BigDecimal paidAmount,
        BigDecimal availableAmount
) {
}
