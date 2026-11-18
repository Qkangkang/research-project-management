package cn.edu.rpm.project.infrastructure.repository;

import cn.edu.rpm.common.api.PageVO;
import cn.edu.rpm.iam.application.dto.ProjectScope;
import cn.edu.rpm.project.application.dto.ProjectDetailSummary;
import cn.edu.rpm.project.application.dto.ProjectListItem;
import cn.edu.rpm.project.application.dto.ProjectListQuery;

import java.util.Optional;

/**
 * 项目只读查询仓储（T-PRJ-011；技术文档 04 §3.6/§3.7）。
 *
 * <p>与单表 CRUD 仓储不同，本仓储承载跨表只读查询（列表 + 详情摘要）。
 * 所有方法必须显式接收 {@link ProjectScope} 与当前用户 ID：</p>
 * <ul>
 *   <li>{@code scope} 由应用服务通过 {@code AuthorizationService.resolveProjectScope}
 *       解析，仓储只消费、不再放宽；</li>
 *   <li>{@code viewerId} 用于 {@code EXISTS project_member} 成员身份判定
 *       （{@code ProjectScope} 刻意不承载用户 ID）。</li>
 * </ul>
 * <p>列表、详情、导出与异步任务必须复用同一 scope 实例（TC-IAM-015 五处同权）。</p>
 */
public interface ProjectQueryRepository {

    /**
     * 分页查询项目列表（含部门名、PI 姓名、预算/支付汇总、最高未关闭风险）。
     *
     * @param query    筛选与分页参数（非空）
     * @param scope    数据范围（非空）
     * @param viewerId 当前用户 ID（成员身份判定；非空）
     * @return 分页结果；无命中返回空 items 且 total 为 0
     */
    PageVO<ProjectListItem> search(ProjectListQuery query, ProjectScope scope, String viewerId);

    /**
     * 查询项目详情摘要（项目头 + 生效基线 + 页签计数）。
     *
     * @param projectId 项目 ID
     * @param scope     数据范围
     * @param viewerId  当前用户 ID（成员身份判定）
     * @return 摘要；项目不存在或不在数据范围内返回空
     */
    Optional<ProjectDetailSummary> findDetailSummary(String projectId, ProjectScope scope, String viewerId);
}
