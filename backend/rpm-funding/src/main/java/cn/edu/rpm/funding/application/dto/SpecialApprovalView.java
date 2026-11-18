package cn.edu.rpm.funding.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 特批视图（T-FUND-024、T-FUND-025）。
 *
 * @param approvalId      特批 ID
 * @param expenseRequestId 支出 ID
 * @param expenseVersionId 支出提交版本 ID
 * @param specialType     特批类型
 * @param overAmount      超出金额
 * @param reason          理由
 * @param status          状态
 * @param wfInstanceId    审批实例 ID
 * @param approverId      审批人
 * @param approvedAt      批准时间
 * @param validUntil      有效期
 * @param adjustmentVersionId 预算调整版本 ID
 */
public record SpecialApprovalView(
        String approvalId,
        String expenseRequestId,
        String expenseVersionId,
        String specialType,
        BigDecimal overAmount,
        String reason,
        String status,
        String wfInstanceId,
        String approverId,
        OffsetDateTime approvedAt,
        LocalDate validUntil,
        String adjustmentVersionId
) {
}
