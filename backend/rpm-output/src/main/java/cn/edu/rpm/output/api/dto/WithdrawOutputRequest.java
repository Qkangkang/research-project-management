package cn.edu.rpm.output.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 成果撤回申请请求体（T-OUT-016；技术文档 08 §14.1）。
 *
 * @param reason 撤回理由（必填，进入审计并供科研管理审核）
 */
public record WithdrawOutputRequest(
        @JsonProperty("reason") @NotBlank @Size(max = 1024) String reason
) {
}
