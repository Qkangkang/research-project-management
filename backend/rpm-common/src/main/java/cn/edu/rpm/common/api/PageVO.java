package cn.edu.rpm.common.api;

import java.util.List;

/**
 * 分页响应载体，形状锚点《04-核心业务接口设计.md》§1.2：
 * {@code data: { items, page, size, total }}；{@code page} 从 1 开始，{@code size} 默认 20、最大 200。
 *
 * @param <T> 行记录类型
 */
public class PageVO<T> {

    /** 当前页数据行，永不为 {@code null}。 */
    private final List<T> items;

    /** 页码，从 1 开始。 */
    private final int page;

    /** 每页条数。 */
    private final int size;

    /** 总记录数。 */
    private final long total;

    private PageVO(List<T> items, int page, int size, long total) {
        this.items = items == null ? List.of() : List.copyOf(items);
        this.page = page;
        this.size = size;
        this.total = total;
    }

    /**
     * 构造分页响应。
     *
     * @param items 当前页数据（null 视为空列表）
     * @param page  页码（从 1 开始）
     * @param size  每页条数
     * @param total 总记录数
     * @param <T>   行记录类型
     * @return 分页响应载体
     */
    public static <T> PageVO<T> of(List<T> items, int page, int size, long total) {
        return new PageVO<>(items, page, size, total);
    }

    public List<T> getItems() {
        return items;
    }

    public int getPage() {
        return page;
    }

    public int getSize() {
        return size;
    }

    public long getTotal() {
        return total;
    }
}
