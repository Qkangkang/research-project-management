package cn.edu.rpm.output.infrastructure.repository;

import cn.edu.rpm.output.infrastructure.mapper.dto.OutputLibraryRow;
import cn.edu.rpm.output.infrastructure.mapper.dto.OutputSearchRow;
import cn.edu.rpm.output.infrastructure.mapper.dto.OutputStatRow;

import java.util.List;

/**
 * 成果检索与统计仓储（T-OUT-014）。
 *
 * <p>封装 {@code OutputQueryMapper} 的参数装配与分页计算；SQL 口径见
 * {@code OutputQueryMapper.xml}。全院库仅返回 {@code APPROVED} 成果，
 * 统计按成果去重数。</p>
 */
public interface OutputQueryRepository {

    /**
     * 全院成果库分页检索。
     *
     * @param keyword    标题/DOI/专利号关键字，可空
     * @param outputType 成果类型，可空
     * @param deptId     部门 ID，可空
     * @param year       完成年度，可空
     * @param page       页码（从 1 开始）
     * @param size       每页条数
     * @return 分页行
     */
    OutputPage<OutputLibraryRow> searchLibrary(String keyword, String outputType, String deptId,
                                               Integer year, int page, int size);

    /**
     * 「我的成果 + 已公开成果」分页检索。
     *
     * @param ownerId    当前请求人用户 ID（必填）
     * @param keyword    标题/DOI/专利号关键字，可空
     * @param outputType 成果类型，可空
     * @param status     成果状态，可空
     * @param deptId     部门 ID，可空
     * @param year       完成年度，可空
     * @param page       页码（从 1 开始）
     * @param size       每页条数
     * @return 分页行
     */
    OutputPage<OutputSearchRow> searchVisible(String ownerId, String keyword, String outputType,
                                              String status, String deptId, Integer year,
                                              int page, int size);

    /**
     * 统计已审核成果去重数。
     *
     * @param groupBy 分组维度：TYPE / YEAR / DEPT（调用方已校验）
     * @param deptId  部门过滤，可空
     * @param year    完成年度过滤，可空
     * @return 分组统计行
     */
    List<OutputStatRow> stats(String groupBy, String deptId, Integer year);
}
