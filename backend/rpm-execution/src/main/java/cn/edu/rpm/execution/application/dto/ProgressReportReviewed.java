package cn.edu.rpm.execution.application.dto;

/**
 * 周期报告审核结果（T-EXEC-007）。
 *
 * @param reportId  报告 ID
 * @param versionId 被审核的版本 ID
 * @param status    报告状态（RETURNED / APPROVED）
 * @param decision  版本审核决策（RETURN / PASS）
 */
public record ProgressReportReviewed(
        String reportId,
        String versionId,
        String status,
        String decision
) {
}
