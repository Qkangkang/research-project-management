package cn.edu.rpm.application.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 外部项目登记结果（T-APP-020）。
 *
 * @param applicationId 生成的申报 ID（状态 {@code APPROVED}）
 * @param applicationNo 申报编号
 * @param versionId     申报版本 ID
 * @param projectId     项目 ID
 * @param projectNo     项目编号
 * @param baselineId    初始基线 ID
 */
public record ExternalRegistrationResult(
        @JsonProperty("application_id") String applicationId,
        @JsonProperty("application_no") String applicationNo,
        @JsonProperty("version_id") String versionId,
        @JsonProperty("project_id") String projectId,
        @JsonProperty("project_no") String projectNo,
        @JsonProperty("baseline_id") String baselineId
) {
}
