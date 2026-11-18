package cn.edu.rpm.execution.application.dto;

/**
 * 周期报告提交结果（T-EXEC-006）。
 *
 * @param reportId  报告 ID
 * @param versionId 新生成的版本 ID
 * @param versionNo 版本号
 * @param status    报告状态（提交后为 SUBMITTED）
 * @param lateFlag  是否迟报
 */
public record ProgressReportSubmitted(
        String reportId,
        String versionId,
        int versionNo,
        String status,
        boolean lateFlag
) {
}
