package cn.edu.rpm.output.application.dto;

/**
 * 成果提交结果（T-OUT-009；TC-OUT-005）。
 *
 * @param outputId  成果 ID
 * @param status    成果状态（提交后为 SUBMITTED）
 * @param versionId 固定提交的版本 ID
 * @param versionNo 提交版本号
 */
public record OutputSubmission(
        String outputId,
        String status,
        String versionId,
        int versionNo
) {
}
