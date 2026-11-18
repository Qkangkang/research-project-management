package cn.edu.rpm.execution.application.dto;

/**
 * 上传交付物命令（T-EXEC-008；技术文档 04 §2.3）。
 *
 * @param fileId          文件对象 ID（rpm-file，必须已 {@code AVAILABLE}）
 * @param title           交付物名称（≤512 字符）
 * @param milestoneId     关联里程碑 ID（可空）
 * @param reportVersionId 关联报告版本 ID（可空）
 * @param sha256          客户端声明的文件内容 SHA-256（64 位十六进制）
 */
public record UploadDeliverableCmd(
        String fileId,
        String title,
        String milestoneId,
        String reportVersionId,
        String sha256
) {
}
