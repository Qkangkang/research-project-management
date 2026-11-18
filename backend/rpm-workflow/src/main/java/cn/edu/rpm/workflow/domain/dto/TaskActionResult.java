package cn.edu.rpm.workflow.domain.dto;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 任务处理结果（T-WF-014；技术文档 04 §3.11 响应体）。
 *
 * <p>{@code nextTaskIds} 为本次动作后进入 PENDING 的下游任务；终态动作返回空列表。
 * 重复 {@code request_id} 提交时返回首次结果快照，任务版本不再增加（TC-APP-023）。</p>
 *
 * @param taskId         任务 ID
 * @param instanceId     审批实例 ID
 * @param taskStatus     动作后的任务状态
 * @param instanceStatus 动作后的实例状态
 * @param nextTaskIds    下一节点任务 ID 列表
 */
public record TaskActionResult(
        UUID taskId,
        UUID instanceId,
        String taskStatus,
        String instanceStatus,
        List<UUID> nextTaskIds
) {

    /** 校验必填字段并复制下一节点列表。 */
    public TaskActionResult {
        Objects.requireNonNull(taskId, "taskId 不能为空");
        Objects.requireNonNull(instanceId, "instanceId 不能为空");
        Objects.requireNonNull(taskStatus, "taskStatus 不能为空");
        Objects.requireNonNull(instanceStatus, "instanceStatus 不能为空");
        nextTaskIds = nextTaskIds == null ? List.of() : List.copyOf(nextTaskIds);
    }
}
