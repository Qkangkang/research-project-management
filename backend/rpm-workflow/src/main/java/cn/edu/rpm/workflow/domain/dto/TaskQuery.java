package cn.edu.rpm.workflow.domain.dto;

/**
 * 待办任务查询条件（T-WF-008；技术文档 03 §3.2）。
 *
 * <p>分页参数在构造时校验：{@code pageNo >= 1}、{@code pageSize} 在 1～100；
 * 防止前端传入超大页导致全表扫描。</p>
 *
 * @param bizType 业务类型过滤；为空表示不过滤
 * @param status  任务状态；为空时按 PENDING（待办）处理
 * @param pageNo  页码（从 1 开始）
 * @param pageSize 每页条数（1～100）
 */
public record TaskQuery(
        String bizType,
        String status,
        int pageNo,
        int pageSize
) {

    /** 默认待办状态。 */
    public static final String DEFAULT_STATUS = "PENDING";

    /** 单页最大条数。 */
    public static final int MAX_PAGE_SIZE = 100;

    /** 校验并补全默认值。 */
    public TaskQuery {
        status = (status == null || status.isBlank()) ? DEFAULT_STATUS : status;
        if (pageNo < 1) {
            throw new IllegalArgumentException("pageNo 必须大于等于 1");
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("pageSize 必须在 1～" + MAX_PAGE_SIZE + " 之间");
        }
    }

    /**
     * 构造默认待办查询（第 1 页、每页 20 条）。
     *
     * @param bizType 业务类型过滤；可为空
     * @return 待办查询条件
     */
    public static TaskQuery pending(String bizType) {
        return new TaskQuery(bizType, DEFAULT_STATUS, 1, 20);
    }
}
