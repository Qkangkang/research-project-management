package cn.edu.rpm.application.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 新建申报选项。
 *
 * @param batches      开放批次
 * @param projectTypes 当前有效项目类型模板
 */
public record ApplicationOptionsView(
        @JsonProperty("batches") List<BatchOption> batches,
        @JsonProperty("project_types") List<ProjectTypeOption> projectTypes
) {

    /**
     * 开放批次选项。
     *
     * @param id      批次 ID
     * @param batchNo 批次编号
     * @param name    批次名称
     * @param year    申报年度
     * @param status  批次状态
     * @param startAt 开始时间
     * @param endAt   截止时间
     */
    public record BatchOption(
            @JsonProperty("id") String id,
            @JsonProperty("batch_no") String batchNo,
            @JsonProperty("name") String name,
            @JsonProperty("year") Integer year,
            @JsonProperty("status") String status,
            @JsonProperty("start_at") OffsetDateTime startAt,
            @JsonProperty("end_at") OffsetDateTime endAt
    ) {
    }

    /**
     * 项目类型选项。
     *
     * @param projectType 项目类型编码
     * @param templateId  当前有效模板 ID
     * @param versionNo   模板版本号
     * @param formSchema  表单 JSON Schema 子集
     */
    public record ProjectTypeOption(
            @JsonProperty("project_type") String projectType,
            @JsonProperty("template_id") String templateId,
            @JsonProperty("version_no") Integer versionNo,
            @JsonProperty("form_schema") String formSchema
    ) {
    }
}
