package cn.edu.rpm.application.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 形式审查结果（T-APP-017）。
 *
 * @param applicationId 申报 ID
 * @param versionId     被审查版本 ID
 * @param decision      审查结论
 * @param status        审查后申报状态
 * @param issueCount    问题项数量
 */
public record FormalReviewResult(
        @JsonProperty("application_id") String applicationId,
        @JsonProperty("version_id") String versionId,
        @JsonProperty("decision") String decision,
        @JsonProperty("status") String status,
        @JsonProperty("issue_count") int issueCount
) {
}
