package cn.edu.rpm.application.application.dto;

/**
 * 申报列表查询条件。
 *
 * @param keyword     编号/标题关键字
 * @param projectType 项目类型
 * @param status      申报状态
 * @param batchId     批次 ID
 * @param page        页码（从 1 开始）
 * @param size        每页条数
 */
public record ApplicationListQuery(
        String keyword,
        String projectType,
        String status,
        String batchId,
        int page,
        int size
) {

    /**
     * 规范化分页参数。
     */
    public ApplicationListQuery {
        page = Math.max(page, 1);
        size = Math.min(Math.max(size, 1), 200);
    }
}
