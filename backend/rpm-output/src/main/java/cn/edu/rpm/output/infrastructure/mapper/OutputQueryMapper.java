package cn.edu.rpm.output.infrastructure.mapper;

import cn.edu.rpm.output.infrastructure.mapper.dto.OutputLibraryRow;
import cn.edu.rpm.output.infrastructure.mapper.dto.OutputSearchRow;
import cn.edu.rpm.output.infrastructure.mapper.dto.OutputStatRow;
import cn.edu.rpm.output.infrastructure.mapper.param.OutputLibraryQueryParam;
import cn.edu.rpm.output.infrastructure.mapper.param.OutputSearchParam;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 成果检索与统计 Mapper（T-OUT-014）。
 *
 * <p>SQL 统一维护在 {@code resources/mapper/OutputQueryMapper.xml}
 * （禁止注解 SQL）：</p>
 * <ul>
 *   <li>全院成果库检索：仅 {@code status = 'APPROVED'}，关键字走 V01
 *       {@code pg_trgm} 索引（title/doi/patent_no）；</li>
 *   <li>我的成果 + 已公开成果检索：保证他人未公开成果不进入结果；</li>
 *   <li>统计：{@code COUNT(DISTINCT id)} 成果去重数，按类型/年度/部门分组。</li>
 * </ul>
 */
@Mapper
public interface OutputQueryMapper {

    /**
     * 全院成果库分页检索（仅已审核成果）。
     *
     * @param query 检索参数
     * @return 当前页行；无命中返回空列表
     */
    List<OutputLibraryRow> selectLibraryPage(@Param("q") OutputLibraryQueryParam query);

    /**
     * 全院成果库检索总数。
     *
     * @param query 检索参数
     * @return 总记录数
     */
    long countLibrary(@Param("q") OutputLibraryQueryParam query);

    /**
     * 「我的成果 + 已公开成果」分页检索。
     *
     * @param query 检索参数（ownerId 必填）
     * @return 当前页行；无命中返回空列表
     */
    List<OutputSearchRow> selectVisiblePage(@Param("q") OutputSearchParam query);

    /**
     * 「我的成果 + 已公开成果」检索总数。
     *
     * @param query 检索参数（ownerId 必填）
     * @return 总记录数
     */
    long countVisible(@Param("q") OutputSearchParam query);

    /**
     * 按成果类型统计已审核成果去重数。
     *
     * @param deptId 部门过滤，可空
     * @param year   完成年度过滤，可空
     * @return 分组统计行
     */
    List<OutputStatRow> statsByType(@Param("deptId") String deptId,
                                    @Param("year") Integer year);

    /**
     * 按完成年度统计已审核成果去重数。
     *
     * @param deptId 部门过滤，可空
     * @return 分组统计行
     */
    List<OutputStatRow> statsByYear(@Param("deptId") String deptId);

    /**
     * 按部门统计已审核成果去重数。
     *
     * @param year 完成年度过滤，可空
     * @return 分组统计行
     */
    List<OutputStatRow> statsByDept(@Param("year") Integer year);
}
