package cn.edu.rpm.execution.application.service;

import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.common.util.UuidUtils;
import cn.edu.rpm.execution.application.dto.CreateProgressReportCmd;
import cn.edu.rpm.execution.application.dto.ProgressReportCreated;
import cn.edu.rpm.execution.application.dto.ProgressReportView;
import cn.edu.rpm.execution.domain.model.ExecutionPermissions;
import cn.edu.rpm.execution.infrastructure.entity.ProgressReportEntity;
import cn.edu.rpm.execution.infrastructure.repository.ProgressReportRepository;
import cn.edu.rpm.iam.application.dto.Action;
import cn.edu.rpm.iam.application.dto.AuthContext;
import cn.edu.rpm.iam.application.dto.AuthDecision;
import cn.edu.rpm.iam.application.dto.ResourceRef;
import cn.edu.rpm.iam.domain.model.ResourceTypes;
import cn.edu.rpm.iam.domain.service.AuthorizationService;
import cn.edu.rpm.project.infrastructure.entity.ProjectEntity;
import cn.edu.rpm.project.infrastructure.entity.ProjectMemberEntity;
import cn.edu.rpm.project.infrastructure.repository.ProjectMemberRepository;
import cn.edu.rpm.project.infrastructure.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 周期报告查询与创建入口（T-EXEC-010；技术文档 04 §2.3）。
 *
 * <p>Controller 只做请求映射；本服务在入口完成项目数据范围授权后，创建委托
 * {@link ProgressReportService}（周期规则、唯一性与到期日计算），列表按项目返回。
 * 提交与审核仍由 {@link ProgressReportSubmitAppService} 与
 * {@link ProgressReportReviewService} 处理。</p>
 */
@Service
public class ProgressReportAppService {

    private final ProgressReportService progressReportService;

    private final ProgressReportRepository progressReportRepository;

    private final ProjectRepository projectRepository;

    private final ProjectMemberRepository projectMemberRepository;

    private final AuthorizationService authorizationService;

    private final Clock clock;

    /**
     * 构造报告应用服务。
     *
     * @param progressReportService    报告创建领域服务
     * @param progressReportRepository 报告仓储
     * @param projectRepository        项目仓储
     * @param projectMemberRepository  成员仓储（授权装载）
     * @param authorizationService     统一授权服务
     * @param clock                    时间基准
     */
    public ProgressReportAppService(ProgressReportService progressReportService,
                                    ProgressReportRepository progressReportRepository,
                                    ProjectRepository projectRepository,
                                    ProjectMemberRepository projectMemberRepository,
                                    AuthorizationService authorizationService,
                                    Clock clock) {
        this.progressReportService = Objects.requireNonNull(progressReportService,
                "progressReportService 不能为空");
        this.progressReportRepository = Objects.requireNonNull(progressReportRepository,
                "progressReportRepository 不能为空");
        this.projectRepository = Objects.requireNonNull(projectRepository, "projectRepository 不能为空");
        this.projectMemberRepository = Objects.requireNonNull(projectMemberRepository,
                "projectMemberRepository 不能为空");
        this.authorizationService = Objects.requireNonNull(authorizationService,
                "authorizationService 不能为空");
        this.clock = Objects.requireNonNull(clock, "clock 不能为空");
    }

    /**
     * 创建报告草稿（TC-EXEC-006）。
     *
     * @param projectId 项目 ID
     * @param cmd       创建命令
     * @param auth      当前请求授权上下文
     * @return 报告 ID、周期键、状态与到期日
     * @throws BizException 项目不存在 {@code RPM-PROJ-001}；无权限 {@code RPM-COMMON-403}；
     *                      同周期已存在 {@code RPM-EXEC-001}
     */
    @Transactional
    public ProgressReportCreated create(String projectId, CreateProgressReportCmd cmd,
                                        AuthContext auth) {
        Objects.requireNonNull(cmd, "cmd 不能为空");
        ProjectEntity project = loadProjectAndAuthorize(projectId, auth,
                ExecutionPermissions.REPORT_EDIT);
        return progressReportService.create(project.getId(), cmd.periodType(), cmd.periodKey());
    }

    /**
     * 查询项目报告列表（按周期键降序）。
     *
     * @param projectId 项目 ID
     * @param auth      当前请求授权上下文
     * @return 报告视图列表；无数据返回空列表
     * @throws BizException 项目不存在 {@code RPM-PROJ-001}；无权限 {@code RPM-COMMON-403}
     */
    @Transactional(readOnly = true)
    public List<ProgressReportView> list(String projectId, AuthContext auth) {
        ProjectEntity project = loadProjectAndAuthorize(projectId, auth, ExecutionPermissions.VIEW);
        return progressReportRepository.findByProjectId(project.getId()).stream()
                .map(ProgressReportAppService::toView)
                .toList();
    }

    private ProjectEntity loadProjectAndAuthorize(String projectId, AuthContext auth,
                                                  String permission) {
        Objects.requireNonNull(auth, "auth 不能为空");
        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BizException(ErrorCode.PROJ_001));
        ResourceRef resource = new ResourceRef(
                ResourceTypes.PROJECT,
                UuidUtils.parse(project.getId()),
                UuidUtils.parse(project.getPiId()),
                UuidUtils.parse(project.getDeptId()),
                activeMemberIds(project.getId()));
        AuthDecision decision = authorizationService.check(auth, Action.of(permission), resource);
        if (!decision.allowed()) {
            throw new BizException(ErrorCode.COMMON_403);
        }
        return project;
    }

    private Set<UUID> activeMemberIds(String projectId) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        return projectMemberRepository.findActiveByProjectId(projectId, now).stream()
                .map(ProjectMemberEntity::getUserId)
                .map(UuidUtils::parse)
                .collect(Collectors.toSet());
    }

    private static ProgressReportView toView(ProgressReportEntity report) {
        return new ProgressReportView(report.getId(), report.getProjectId(),
                report.getPeriodType(), report.getPeriodKey(), report.getDueDate(),
                report.getStatus(), report.getCurrentVersionId(), report.getVersion(),
                report.getCreatedAt());
    }
}
