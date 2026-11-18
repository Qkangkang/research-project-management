package cn.edu.rpm.application.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/**
 * 提交申报请求体（T-APP-008；技术文档 04 §3.2）。
 *
 * <p>在文档示例字段之外补充 {@code form_data} 与 {@code attachments}：
 * 提交链路需把当前表单与必填附件固化为不可变版本（TC-APP-004、TC-APP-006）；
 * 附件只传 {@code file_id}，文件本体与哈希由 {@code rpm-file} 持有。</p>
 *
 * @param expectedVersion 客户端读取到的申报主档版本号（必填）
 * @param comment         提交说明
 * @param formData        当前表单数据（JSON 对象，必填）
 * @param attachments     附件引用（用途 + 文件 ID）
 * @param collaborators   协作成员（申请人本人固定为 PI）
 */
public record SubmitApplicationRequest(
        @JsonProperty("expected_version") @NotNull @Min(0) Integer expectedVersion,
        @JsonProperty("comment") @Size(max = 2048) String comment,
        @JsonProperty("form_data") @NotNull Map<String, Object> formData,
        @JsonProperty("attachments") @NotNull List<@Valid Attachment> attachments,
        @JsonProperty("collaborators") List<@Valid Collaborator> collaborators
) {

    /**
     * 附件引用。
     *
     * @param purpose 附件用途（与表单模板 {@code requiredAttachments} 对应，如 ETHICS）
     * @param fileId  文件 ID（{@code file_object.id}）
     */
    public record Attachment(
            @JsonProperty("purpose") @NotBlank @Size(max = 64) String purpose,
            @JsonProperty("file_id") @NotBlank String fileId
    ) {
    }

    /**
     * 协作成员。
     *
     * @param userId           用户 ID
     * @param role             成员角色（如 CO_AUTHOR / ADVISOR；PI 由申请人固定承载）
     * @param contributionDesc 贡献说明
     */
    public record Collaborator(
            @JsonProperty("user_id") @NotBlank String userId,
            @JsonProperty("role") @NotBlank @Size(max = 32) String role,
            @JsonProperty("contribution_desc") @Size(max = 1024) String contributionDesc
    ) {
    }
}
