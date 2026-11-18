package cn.edu.rpm.workflow.domain.model;

import java.math.BigDecimal;

/**
 * 金额条件区间（T-WF-013；技术文档 03 §3.3 {@code GT/GTE/LT/LTE/BETWEEN}）。
 *
 * <p>{@code lower}/{@code upper} 为空表示该侧开区间；用于发布时检测同一模板下
 * 金额条件是否重叠。区间为半开/闭区间的组合由 {@code lowerInclusive}/{@code upperInclusive} 表达。</p>
 *
 * @param lower          下界；为空表示负无穷
 * @param upper          上界；为空表示正无穷
 * @param lowerInclusive 是否包含下界
 * @param upperInclusive 是否包含上界
 */
public record BudgetConditionRange(
        BigDecimal lower,
        BigDecimal upper,
        boolean lowerInclusive,
        boolean upperInclusive
) {

    /**
     * 判断两个区间是否存在交集（含端点闭合规则）。
     *
     * @param other 另一个区间
     * @return 存在交集返回 {@code true}
     */
    public boolean overlaps(BudgetConditionRange other) {
        if (lower != null && other.upper != null) {
            int compared = lower.compareTo(other.upper);
            if (compared > 0 || (compared == 0 && !(lowerInclusive && other.upperInclusive))) {
                return false;
            }
        }
        if (upper != null && other.lower != null) {
            int compared = upper.compareTo(other.lower);
            if (compared < 0 || (compared == 0 && !(upperInclusive && other.lowerInclusive))) {
                return false;
            }
        }
        return true;
    }
}
