package cn.edu.rpm.output.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 保存成果草稿/修订请求体（T-OUT-016；技术文档 05 §4.1）。
 *
 * @param title          标题（1～512 字符）
 * @param doi            DOI，可空
 * @param patentNo       专利号，PATENT 类型必填
 * @param completedAt    完成日期
 * @param metadata       扩展元数据
 * @param evidenceFileIds 证据文件 ID 列表（替换当前草稿版本证据）
 */
public record SaveOutputDraftRequest(
        @JsonProperty("title") @NotBlank @Size(max = 512) String title,
        @JsonProperty("doi") @Size(max = 256) String doi,
        @JsonProperty("patent_no") @Size(max = 128) String patentNo,
        @JsonProperty("completed_at") @NotNull LocalDate completedAt,
        @JsonProperty("metadata") Map<String, Object> metadata,
        @JsonProperty("evidence_file_ids") List<String> evidenceFileIds
) {
}
