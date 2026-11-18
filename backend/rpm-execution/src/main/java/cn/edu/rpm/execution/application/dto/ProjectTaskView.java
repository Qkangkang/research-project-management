package cn.edu.rpm.execution.application.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 轻任务列表项（T-EXEC-009；技术文档 09 §4.1）。
 *
 * @param taskId        任务 ID
 * @param projectId     项目 ID
 * @param title         标题
 * @param description   描述
 * @param assigneeId    处理人用户 ID
 * @param dueDate       截止日期
 * @param status        状态
 * @param deliverableId 关联交付物 ID
 * @param completedAt   完成时间
 * @param createdAt     创建时间
 * @param version       乐观锁版本
 */
public record ProjectTaskView(
        String taskId,
        String projectId,
        String title,
        String description,
        String assigneeId,
        LocalDate dueDate,
        String status,
        String deliverableId,
        OffsetDateTime completedAt,
        OffsetDateTime createdAt,
        int version
) {
}
