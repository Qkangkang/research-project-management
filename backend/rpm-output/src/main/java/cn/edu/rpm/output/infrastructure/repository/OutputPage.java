package cn.edu.rpm.output.infrastructure.repository;

import java.util.List;

/**
 * 查询分页结果（T-OUT-014；仓储内部载体）。
 *
 * <p>行数据 + 总数一次返回，应用服务再组装为 {@code PageVO}；
 * 行类型为 MyBatis 扁平行，不跨层暴露为业务 DTO。</p>
 *
 * @param rows  当前页行，永不为 {@code null}
 * @param total 总记录数
 * @param <T>   行类型
 */
public record OutputPage<T>(List<T> rows, long total) {

    /** 规范化行列表。 */
    public OutputPage {
        rows = rows == null ? List.of() : List.copyOf(rows);
    }
}
