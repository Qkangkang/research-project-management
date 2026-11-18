package cn.edu.rpm.funding.domain.model;

import java.math.BigDecimal;

/**
 * 余额投影重建结果（T-FUND-018；技术文档 05 §1.2、08 §10）。
 *
 * @param projectId           项目 ID
 * @param lineCount           参与重放的科目数
 * @param correctedLineCount  与在线投影存在偏差并被修正的科目数
 * @param maxDifference       四类金额逐项比对的最大绝对偏差
 */
public record BudgetRebuildResult(
        String projectId,
        int lineCount,
        int correctedLineCount,
        BigDecimal maxDifference
) {

    /**
     * 在线投影与重放结果是否完全一致。
     *
     * @return 无修正且最大偏差为 0 时返回 {@code true}
     */
    public boolean consistent() {
        return correctedLineCount == 0 && maxDifference.compareTo(BigDecimal.ZERO) == 0;
    }
}
