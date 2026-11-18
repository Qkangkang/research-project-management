package cn.edu.rpm.execution.application.dto;

import java.time.LocalDate;

/**
 * 创建并分派轻任务命令（T-EXEC-009；技术文档 09 §4）。
 *
 * @param title         任务标题（≤256 字符）
 * @param description   任务描述（≤2000 字符，可空）
 * @param assigneeId    处理人用户 ID（必须是项目有效成员）
 * @param dueDate       截止日期（可空）
 * @param deliverableId 关联交付物 ID（可空；必须属于同项目且 ACTIVE）
 */
public record CreateProjectTaskCmd(
        String title,
        String description,
        String assigneeId,
        LocalDate dueDate,
        String deliverableId
) {
}
