package cn.edu.rpm.project.application.dto;

/**
 * 里程碑延期申请结果（T-PRJ-016；08 §6.1 返回 202 + instanceId）。
 *
 * @param requestId  延期申请 ID
 * @param status     申请状态（PENDING）
 * @param instanceId 审批实例 ID
 */
public record MilestoneDelayRequestResult(String requestId, String status, String instanceId) {
}
