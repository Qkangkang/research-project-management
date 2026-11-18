package cn.edu.rpm.execution.application.dto;

import java.time.OffsetDateTime;

/**
 * 交付物列表项（T-EXEC-008；TC-EXEC-015）。
 *
 * @param deliverableId 交付物 ID
 * @param projectId     项目 ID
 * @param milestoneId   关联里程碑 ID（可空）
 * @param fileId        文件对象 ID
 * @param title         名称
 * @param sha256        内容摘要
 * @param status        状态
 * @param uploadedBy    上传人用户 ID
 * @param createdAt     创建时间
 */
public record DeliverableView(
        String deliverableId,
        String projectId,
        String milestoneId,
        String fileId,
        String title,
        String sha256,
        String status,
        String uploadedBy,
        OffsetDateTime createdAt
) {
}
