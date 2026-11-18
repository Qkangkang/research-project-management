package cn.edu.rpm.workflow.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 审批任务处理请求体（T-WF-015；技术文档 04 §3.11）。
 *
 * <p>字段与文档请求 JSON 对齐（{@code on_behalf_of} / {@code request_id}）；
 * 文档未列出的 {@code expected_version} 为乐观锁必需字段（TC-APP-024），
 * {@code reassign_to} 为超时重派扩展字段（TC-APP-026），二者在任务拆解与本批次报告中说明。</p>
 *
 * @param action          处理动作（APPROVED / RETURNED / REJECTED）；重派时为空
 * @param comment         处理意见
 * @param onBehalfOf      委托人用户 ID（代理审批）
 * @param expectedVersion 读取任务时的版本号
 * @param requestId       请求幂等键
 * @param reassignTo      新处理人用户 ID（管理员重派）
 */
public record TaskActionRequest(
        @JsonProperty("action") String action,
        @JsonProperty("comment") String comment,
        @JsonProperty("on_behalf_of") String onBehalfOf,
        @JsonProperty("expected_version") Integer expectedVersion,
        @JsonProperty("request_id") String requestId,
        @JsonProperty("reassign_to") String reassignTo
) {
}
