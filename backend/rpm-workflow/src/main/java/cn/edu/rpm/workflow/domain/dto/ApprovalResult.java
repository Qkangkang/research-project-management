package cn.edu.rpm.workflow.domain.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 审批结果（T-WF-010；技术文档 03 §3.5）。
 *
 * <p>由应用服务在任务完成后组装，传给 {@link cn.edu.rpm.workflow.domain.port.ApprovalResultHandler}。
 * 处理器必须先在本地事务内校验业务版本仍是当前审批版本（03 §3.5 规则 1），
 * 不是则记录 {@code STALE_APPROVAL} 并抛 {@code RPM-WF-005}。</p>
 *
 * @param instanceId   审批实例 ID
 * @param bizType      业务类型
 * @param bizId        业务对象 ID
 * @param bizVersionId 本次审批的业务版本 ID
 * @param action       处理动作：APPROVED / RETURNED / REJECTED / CANCELLED
 * @param operatorId   真实操作人用户 ID
 * @param onBehalfOf   委托人用户 ID（代理审批）；非代理为空
 * @param comment      处理意见
 * @param occurredAt   动作发生时刻
 * @param requestId    请求幂等键
 * @param nextTaskIds  本次动作后新产生的待办任务 ID；无下一节点为空列表
 */
public record ApprovalResult(
        UUID instanceId,
        String bizType,
        UUID bizId,
        UUID bizVersionId,
        String action,
        UUID operatorId,
        UUID onBehalfOf,
        String comment,
        OffsetDateTime occurredAt,
        UUID requestId,
        List<UUID> nextTaskIds
) {

    /** 校验必填字段并复制下一节点列表。 */
    public ApprovalResult {
        Objects.requireNonNull(instanceId, "instanceId 不能为空");
        Objects.requireNonNull(bizType, "bizType 不能为空");
        Objects.requireNonNull(bizId, "bizId 不能为空");
        Objects.requireNonNull(bizVersionId, "bizVersionId 不能为空");
        Objects.requireNonNull(action, "action 不能为空");
        Objects.requireNonNull(operatorId, "operatorId 不能为空");
        Objects.requireNonNull(occurredAt, "occurredAt 不能为空");
        Objects.requireNonNull(requestId, "requestId 不能为空");
        nextTaskIds = nextTaskIds == null ? List.of() : List.copyOf(nextTaskIds);
    }
}
