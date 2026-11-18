package cn.edu.rpm.workflow.api.dto;

import cn.edu.rpm.workflow.domain.dto.ApprovalTrail;
import cn.edu.rpm.workflow.domain.dto.WorkflowInstance;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 审批轨迹响应体（T-WF-015，{@code GET /api/v1/workflow/instances/{instanceId}}）。
 *
 * @param instanceId        审批实例 ID
 * @param bizType           业务类型
 * @param bizId             业务对象 ID
 * @param bizVersionId      业务版本 ID
 * @param templateVersionId 绑定的模板版本 ID
 * @param initiatorId       发起人用户 ID
 * @param status            实例状态
 * @param startedAt         启动时刻
 * @param endedAt           结束时刻
 * @param tasks             任务轨迹（按轮次与创建时间升序）
 */
public record WorkflowInstanceResponse(
        @JsonProperty("instance_id") String instanceId,
        @JsonProperty("biz_type") String bizType,
        @JsonProperty("biz_id") String bizId,
        @JsonProperty("biz_version_id") String bizVersionId,
        @JsonProperty("template_version_id") String templateVersionId,
        @JsonProperty("initiator_id") String initiatorId,
        @JsonProperty("status") String status,
        @JsonProperty("started_at") OffsetDateTime startedAt,
        @JsonProperty("ended_at") OffsetDateTime endedAt,
        @JsonProperty("tasks") List<WorkflowTaskItem> tasks
) {

    /**
     * 由审批轨迹构造响应。
     *
     * @param trail 审批轨迹
     * @return 响应体
     */
    public static WorkflowInstanceResponse from(ApprovalTrail trail) {
        WorkflowInstance instance = trail.instance();
        return new WorkflowInstanceResponse(
                instance.instanceId().toString(),
                instance.bizType(),
                instance.bizId().toString(),
                instance.bizVersionId().toString(),
                instance.templateVersionId().toString(),
                instance.initiatorId().toString(),
                instance.status(),
                instance.startedAt(),
                instance.endedAt(),
                trail.tasks().stream().map(WorkflowTaskItem::from).toList());
    }
}
