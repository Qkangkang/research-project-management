package cn.edu.rpm.project.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 项目详情摘要响应（T-PRJ-011 / T-PRJ-017；技术文档 04 §3.7 Response 形状）。
 *
 * <p>{@code baseline_id} / {@code baseline_version} 取当前生效基线；
 * 后续各页签查询必须使用同一 {@code project.id} 与 {@code baseline.id}。</p>
 *
 * @param projectId       项目 ID
 * @param projectNo       项目编号
 * @param title           项目名称
 * @param projectType     项目类型
 * @param piId            项目负责人 ID
 * @param piName          项目负责人姓名
 * @param deptId          所属部门 ID
 * @param deptName        所属部门名称
 * @param baselineId      当前生效基线 ID；未生成基线时为 {@code null}
 * @param baselineVersion 当前生效基线版本号；未生成基线时为 {@code null}
 * @param status          项目状态
 * @param startDate       开始日期
 * @param endDate         结束日期
 * @param totalBudget     预算总额
 * @param memberCount     有效成员数
 * @param createdAt       创建时间
 * @param updatedAt       更新时间
 * @param summary         页签摘要
 */
public record ProjectDetailSummary(
        @JsonProperty("project_id") String projectId,
        @JsonProperty("project_no") String projectNo,
        @JsonProperty("title") String title,
        @JsonProperty("project_type") String projectType,
        @JsonProperty("pi_id") String piId,
        @JsonProperty("pi_name") String piName,
        @JsonProperty("dept_id") String deptId,
        @JsonProperty("dept_name") String deptName,
        @JsonProperty("baseline_id") String baselineId,
        @JsonProperty("baseline_version") Integer baselineVersion,
        @JsonProperty("status") String status,
        @JsonProperty("start_date") LocalDate startDate,
        @JsonProperty("end_date") LocalDate endDate,
        @JsonProperty("total_budget") BigDecimal totalBudget,
        @JsonProperty("member_count") Long memberCount,
        @JsonProperty("created_at") OffsetDateTime createdAt,
        @JsonProperty("updated_at") OffsetDateTime updatedAt,
        @JsonProperty("summary") ProjectSummary summary
) {
}
