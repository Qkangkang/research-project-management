package cn.edu.rpm.project.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * 项目详情页签摘要（T-PRJ-011；技术文档 04 §3.7 {@code summary} 对象）。
 *
 * <p>口径与 V02 物化视图一致，保证详情页签与报表统计同源：</p>
 * <ul>
 *   <li>{@code milestone_total} / {@code milestone_completed}：按当前生效基线统计，
 *       已取消（{@code CANCELLED}）里程碑不计入总数；</li>
 *   <li>{@code report_due}：到期未提交（{@code DRAFT}/{@code RETURNED} 且
 *       {@code due_date <= CURRENT_DATE}）的周期报告数；</li>
 *   <li>{@code budget_total}：生效预算版本总额；</li>
 *   <li>{@code available_amount}：生效预算各科目
 *       {@code approved - reserved - approved_unpaid - paid} 之和（V02 口径）；</li>
 *   <li>{@code output_count}：关联有效且未撤回的成果数；</li>
 *   <li>{@code open_risk_count}：{@code OPEN}/{@code PROCESSING}/{@code ESCALATED} 风险数。</li>
 * </ul>
 *
 * @param milestoneTotal     里程碑总数
 * @param milestoneCompleted 已完成里程碑数
 * @param reportDue          到期未提交报告数
 * @param budgetTotal        预算总额
 * @param availableAmount    可用余额
 * @param outputCount        成果数
 * @param openRiskCount      未关闭风险数
 */
public record ProjectSummary(
        @JsonProperty("milestone_total") long milestoneTotal,
        @JsonProperty("milestone_completed") long milestoneCompleted,
        @JsonProperty("report_due") long reportDue,
        @JsonProperty("budget_total") BigDecimal budgetTotal,
        @JsonProperty("available_amount") BigDecimal availableAmount,
        @JsonProperty("output_count") long outputCount,
        @JsonProperty("open_risk_count") long openRiskCount
) {
}
