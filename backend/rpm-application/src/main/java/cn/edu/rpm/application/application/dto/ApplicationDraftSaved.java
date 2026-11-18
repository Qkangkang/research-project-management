package cn.edu.rpm.application.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 保存草稿结果。
 *
 * @param applicationId  申报 ID
 * @param status         申报状态
 * @param version        保存后的主档乐观锁版本号
 * @param draftVersionId 草稿版本 ID
 * @param draftVersionNo 草稿版本号
 */
public record ApplicationDraftSaved(
        @JsonProperty("application_id") String applicationId,
        @JsonProperty("status") String status,
        @JsonProperty("version") int version,
        @JsonProperty("draft_version_id") String draftVersionId,
        @JsonProperty("draft_version_no") int draftVersionNo
) {
}
