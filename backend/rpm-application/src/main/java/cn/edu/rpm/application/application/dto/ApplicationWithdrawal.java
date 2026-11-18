package cn.edu.rpm.application.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 申报撤回结果（T-APP-018）。
 *
 * @param applicationId  申报 ID
 * @param status         撤回后申报状态（{@code WITHDRAWN}）
 * @param instanceStatus 审批实例状态（{@code CANCELLED}）；无实例时为 {@code null}
 */
public record ApplicationWithdrawal(
        @JsonProperty("application_id") String applicationId,
        @JsonProperty("status") String status,
        @JsonProperty("instance_status") String instanceStatus
) {
}
