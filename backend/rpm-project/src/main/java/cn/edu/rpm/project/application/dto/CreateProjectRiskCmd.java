package cn.edu.rpm.project.application.dto;

import java.time.LocalDate;

/**
 * 登记项目风险命令（T-PRJ-015；技术文档 07 §3.3、05 §5.1；TC-EXEC-016）。
 *
 * @param ownerId        责任人用户 ID（必填）
 * @param riskType       风险类型（如 PROGRESS / FUNDING / OUTPUT）
 * @param title          风险标题（必填）
 * @param description    风险描述（可空）
 * @param riskLevel      风险等级：LOW / MEDIUM / HIGH / CRITICAL
 * @param dueDate        处置期限（可空）
 * @param idempotencyKey 幂等键（可空；规则扫描生成时使用，数据库唯一兜底）
 */
public record CreateProjectRiskCmd(
        String ownerId,
        String riskType,
        String title,
        String description,
        String riskLevel,
        LocalDate dueDate,
        String idempotencyKey
) {
}
