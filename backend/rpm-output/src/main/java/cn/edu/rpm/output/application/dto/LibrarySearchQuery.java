package cn.edu.rpm.output.application.dto;

/**
 * 全院成果库检索条件（T-OUT-014；技术文档 09 §5.1）。
 *
 * @param keyword    标题/DOI/专利号关键字（走 pg_trgm 索引），可空
 * @param outputType 成果类型，可空
 * @param deptId     部门 ID，可空
 * @param year       完成年度，可空
 * @param page       页码，可空（默认 1）
 * @param size       每页条数，可空（默认 20，上限 100）
 */
public record LibrarySearchQuery(
        String keyword,
        String outputType,
        String deptId,
        Integer year,
        Integer page,
        Integer size
) {
}
