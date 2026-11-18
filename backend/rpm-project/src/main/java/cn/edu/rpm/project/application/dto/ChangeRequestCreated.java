package cn.edu.rpm.project.application.dto;

/**
 * 项目变更发起结果（T-PRJ-019）。
 *
 * @param changeRequestId 变更单 ID
 * @param versionId       变更版本 ID
 * @param status          变更状态（发起后固定为 IN_REVIEW）
 * @param templateCode    实际使用的审批模板编码
 */
public record ChangeRequestCreated(
        String changeRequestId,
        String versionId,
        String status,
        String templateCode
) {
}
