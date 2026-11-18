package cn.edu.rpm.application.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建申报草稿请求体（T-APP-008；技术文档 04 §3.1）。
 *
 * <p>字段与文档请求 JSON 一一对应；格式校验在此完成，业务校验
 * （批次开放、模板有效、部门授权）由应用服务负责。</p>
 *
 * @param batchId     申报批次 ID（必填，且状态为 OPEN）
 * @param projectType 项目类型（必填，存在有效表单模板）
 * @param title       申报标题（1～512 字符，服务端去首尾空格）
 * @param deptId      申报部门 ID（必填，属于当前用户组织范围）
 */
public record CreateApplicationRequest(
        @JsonProperty("batch_id") @NotBlank String batchId,
        @JsonProperty("project_type") @NotBlank @Size(max = 64) String projectType,
        @JsonProperty("title") @NotBlank @Size(max = 512) String title,
        @JsonProperty("dept_id") @NotBlank String deptId
) {
}
