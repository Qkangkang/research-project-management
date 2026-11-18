package cn.edu.rpm.execution.application.dto;

/**
 * 轻任务创建结果（T-EXEC-009；TC-TSK-001）。
 *
 * @param taskId     任务 ID
 * @param status     状态（{@code TODO}）
 * @param assigneeId 处理人用户 ID
 * @param version    乐观锁版本
 */
public record ProjectTaskCreated(
        String taskId,
        String status,
        String assigneeId,
        int version
) {
}
