package cn.edu.rpm.funding.domain.model;

/**
 * 预算控制判定的支出上下文（T-FUND-012；技术文档 05 §1.3）。
 *
 * <p>只承载策略判定所需的最小标识，不承载支出正文；供告警文案与审计定位使用。</p>
 *
 * @param projectId        项目 ID
 * @param expenseRequestId 支出申请 ID（草稿阶段可为空）
 */
public record ExpenseContext(String projectId, String expenseRequestId) {
}
