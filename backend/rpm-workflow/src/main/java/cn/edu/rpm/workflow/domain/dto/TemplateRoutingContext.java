package cn.edu.rpm.workflow.domain.dto;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * 审批模板路由上下文（T-WF-012；技术文档 03 §3.3 条件字段白名单）。
 *
 * <p>只承载白名单内的路由字段：{@code project_type}、{@code dept_id}、
 * {@code budget_total}、{@code expense_amount}、{@code duration_months}、
 * {@code is_external}、{@code applicant_title}。条件表达式只能引用这些字段。</p>
 *
 * @param projectType    项目类型（必填）
 * @param deptId         申报部门 ID；可为空
 * @param budgetTotal    项目预算总额；可为空
 * @param expenseAmount  本次支出金额；可为空
 * @param durationMonths 项目周期（月）；可为空
 * @param external       是否外部项目；可为空
 * @param applicantTitle 申请人职称；可为空
 */
public record TemplateRoutingContext(
        String projectType,
        UUID deptId,
        BigDecimal budgetTotal,
        BigDecimal expenseAmount,
        Integer durationMonths,
        Boolean external,
        String applicantTitle
) {

    /** 校验项目类型。 */
    public TemplateRoutingContext {
        Objects.requireNonNull(projectType, "projectType 不能为空");
        if (projectType.isBlank()) {
            throw new IllegalArgumentException("projectType 不能为空白");
        }
    }
}
