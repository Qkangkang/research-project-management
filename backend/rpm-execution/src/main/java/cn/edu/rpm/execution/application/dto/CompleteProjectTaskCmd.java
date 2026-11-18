package cn.edu.rpm.execution.application.dto;

/**
 * 完成轻任务命令（T-EXEC-009）。
 *
 * @param deliverableId 关联交付物 ID（可空；必须属于同项目且 ACTIVE）
 */
public record CompleteProjectTaskCmd(String deliverableId) {
}
