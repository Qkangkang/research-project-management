package cn.edu.rpm.workflow.domain.dto;

import java.util.List;
import java.util.Objects;

/**
 * 审批轨迹（T-WF-015；技术文档 04 §2.3 {@code GET /workflow/instances/{instanceId}}）。
 *
 * @param instance 审批实例快照
 * @param tasks    实例下的全部任务（按轮次与创建时间升序）
 */
public record ApprovalTrail(
        WorkflowInstance instance,
        List<WorkflowTaskView> tasks
) {

    /** 校验实例并复制任务列表。 */
    public ApprovalTrail {
        Objects.requireNonNull(instance, "instance 不能为空");
        tasks = tasks == null ? List.of() : List.copyOf(tasks);
    }
}
