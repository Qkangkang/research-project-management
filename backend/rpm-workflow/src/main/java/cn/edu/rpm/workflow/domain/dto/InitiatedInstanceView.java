package cn.edu.rpm.workflow.domain.dto;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * 我发起的审批实例视图（审批中心「我发起的」列表）。
 *
 * @param instanceId 审批实例 ID
 * @param bizType    业务类型（APPLICATION / EXPENSE_REQUEST / …）
 * @param bizId      业务对象 ID
 * @param status     实例状态（RUNNING / APPROVED / REJECTED / CANCELLED / …）
 * @param startedAt  发起时间
 * @param endedAt    结束时间；进行中为空
 */
public record InitiatedInstanceView(
        UUID instanceId,
        String bizType,
        UUID bizId,
        String status,
        OffsetDateTime startedAt,
        OffsetDateTime endedAt
) {

    /** 校验必填字段。 */
    public InitiatedInstanceView {
        Objects.requireNonNull(instanceId, "instanceId 不能为空");
        Objects.requireNonNull(bizType, "bizType 不能为空");
        Objects.requireNonNull(bizId, "bizId 不能为空");
        Objects.requireNonNull(status, "status 不能为空");
        Objects.requireNonNull(startedAt, "startedAt 不能为空");
    }
}
