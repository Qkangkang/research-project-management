package cn.edu.rpm.workflow.domain.dto;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * 审批实例视图（T-WF-008；技术文档 03 §3.2）。
 *
 * <p>由 {@link cn.edu.rpm.workflow.domain.port.WorkflowPort#start} 返回，
 * 是业务模块可见的实例快照，不暴露 Flowable 内部对象。</p>
 *
 * @param instanceId        业务审批实例 ID（wf_instance.id）
 * @param flowableInstanceId Flowable 流程实例 ID；启动后回填
 * @param bizType           业务类型
 * @param bizId             业务对象 ID
 * @param bizVersionId      业务版本 ID
 * @param templateVersionId 绑定的模板版本 ID
 * @param initiatorId       发起人用户 ID
 * @param status            实例状态（RUNNING / 终态）
 * @param startedAt         启动时刻
 * @param endedAt           结束时刻；运行中为空
 */
public record WorkflowInstance(
        UUID instanceId,
        String flowableInstanceId,
        String bizType,
        UUID bizId,
        UUID bizVersionId,
        UUID templateVersionId,
        UUID initiatorId,
        String status,
        OffsetDateTime startedAt,
        OffsetDateTime endedAt
) {

    /** 校验实例关键字段。 */
    public WorkflowInstance {
        Objects.requireNonNull(instanceId, "instanceId 不能为空");
        Objects.requireNonNull(bizType, "bizType 不能为空");
        Objects.requireNonNull(bizId, "bizId 不能为空");
        Objects.requireNonNull(bizVersionId, "bizVersionId 不能为空");
        Objects.requireNonNull(templateVersionId, "templateVersionId 不能为空");
        Objects.requireNonNull(initiatorId, "initiatorId 不能为空");
        Objects.requireNonNull(status, "status 不能为空");
    }
}
