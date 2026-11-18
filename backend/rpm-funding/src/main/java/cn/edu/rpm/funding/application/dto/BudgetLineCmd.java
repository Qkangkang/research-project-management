package cn.edu.rpm.funding.application.dto;

import java.math.BigDecimal;

/**
 * 预算科目草稿行（T-FUND-017；技术文档 05 §3.1）。
 *
 * @param subjectCode 科目编码（版本内唯一）
 * @param subjectName 科目名称
 * @param amount      科目金额（正数）
 */
public record BudgetLineCmd(String subjectCode, String subjectName, BigDecimal amount) {
}
