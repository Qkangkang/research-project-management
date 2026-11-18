package cn.edu.rpm.output.application.dto;

/**
 * 全院成果统计项（T-OUT-014；TC-OSR-005）。
 *
 * @param groupKey    分组键（类型码 / 年度 / 部门 ID）
 * @param outputCount 成果去重数（按成果 ID 去重）
 */
public record OutputStatItem(
        String groupKey,
        long outputCount
) {
}
