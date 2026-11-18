package cn.edu.rpm.workflow.api.dto;

import cn.edu.rpm.workflow.domain.dto.WorkflowTaskView;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;

/**
 * 待办任务列表项（T-WF-015，{@code GET /api/v1/workflow/tasks}）。
 *
 * @param taskId        任务 ID
 * @param instanceId    审批实例 ID
 * @param bizType       业务类型
 * @param bizId         业务对象 ID
 * @param nodeKey       节点 key
 * @param nodeName      节点名称
 * @param assigneeId    处理人用户 ID
 * @param delegatedFrom 委托人用户 ID
 * @param status        任务状态
 * @param dueAt         处理时限
 * @param roundNo       审批轮次
 * @param version       任务版本（处理时回传 expected_version）
 */
public record WorkflowTaskItem(
        @JsonProperty("task_id") String taskId,
        @JsonProperty("instance_id") String instanceId,
        @JsonProperty("biz_type") String bizType,
        @JsonProperty("biz_id") String bizId,
        @JsonProperty("node_key") String nodeKey,
        @JsonProperty("node_name") String nodeName,
        @JsonProperty("assignee_id") String assigneeId,
        @JsonProperty("delegated_from") String delegatedFrom,
        @JsonProperty("status") String status,
        @JsonProperty("due_at") OffsetDateTime dueAt,
        @JsonProperty("round_no") Integer roundNo,
        @JsonProperty("version") Integer version
) {

    /**
     * 由任务视图构造列表项。
     *
     * @param view 任务视图
     * @return 列表项
     */
    public static WorkflowTaskItem from(WorkflowTaskView view) {
        return new WorkflowTaskItem(
                view.taskId().toString(),
                view.instanceId().toString(),
                view.bizType(),
                view.bizId().toString(),
                view.nodeKey(),
                view.nodeName(),
                view.assigneeId() == null ? null : view.assigneeId().toString(),
                view.delegatedFrom() == null ? null : view.delegatedFrom().toString(),
                view.status(),
                view.dueAt(),
                view.roundNo(),
                view.version());
    }
}
