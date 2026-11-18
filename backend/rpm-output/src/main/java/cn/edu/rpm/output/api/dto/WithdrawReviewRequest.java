package cn.edu.rpm.output.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 成果撤回审核请求体（T-OUT-016；技术文档 08 §14.1）。
 *
 * @param approved 是否同意撤回
 * @param comment  审核意见，可空
 */
public record WithdrawReviewRequest(
        @JsonProperty("approved") boolean approved,
        @JsonProperty("comment") String comment
) {
}
