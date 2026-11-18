package cn.edu.rpm.project.infrastructure.repository;

import cn.edu.rpm.common.api.PageVO;
import cn.edu.rpm.common.util.UuidUtils;
import cn.edu.rpm.iam.application.dto.ProjectScope;
import cn.edu.rpm.project.application.dto.ProjectDetailSummary;
import cn.edu.rpm.project.application.dto.ProjectListItem;
import cn.edu.rpm.project.application.dto.ProjectListQuery;
import cn.edu.rpm.project.application.dto.ProjectSummary;
import cn.edu.rpm.project.infrastructure.mapper.ProjectQueryMapper;
import cn.edu.rpm.project.infrastructure.mapper.dto.ProjectDetailRow;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 项目只读查询仓储实现（T-PRJ-011）。
 *
 * <p>把 {@link ProjectScope} 的 {@link UUID} 集合转为标准 uuid 文本后交给参数化 SQL；
 * 详情摘要由扁平行组装为对外响应对象。金额缺省值统一为 {@link BigDecimal#ZERO}，
 * 禁止返回 {@code null} 金额（前端展示与导出按 0 处理）。</p>
 */
@Repository
public class ProjectQueryRepositoryImpl implements ProjectQueryRepository {

    private final ProjectQueryMapper projectQueryMapper;

    public ProjectQueryRepositoryImpl(ProjectQueryMapper projectQueryMapper) {
        this.projectQueryMapper = projectQueryMapper;
    }

    @Override
    public PageVO<ProjectListItem> search(ProjectListQuery query, ProjectScope scope, String viewerId) {
        Objects.requireNonNull(query, "query 不能为空");
        Objects.requireNonNull(scope, "scope 不能为空");
        Objects.requireNonNull(viewerId, "viewerId 不能为空");

        List<String> deptIds = formatDeptIds(scope);
        List<String> grantedIds = formatGrantedIds(scope);

        long total = projectQueryMapper.count(query, scope.scopeAll(), scopePiId(scope),
                viewerId, deptIds, grantedIds);
        if (total == 0) {
            return PageVO.of(List.of(), query.getPage(), query.getSize(), 0);
        }
        List<ProjectListItem> items = projectQueryMapper.search(query, scope.scopeAll(),
                scopePiId(scope), viewerId, deptIds, grantedIds, query.getOffset(), query.getSize());
        return PageVO.of(items, query.getPage(), query.getSize(), total);
    }

    @Override
    public Optional<ProjectDetailSummary> findDetailSummary(String projectId, ProjectScope scope,
                                                            String viewerId) {
        Objects.requireNonNull(projectId, "projectId 不能为空");
        Objects.requireNonNull(scope, "scope 不能为空");
        Objects.requireNonNull(viewerId, "viewerId 不能为空");

        ProjectDetailRow row = projectQueryMapper.findDetail(projectId, scope.scopeAll(),
                scopePiId(scope), viewerId, formatDeptIds(scope), formatGrantedIds(scope));
        if (row == null) {
            return Optional.empty();
        }
        ProjectSummary summary = new ProjectSummary(
                zeroIfNull(row.getMilestoneTotal()),
                zeroIfNull(row.getMilestoneCompleted()),
                zeroIfNull(row.getReportDue()),
                row.getBudgetTotal() == null ? BigDecimal.ZERO : row.getBudgetTotal(),
                row.getAvailableAmount() == null ? BigDecimal.ZERO : row.getAvailableAmount(),
                zeroIfNull(row.getOutputCount()),
                zeroIfNull(row.getOpenRiskCount()));
        return Optional.of(new ProjectDetailSummary(row.getProjectId(), row.getProjectNo(),
                row.getTitle(), row.getProjectType(), row.getPiId(), row.getPiName(),
                row.getDeptId(), row.getDeptName(), row.getBaselineId(),
                row.getBaselineVersion(), row.getStatus(), row.getStartDate(),
                row.getEndDate(), summary.budgetTotal(), row.getMemberCount(),
                row.getCreatedAt(), row.getUpdatedAt(), summary));
    }

    private static String scopePiId(ProjectScope scope) {
        return scope.piId() == null ? null : UuidUtils.format(scope.piId());
    }

    private static List<String> formatDeptIds(ProjectScope scope) {
        return scope.deptIds().stream().map(UuidUtils::format).collect(Collectors.toList());
    }

    private static List<String> formatGrantedIds(ProjectScope scope) {
        return scope.grantedProjectIds().stream().map(UuidUtils::format).collect(Collectors.toList());
    }

    private static long zeroIfNull(Long value) {
        return value == null ? 0L : value;
    }
}
