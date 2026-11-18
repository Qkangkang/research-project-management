package cn.edu.rpm.project.application.service;

import cn.edu.rpm.common.api.PageVO;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.common.util.UuidUtils;
import cn.edu.rpm.iam.application.dto.Action;
import cn.edu.rpm.iam.application.dto.AuthContext;
import cn.edu.rpm.iam.application.dto.AuthDecision;
import cn.edu.rpm.iam.application.dto.ProjectQuery;
import cn.edu.rpm.iam.application.dto.ProjectScope;
import cn.edu.rpm.iam.application.dto.ResourceRef;
import cn.edu.rpm.iam.domain.model.ResourceTypes;
import cn.edu.rpm.iam.domain.service.AuthorizationService;
import cn.edu.rpm.project.application.dto.ProjectDetailSummary;
import cn.edu.rpm.project.application.dto.ProjectListItem;
import cn.edu.rpm.project.application.dto.ProjectListQuery;
import cn.edu.rpm.project.domain.model.ProjectPermissions;
import cn.edu.rpm.project.infrastructure.entity.ProjectEntity;
import cn.edu.rpm.project.infrastructure.entity.ProjectMemberEntity;
import cn.edu.rpm.project.infrastructure.repository.ProjectMemberRepository;
import cn.edu.rpm.project.infrastructure.repository.ProjectQueryRepository;
import cn.edu.rpm.project.infrastructure.repository.ProjectRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 项目查询应用服务（T-PRJ-017；技术文档 04 §3.6/§3.7）。
 *
 * <p>数据范围在应用服务入口通过 {@code AuthorizationService.resolveProjectScope}
 * 解析后注入仓储，列表/详情/导出/异步任务使用同一 scope 实例（TC-IAM-015 五处同权）。
 * 列表无匹配返回空分页（{@code code=OK, items=[], total=0}，TC-PRJ-002），
 * 不返回 404；详情不存在返回 {@code RPM-PROJ-001}，存在但无数据范围返回
 * {@code RPM-COMMON-403} 且不泄露标题/编号/预算（TC-PRJ-004/035）。</p>
 */
@Service
public class ProjectQueryAppService {

    private final ProjectRepository projectRepository;

    private final ProjectMemberRepository projectMemberRepository;

    private final ProjectQueryRepository projectQueryRepository;

    private final AuthorizationService authorizationService;

    private final Clock clock;

    /**
     * 构造查询应用服务。
     *
     * @param projectRepository       项目仓储
     * @param projectMemberRepository 成员仓储（详情授权装载）
     * @param projectQueryRepository  只读查询仓储
     * @param authorizationService    统一授权服务
     * @param clock                   时间基准
     */
    public ProjectQueryAppService(ProjectRepository projectRepository,
                                  ProjectMemberRepository projectMemberRepository,
                                  ProjectQueryRepository projectQueryRepository,
                                  AuthorizationService authorizationService,
                                  Clock clock) {
        this.projectRepository = Objects.requireNonNull(projectRepository, "projectRepository 不能为空");
        this.projectMemberRepository = Objects.requireNonNull(projectMemberRepository,
                "projectMemberRepository 不能为空");
        this.projectQueryRepository = Objects.requireNonNull(projectQueryRepository,
                "projectQueryRepository 不能为空");
        this.authorizationService = Objects.requireNonNull(authorizationService,
                "authorizationService 不能为空");
        this.clock = Objects.requireNonNull(clock, "clock 不能为空");
    }

    /**
     * 查询项目列表（TC-PRJ-001 / TC-PRJ-002）。
     *
     * @param query 筛选与分页参数（空取默认值）
     * @param auth  当前请求授权上下文
     * @return 分页结果；无匹配时 items 为空且 total 为 0
     */
    public PageVO<ProjectListItem> list(ProjectListQuery query, AuthContext auth) {
        Objects.requireNonNull(auth, "auth 不能为空");
        ProjectListQuery effectiveQuery = query == null ? new ProjectListQuery() : query;
        ProjectScope scope = authorizationService.resolveProjectScope(auth,
                ProjectQuery.ofAction(ProjectPermissions.VIEW));
        return projectQueryRepository.search(effectiveQuery, scope,
                UuidUtils.formatCompact(auth.userId()));
    }

    /**
     * 查询项目详情摘要（TC-PRJ-003 / TC-PRJ-004 / TC-PRJ-035）。
     *
     * @param projectId 项目 ID
     * @param auth      当前请求授权上下文
     * @return 项目头 + 当前生效基线 + 页签摘要
     * @throws BizException 项目不存在 {@code RPM-PROJ-001}；无数据范围/项目身份
     *                      {@code RPM-COMMON-403}
     */
    public ProjectDetailSummary detail(String projectId, AuthContext auth) {
        Objects.requireNonNull(auth, "auth 不能为空");
        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BizException(ErrorCode.PROJ_001));

        ResourceRef resource = new ResourceRef(
                ResourceTypes.PROJECT,
                UuidUtils.parse(project.getId()),
                UuidUtils.parse(project.getPiId()),
                UuidUtils.parse(project.getDeptId()),
                activeMemberIds(project.getId()));
        AuthDecision decision = authorizationService.check(auth,
                Action.of(ProjectPermissions.VIEW), resource);
        if (!decision.allowed()) {
            throw new BizException(ErrorCode.COMMON_403);
        }

        ProjectScope scope = authorizationService.resolveProjectScope(auth,
                ProjectQuery.ofAction(ProjectPermissions.VIEW));
        return projectQueryRepository.findDetailSummary(projectId, scope,
                        UuidUtils.formatCompact(auth.userId()))
                .orElseThrow(() -> new BizException(ErrorCode.COMMON_403));
    }

    private Set<UUID> activeMemberIds(String projectId) {
        OffsetDateTime now = clock.instant().atZone(clock.getZone()).toOffsetDateTime();
        return projectMemberRepository.findActiveByProjectId(projectId, now).stream()
                .map(ProjectMemberEntity::getUserId)
                .map(UuidUtils::parse)
                .collect(Collectors.toSet());
    }
}
