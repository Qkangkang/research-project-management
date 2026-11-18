package cn.edu.rpm.execution.application.dto;

/**
 * 轻任务状态迁移结果（T-EXEC-009）。
 *
 * @param taskId  任务 ID
 * @param status  迁移后的状态
 * @param version 乐观锁版本
 */
public record ProjectTaskStateChanged(
        String taskId,
        String status,
        int version
) {
}
