package cn.edu.rpm.application.api.dto;

import cn.edu.rpm.application.infrastructure.entity.ApplicationVersionEntity;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 申报版本视图（T-APP-008；技术文档 04 §3.2 响应）。
 *
 * @param applicationId      申报 ID
 * @param versionId          版本 ID
 * @param versionNo          版本号（从 1 起）
 * @param status             版本状态
 * @param contentHash        表单内容 SHA-256（64 位十六进制）
 * @param submittedBy        提交人用户 ID
 * @param submittedAt        提交时刻
 * @param workflowInstanceId 审批实例 ID；未启动审批时为空
 */
public record ApplicationVersionVO(
        @JsonProperty("application_id") String applicationId,
        @JsonProperty("version_id") String versionId,
        @JsonProperty("version_no") Integer versionNo,
        @JsonProperty("status") String status,
        @JsonProperty("content_hash") String contentHash,
        @JsonProperty("submitted_by") String submittedBy,
        @JsonProperty("submitted_at") OffsetDateTime submittedAt,
        @JsonProperty("workflow_instance_id") String workflowInstanceId
) {

    /**
     * 由版本 PO 与审批实例 ID 构造视图。
     *
     * @param entity             申报版本
     * @param workflowInstanceId 审批实例 ID；可为空
     * @return 版本视图
     */
    public static ApplicationVersionVO from(ApplicationVersionEntity entity, UUID workflowInstanceId) {
        return new ApplicationVersionVO(
                entity.getApplicationId(),
                entity.getId(),
                entity.getVersionNo(),
                entity.getStatus(),
                entity.getContentHash(),
                entity.getSubmittedBy(),
                entity.getSubmittedAt(),
                workflowInstanceId == null ? null : workflowInstanceId.toString());
    }
}
