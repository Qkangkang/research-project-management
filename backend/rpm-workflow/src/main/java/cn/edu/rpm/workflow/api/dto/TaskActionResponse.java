package cn.edu.rpm.workflow.api.dto;

import cn.edu.rpm.workflow.domain.dto.TaskActionResult;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.UUID;

/**
 * 审批任务处理响应体（T-WF-015；技术文档 04 §3.11）。
 *
 * @param taskId         任务 ID
 * @param instanceId     审批实例 ID
 * @param taskStatus     动作后的任务状态
 * @param instanceStatus 动作后的实例状态
 * @param nextTaskIds    下一节点任务 ID 列表
 */
public record TaskActionResponse(
        @JsonProperty("task_id") String taskId,
        @JsonProperty("instance_id") String instanceId,
        @JsonProperty("task_status") String taskStatus,
        @JsonProperty("instance_status") String instanceStatus,
        @JsonProperty("next_task_ids") List<String> nextTaskIds
) {

    /**
     * 由领域结果构造响应。
     *
     * @param result 任务处理结果
     * @return 响应体
     */
    public static TaskActionResponse from(TaskActionResult result) {
        return new TaskActionResponse(
                result.taskId().toString(),
                result.instanceId().toString(),
                result.taskStatus(),
                result.instanceStatus(),
                result.nextTaskIds().stream().map(UUID::toString).toList());
    }
}
