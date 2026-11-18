package cn.edu.rpm.output.application.dto;

/**
 * 「我的成果 + 已公开成果」检索条件（T-OUT-014；TC-OUT-014）。
 *
 * @param keyword    标题/DOI/专利号关键字，可空
 * @param outputType 成果类型，可空
 * @param status     成果状态，可空
 * @param deptId     部门 ID，可空
 * @param year       完成年度，可空
 * @param page       页码，可空（默认 1）
 * @param size       每页条数，可空（默认 20，上限 100）
 */
public record OutputSearchQuery(
        String keyword,
        String outputType,
        String status,
        String deptId,
        Integer year,
        Integer page,
        Integer size
) {
}
