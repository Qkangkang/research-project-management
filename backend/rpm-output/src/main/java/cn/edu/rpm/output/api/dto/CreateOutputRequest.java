package cn.edu.rpm.output.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 登记成果请求体（T-OUT-016；技术文档 05 §4.2）。
 *
 * <p>字段与接口 JSON 一一对应；格式校验在此完成，类型模板、DOI/专利号格式、
 * 完成日期、证据文件可用性与查重由应用服务负责。</p>
 *
 * @param outputType     成果类型（PAPER/PATENT/...）
 * @param title          标题（1～512 字符）
 * @param doi            DOI，可空
 * @param patentNo       专利号，PATENT 类型必填
 * @param completedAt    完成日期（不得晚于当前日期）
 * @param ownerId        完成人用户 ID；为空时默认当前用户
 * @param deptId         所属部门 ID
 * @param metadata       扩展元数据
 * @param evidenceFileIds 证据文件 ID 列表
 */
public record CreateOutputRequest(
        @JsonProperty("output_type") @NotBlank @Size(max = 32) String outputType,
        @JsonProperty("title") @NotBlank @Size(max = 512) String title,
        @JsonProperty("doi") @Size(max = 256) String doi,
        @JsonProperty("patent_no") @Size(max = 128) String patentNo,
        @JsonProperty("completed_at") @NotNull LocalDate completedAt,
        @JsonProperty("owner_id") String ownerId,
        @JsonProperty("dept_id") @NotBlank String deptId,
        @JsonProperty("metadata") Map<String, Object> metadata,
        @JsonProperty("evidence_file_ids") List<String> evidenceFileIds
) {
}
