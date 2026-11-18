package cn.edu.rpm.output.application.dto;

/**
 * 全院成果统计条件（T-OUT-014；技术文档 09 §5.1）。
 *
 * @param groupBy 分组维度：TYPE / YEAR / DEPT
 * @param deptId  部门过滤，可空
 * @param year    完成年度过滤，可空
 */
public record OutputStatsQuery(
        String groupBy,
        String deptId,
        Integer year
) {
}
