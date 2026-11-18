package cn.edu.rpm.application.api.dto;

import cn.edu.rpm.application.infrastructure.entity.ApplicationEntity;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;

/**
 * 申报主档视图（T-APP-008；技术文档 04 §3.1 响应）。
 *
 * @param applicationId    申报 ID
 * @param applicationNo    申报编号（如 APP-2026-000123）
 * @param batchId          申报批次 ID
 * @param projectType      项目类型
 * @param title            申报标题
 * @param deptId           申报部门 ID
 * @param status           申报状态
 * @param currentVersionId 当前生效版本 ID；草稿为空
 * @param version          乐观锁版本号（保存草稿/提交时回传）
 * @param createdAt        创建时间
 * @param updatedAt        最后更新时间
 */
public record ApplicationVO(
        @JsonProperty("application_id") String applicationId,
        @JsonProperty("application_no") String applicationNo,
        @JsonProperty("batch_id") String batchId,
        @JsonProperty("project_type") String projectType,
        @JsonProperty("title") String title,
        @JsonProperty("dept_id") String deptId,
        @JsonProperty("status") String status,
        @JsonProperty("current_version_id") String currentVersionId,
        @JsonProperty("version") Integer version,
        @JsonProperty("created_at") OffsetDateTime createdAt,
        @JsonProperty("updated_at") OffsetDateTime updatedAt
) {

    /**
     * 由申报 PO 构造视图。
     *
     * @param entity 申报主档
     * @return 申报视图
     */
    public static ApplicationVO from(ApplicationEntity entity) {
        return new ApplicationVO(
                entity.getId(),
                entity.getApplicationNo(),
                entity.getBatchId(),
                entity.getProjectType(),
                entity.getTitle(),
                entity.getDeptId(),
                entity.getStatus(),
                entity.getCurrentVersionId(),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
