package cn.edu.rpm.application.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * 保存申报草稿请求体（T-APP-008；技术文档 04 §2.2 {@code PUT /applications/{id}/draft}）。
 *
 * <p>{@code expected_version} 为乐观锁必需字段：与主档当前版本不一致时返回
 * {@code RPM-APPLY-002}，草稿内容不覆盖（TC-APP-003）。表单内容以 JSON 对象提交，
 * 由服务端规范化后写入草稿版本。</p>
 *
 * @param expectedVersion 客户端读取到的申报主档版本号（必填）
 * @param title           申报标题（可选；为空表示不修改）
 * @param formData        表单数据（JSON 对象，必填）
 */
public record UpdateDraftRequest(
        @JsonProperty("expected_version") @NotNull @Min(0) Integer expectedVersion,
        @JsonProperty("title") @Size(max = 512) String title,
        @JsonProperty("form_data") @NotNull Map<String, Object> formData
) {
}
