package cn.edu.rpm.execution.application.dto;

/**
 * 交付物创建结果（T-EXEC-008）。
 *
 * @param deliverableId 交付物 ID
 * @param fileId        文件对象 ID
 * @param sha256        已校验的 SHA-256
 * @param status        状态（{@code ACTIVE}）
 * @param version       乐观锁版本
 */
public record DeliverableCreated(
        String deliverableId,
        String fileId,
        String sha256,
        String status,
        int version
) {
}
