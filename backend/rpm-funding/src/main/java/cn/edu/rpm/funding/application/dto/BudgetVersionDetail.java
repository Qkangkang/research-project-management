package cn.edu.rpm.funding.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 预算版本明细视图（T-FUND-017；TC-FUND-007、TC-FUND-009）。
 *
 * @param budgetVersionId 预算版本 ID
 * @param projectId       项目 ID
 * @param versionNo       版本号
 * @param totalAmount     预算总额
 * @param status          版本状态
 * @param controlLevel    控制级别
 * @param effectiveFrom   生效开始日期
 * @param effectiveTo     生效结束日期
 * @param lines           科目与余额明细
 */
public record BudgetVersionDetail(
        String budgetVersionId,
        String projectId,
        Integer versionNo,
        BigDecimal totalAmount,
        String status,
        String controlLevel,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        List<BudgetLineDetail> lines
) {
}
