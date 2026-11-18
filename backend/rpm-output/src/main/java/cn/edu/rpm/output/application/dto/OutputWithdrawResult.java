package cn.edu.rpm.output.application.dto;

/**
 * 成果撤回/撤回审核结果（T-OUT-013；TC-OUT-101～103）。
 *
 * @param outputId       成果 ID
 * @param status         当前成果状态（审核通过后为 REVOKED）
 * @param withdrawReview 是否需要科研管理审核
 */
public record OutputWithdrawResult(
        String outputId,
        String status,
        boolean withdrawReview
) {
}
