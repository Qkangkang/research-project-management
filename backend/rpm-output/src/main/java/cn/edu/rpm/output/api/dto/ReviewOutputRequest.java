package cn.edu.rpm.output.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 成果审核请求体（T-OUT-016；技术文档 05 §4.4）。
 *
 * @param versionId 审核的版本 ID（必须为当前提交版本）
 * @param decision  审核结论：APPROVED / RETURNED / REJECTED
 * @param comment   审核意见（退回/拒绝必填）
 */
public record ReviewOutputRequest(
        @JsonProperty("version_id") @NotBlank String versionId,
        @JsonProperty("decision") @NotBlank @Size(max = 32) String decision,
        @JsonProperty("comment") @Size(max = 2048) String comment
) {
}
