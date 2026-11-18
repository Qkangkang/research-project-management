package cn.edu.rpm.execution.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;

/**
 * 周期报告创建结果（T-EXEC-005；TC-EXEC-006）。
 *
 * @param reportId   报告 ID
 * @param periodType 周期类型（MONTH / QUARTER）
 * @param periodKey  周期键（如 {@code 2026-11}、{@code 2026-Q4}）
 * @param status     报告状态（DRAFT）
 * @param dueDate    到期日
 */
public record ProgressReportCreated(
        @JsonProperty("report_id") String reportId,
        @JsonProperty("period_type") String periodType,
        @JsonProperty("period_key") String periodKey,
        @JsonProperty("status") String status,
        @JsonProperty("due_date") LocalDate dueDate
) {
}
