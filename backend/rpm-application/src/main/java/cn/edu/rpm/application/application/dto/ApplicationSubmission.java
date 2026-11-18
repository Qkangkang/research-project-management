package cn.edu.rpm.application.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 提交申报结果（响应形状与《04-核心业务接口设计.md》§3.2 一致；
 * 字段与 TC-APP-004 / TC-APP-008 断言一致）。
 *
 * @param applicationId      申报 ID
 * @param versionId          提交版本 ID
 * @param versionNo          版本号
 * @param status             申报状态（IN_REVIEW）
 * @param workflowInstanceId 审批实例 ID
 */
public record ApplicationSubmission(
        @JsonProperty("application_id") String applicationId,
        @JsonProperty("version_id") String versionId,
        @JsonProperty("version_no") int versionNo,
        @JsonProperty("status") String status,
        @JsonProperty("workflow_instance_id") String workflowInstanceId
) {
}
