package cn.edu.rpm.funding.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 创建预算草稿命令（T-FUND-017；技术文档 05 §3.1）。
 *
 * @param sourceType    来源类型（GRANT / CONTRACT / MATCHING / ADJUSTMENT / MIGRATION）
 * @param controlLevel  控制级别（BLOCK / WARN / APPROVAL）
 * @param effectiveFrom 生效开始日期
 * @param effectiveTo   生效结束日期
 * @param lines         科目明细（非空，科目编码不重复）
 * @param totalAmount   预算总额（必须等于明细合计）
 */
public record CreateBudgetDraftCmd(
        String sourceType,
        String controlLevel,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        List<BudgetLineCmd> lines,
        BigDecimal totalAmount
) {
}
