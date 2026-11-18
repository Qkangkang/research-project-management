package cn.edu.rpm.output.application.dto;

/**
 * 成果登记结果（T-OUT-008；TC-OUT-001 / TC-OUT-006）。
 *
 * @param outputId       成果 ID
 * @param status         成果状态（登记后为 DRAFT）
 * @param versionId      初始版本 ID
 * @param versionNo      初始版本号（从 1 开始）
 * @param warningCode    提示码：有疑似重复时为 {@code RPM-OUT-001}，否则为 {@code null}
 * @param warningMessage 提示文案，可空
 */
public record OutputCreated(
        String outputId,
        String status,
        String versionId,
        int versionNo,
        String warningCode,
        String warningMessage
) {
}
