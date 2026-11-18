package cn.edu.rpm.output.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 关联成果到项目请求体（T-OUT-016；技术文档 05 §4.3）。
 *
 * @param projectId        目标项目 ID
 * @param contributionDesc 贡献/用途说明（必填，≤1024 字符）
 * @param attributionRatio 归属比例（0～1，仅指定统计口径）
 */
public record RelateOutputRequest(
        @JsonProperty("project_id") @NotBlank String projectId,
        @JsonProperty("contribution_desc") @NotBlank @Size(max = 1024) String contributionDesc,
        @JsonProperty("attribution_ratio") @DecimalMin("0.0") @DecimalMax("1.0")
        BigDecimal attributionRatio
) {
}
