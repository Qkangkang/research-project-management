package cn.edu.rpm.project.application.dto;

import java.time.OffsetDateTime;

/**
 * 负责人交接发起结果（T-PRJ-020）。
 *
 * @param handoverId        交接单 ID
 * @param status            交接状态（发起后固定为 IN_REVIEW）
 * @param effectiveAt       交接生效时间
 * @param workflowInstanceId 审批实例 ID
 */
public record ProjectHandoverInitiated(
        String handoverId,
        String status,
        OffsetDateTime effectiveAt,
        String workflowInstanceId
) {
}
