package cn.edu.rpm.application.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 申报详情视图。
 *
 * @param applicationId    申报 ID
 * @param applicationNo    申报编号
 * @param batchId          批次 ID
 * @param batchName        批次名称
 * @param projectType      项目类型
 * @param title            标题
 * @param applicantId      申请人 ID
 * @param applicantName    申请人姓名
 * @param deptId           所属部门 ID
 * @param deptName         所属部门名称
 * @param status           申报状态
 * @param currentVersionId 当前生效版本 ID
 * @param version          乐观锁版本号
 * @param formData         当前草稿或生效版本表单数据
 * @param attachments      附件（当前数据模型尚未固化引用，返回空数组）
 * @param workflowInstanceId 审批实例 ID（如可解析）
 * @param createdAt        创建时间
 * @param updatedAt        更新时间
 */
public record ApplicationDetailView(
        @JsonProperty("application_id") String applicationId,
        @JsonProperty("application_no") String applicationNo,
        @JsonProperty("batch_id") String batchId,
        @JsonProperty("batch_name") String batchName,
        @JsonProperty("project_type") String projectType,
        @JsonProperty("title") String title,
        @JsonProperty("applicant_id") String applicantId,
        @JsonProperty("applicant_name") String applicantName,
        @JsonProperty("dept_id") String deptId,
        @JsonProperty("dept_name") String deptName,
        @JsonProperty("status") String status,
        @JsonProperty("current_version_id") String currentVersionId,
        @JsonProperty("version") Integer version,
        @JsonProperty("form_data") Map<String, Object> formData,
        @JsonProperty("attachments") List<AttachmentView> attachments,
        @JsonProperty("workflow_instance_id") String workflowInstanceId,
        @JsonProperty("created_at") OffsetDateTime createdAt,
        @JsonProperty("updated_at") OffsetDateTime updatedAt
) {

    /**
     * 附件视图。
     *
     * @param purpose  用途
     * @param fileId   文件 ID
     * @param fileName 文件名
     */
    public record AttachmentView(
            @JsonProperty("purpose") String purpose,
            @JsonProperty("file_id") String fileId,
            @JsonProperty("file_name") String fileName
    ) {
    }
}
