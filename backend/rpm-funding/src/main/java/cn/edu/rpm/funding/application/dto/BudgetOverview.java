package cn.edu.rpm.funding.application.dto;

import java.util.List;

/**
 * 项目预算总览（T-FUND-017；TC-FUND-009、TC-FUND-010）。
 *
 * @param versions        版本摘要列表（版本号降序；无预算时为空列表）
 * @param effectiveBudget 当前生效版本明细；无生效版本时为 {@code null}
 */
public record BudgetOverview(List<BudgetVersionSummary> versions, BudgetVersionDetail effectiveBudget) {
}
