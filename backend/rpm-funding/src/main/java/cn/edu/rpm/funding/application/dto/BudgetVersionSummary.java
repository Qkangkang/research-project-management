package cn.edu.rpm.funding.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 预算版本摘要（T-FUND-017；TC-FUND-010）。
 *
 * @param budgetVersionId 预算版本 ID
 * @param versionNo       版本号
 * @param totalAmount     预算总额
 * @param status          版本状态
 * @param controlLevel    控制级别
 * @param effectiveFrom   生效开始日期
 * @param effectiveTo     生效结束日期
 */
public record BudgetVersionSummary(
        String budgetVersionId,
        Integer versionNo,
        BigDecimal totalAmount,
        String status,
        String controlLevel,
        LocalDate effectiveFrom,
        LocalDate effectiveTo
) {
}
