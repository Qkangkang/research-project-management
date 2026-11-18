package cn.edu.rpm.output.application.dto;

/**
 * 成果审核结果（T-OUT-011；TC-OUT-009 / TC-OUT-010）。
 *
 * @param outputId  成果 ID
 * @param status    审核后的成果状态
 * @param versionId 审核绑定的版本 ID
 * @param reviewId  审核记录 ID
 * @param decision  审核结论
 */
public record OutputReviewResult(
        String outputId,
        String status,
        String versionId,
        String reviewId,
        String decision
) {
}
