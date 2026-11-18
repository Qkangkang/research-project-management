package cn.edu.rpm.execution.application.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 周期报告列表项（T-EXEC-010）。
 *
 * @param reportId         报告 ID
 * @param projectId        项目 ID
 * @param periodType       周期类型
 * @param periodKey        周期键
 * @param dueDate          到期日
 * @param status           状态
 * @param currentVersionId 当前版本 ID
 * @param version          乐观锁版本
 * @param createdAt        创建时间
 */
public record ProgressReportView(
        String reportId,
        String projectId,
        String periodType,
        String periodKey,
        LocalDate dueDate,
        String status,
        String currentVersionId,
        int version,
        OffsetDateTime createdAt
) {
}
