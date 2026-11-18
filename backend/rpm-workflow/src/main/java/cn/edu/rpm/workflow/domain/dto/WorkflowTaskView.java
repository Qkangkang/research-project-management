package cn.edu.rpm.workflow.domain.dto;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * 审批任务视图（T-WF-008；技术文档 03 §3.2）。
 *
 * <p>用于工作台待办列表与审批轨迹；{@code version} 供前端在提交处理时
 * 回传 {@link CompleteTaskCmd#expectedVersion()}，实现乐观锁校验。</p>
 *
 * @param taskId        任务 ID
 * @param instanceId    审批实例 ID
 * @param bizType       业务类型
 * @param bizId         业务对象 ID
 * @param nodeKey       节点 key
 * @param nodeName      节点名称
 * @param assigneeId    处理人用户 ID
 * @param delegatedFrom 委托人用户 ID（代理任务）；非代理为空
 * @param status        任务状态
 * @param dueAt         处理时限；为空表示不设时限
 * @param roundNo       审批轮次
 * @param version       任务版本（乐观锁）
 */
public record WorkflowTaskView(
        UUID taskId,
        UUID instanceId,
        String bizType,
        UUID bizId,
        String nodeKey,
        String nodeName,
        UUID assigneeId,
        UUID delegatedFrom,
        String status,
        OffsetDateTime dueAt,
        Integer roundNo,
        Integer version
) {

    /** 校验任务关键字段。 */
    public WorkflowTaskView {
        Objects.requireNonNull(taskId, "taskId 不能为空");
        Objects.requireNonNull(instanceId, "instanceId 不能为空");
        Objects.requireNonNull(bizType, "bizType 不能为空");
        Objects.requireNonNull(bizId, "bizId 不能为空");
        Objects.requireNonNull(nodeKey, "nodeKey 不能为空");
        Objects.requireNonNull(status, "status 不能为空");
        Objects.requireNonNull(version, "version 不能为空");
    }
}
