package cn.edu.rpm.project.infrastructure.mapper;

import cn.edu.rpm.project.application.dto.ProjectListItem;
import cn.edu.rpm.project.application.dto.ProjectListQuery;
import cn.edu.rpm.project.infrastructure.mapper.dto.ProjectDetailRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

/**
 * 项目查询 Mapper（T-PRJ-011；技术文档 04 §3.6/§3.7）。
 *
 * <p>只读查询侧例外：为满足“列表/详情批量填充、避免 N+1”，本 Mapper 允许以
 * 参数化 SQL 只读 JOIN {@code org_unit}、{@code iam_user}、{@code budget_*}
 * 等表；<b>禁止任何写操作</b>，业务模块也不得借此绕过领域仓储写表
 * （CLAUDE.local.md 模块依赖规则）。</p>
 *
 * <p>数据范围统一由参数化的 {@code scopeAll / scopePiId / scopeUserId /
 * scopeDeptIds / scopeGrantedIds} 注入，SQL 全部使用 {@code #{}} 占位符，
 * 禁止字符串拼接（防注入）。</p>
 */
@Mapper
public interface ProjectQueryMapper {

    /**
     * 分页查询项目列表。
     *
     * @param query           筛选与分页参数
     * @param scopeAll        是否全院范围
     * @param scopePiId       本人负责项目（PI 身份）；可为 {@code null}
     * @param scopeUserId     当前用户 ID（成员身份 EXISTS 条件）
     * @param scopeDeptIds    授权部门 ID 集合（标准 uuid 文本）
     * @param scopeGrantedIds 临时授权项目 ID 集合（标准 uuid 文本）
     * @param offset          偏移量
     * @param limit           每页条数
     * @return 列表行；无命中返回空列表
     */
    List<ProjectListItem> search(@Param("query") ProjectListQuery query,
                                 @Param("scopeAll") boolean scopeAll,
                                 @Param("scopePiId") String scopePiId,
                                 @Param("scopeUserId") String scopeUserId,
                                 @Param("scopeDeptIds") Collection<String> scopeDeptIds,
                                 @Param("scopeGrantedIds") Collection<String> scopeGrantedIds,
                                 @Param("offset") long offset,
                                 @Param("limit") int limit);

    /**
     * 统计项目列表总条数（与 {@link #search} 使用同一筛选与数据范围条件）。
     *
     * @param query           筛选参数
     * @param scopeAll        是否全院范围
     * @param scopePiId       本人负责项目（PI 身份）；可为 {@code null}
     * @param scopeUserId     当前用户 ID（成员身份 EXISTS 条件）
     * @param scopeDeptIds    授权部门 ID 集合
     * @param scopeGrantedIds 临时授权项目 ID 集合
     * @return 总条数
     */
    long count(@Param("query") ProjectListQuery query,
               @Param("scopeAll") boolean scopeAll,
               @Param("scopePiId") String scopePiId,
               @Param("scopeUserId") String scopeUserId,
               @Param("scopeDeptIds") Collection<String> scopeDeptIds,
               @Param("scopeGrantedIds") Collection<String> scopeGrantedIds);

    /**
     * 查询项目详情摘要（项目头 + 当前生效基线 + 页签计数）。
     *
     * @param projectId       项目 ID
     * @param scopeAll        是否全院范围
     * @param scopePiId       本人负责项目（PI 身份）；可为 {@code null}
     * @param scopeUserId     当前用户 ID（成员身份 EXISTS 条件）
     * @param scopeDeptIds    授权部门 ID 集合
     * @param scopeGrantedIds 临时授权项目 ID 集合
     * @return 摘要扁平行；项目不存在或不在数据范围内返回 {@code null}
     */
    ProjectDetailRow findDetail(@Param("projectId") String projectId,
                                @Param("scopeAll") boolean scopeAll,
                                @Param("scopePiId") String scopePiId,
                                @Param("scopeUserId") String scopeUserId,
                                @Param("scopeDeptIds") Collection<String> scopeDeptIds,
                                @Param("scopeGrantedIds") Collection<String> scopeGrantedIds);
}
