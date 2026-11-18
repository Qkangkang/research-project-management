package cn.edu.rpm.project.api;

import cn.edu.rpm.application.domain.model.ProjectCreationCommand;
import cn.edu.rpm.common.api.PageVO;
import cn.edu.rpm.common.api.Result;
import cn.edu.rpm.iam.application.annotation.RequirePermission;
import cn.edu.rpm.iam.application.context.AuthContextHolder;
import cn.edu.rpm.iam.application.dto.AuthContext;
import cn.edu.rpm.project.api.dto.ProjectRequests;
import cn.edu.rpm.project.application.dto.AddProjectMemberCmd;
import cn.edu.rpm.project.application.dto.ChangeRequestCreated;
import cn.edu.rpm.project.application.dto.CloseProjectRiskCmd;
import cn.edu.rpm.project.application.dto.CreateChangeRequestCmd;
import cn.edu.rpm.project.application.dto.CreateProjectRiskCmd;
import cn.edu.rpm.project.application.dto.InitiateHandoverCmd;
import cn.edu.rpm.project.application.dto.ProjectDetailSummary;
import cn.edu.rpm.project.application.dto.ProjectHandoverInitiated;
import cn.edu.rpm.project.application.dto.ProjectListItem;
import cn.edu.rpm.project.application.dto.ProjectListQuery;
import cn.edu.rpm.project.application.dto.ProjectMemberVO;
import cn.edu.rpm.project.application.dto.ProjectRiskView;
import cn.edu.rpm.project.application.dto.ProjectStateChanged;
import cn.edu.rpm.project.application.service.ChangeRequestAppService;
import cn.edu.rpm.project.application.service.ProjectHandoverAppService;
import cn.edu.rpm.project.application.service.ProjectMemberAppService;
import cn.edu.rpm.project.application.service.ProjectQueryAppService;
import cn.edu.rpm.project.domain.model.ProjectPermissions;
import cn.edu.rpm.project.domain.service.MilestoneService;
import cn.edu.rpm.project.domain.service.ProjectLifecycleService;
import cn.edu.rpm.project.domain.service.ProjectRiskService;
import cn.edu.rpm.project.infrastructure.entity.MilestoneEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 项目接口（T-PRJ-023；技术文档 04 §2.1、07 §3.1）。
 *
 * <p>Controller 只做请求映射与身份提取，业务规则、授权判定、状态迁移、
 * 幂等与审计全部由应用/领域服务完成。{@code @RequirePermission} 仅做功能权限粗筛。</p>
 */
@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final ProjectQueryAppService projectQueryAppService;

    private final ProjectMemberAppService projectMemberAppService;

    private final MilestoneService milestoneService;

    private final ProjectRiskService projectRiskService;

    private final ProjectLifecycleService projectLifecycleService;

    private final ChangeRequestAppService changeRequestAppService;

    private final ProjectHandoverAppService projectHandoverAppService;

    /**
     * 构造控制器。
     *
     * @param projectQueryAppService    查询服务
     * @param projectMemberAppService   成员服务
     * @param milestoneService          里程碑服务
     * @param projectRiskService        风险服务
     * @param projectLifecycleService   生命周期服务
     * @param changeRequestAppService   变更服务
     * @param projectHandoverAppService 交接服务
     */
    public ProjectController(ProjectQueryAppService projectQueryAppService,
                             ProjectMemberAppService projectMemberAppService,
                             MilestoneService milestoneService,
                             ProjectRiskService projectRiskService,
                             ProjectLifecycleService projectLifecycleService,
                             ChangeRequestAppService changeRequestAppService,
                             ProjectHandoverAppService projectHandoverAppService) {
        this.projectQueryAppService = projectQueryAppService;
        this.projectMemberAppService = projectMemberAppService;
        this.milestoneService = milestoneService;
        this.projectRiskService = projectRiskService;
        this.projectLifecycleService = projectLifecycleService;
        this.changeRequestAppService = changeRequestAppService;
        this.projectHandoverAppService = projectHandoverAppService;
    }

    /**
     * 项目列表。
     *
     * @param keyword     关键字
     * @param projectType 项目类型
     * @param deptId      部门
     * @param piId        负责人
     * @param status      状态
     * @param year        年度
     * @param page        页码（从 1 开始）
     * @param size        每页条数
     * @return 分页结果
     */
    @GetMapping
    @RequirePermission(ProjectPermissions.VIEW)
    public Result<PageVO<ProjectListItem>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String projectType,
            @RequestParam(required = false) String deptId,
            @RequestParam(required = false) String piId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer year,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        ProjectListQuery query = new ProjectListQuery();
        query.setKeyword(keyword);
        query.setProjectType(projectType);
        query.setDeptId(deptId);
        query.setPiId(piId);
        query.setStatus(status);
        query.setYear(year);
        query.setPage(page);
        query.setSize(size);
        return Result.ok(projectQueryAppService.list(query, AuthContextHolder.require()));
    }

    /**
     * 项目详情（头 + 八页签摘要）。
     *
     * @param projectId 项目 ID
     * @return 详情摘要
     */
    @GetMapping("/{projectId}")
    @RequirePermission(ProjectPermissions.VIEW)
    public Result<ProjectDetailSummary> detail(@PathVariable("projectId") String projectId) {
        return Result.ok(projectQueryAppService.detail(projectId, AuthContextHolder.require()));
    }

    /**
     * 项目成员列表。
     *
     * @param projectId 项目 ID
     * @return 成员列表
     */
    @GetMapping("/{projectId}/members")
    @RequirePermission(ProjectPermissions.VIEW)
    public Result<List<ProjectMemberVO>> members(@PathVariable("projectId") String projectId) {
        return Result.ok(projectMemberAppService.list(projectId, AuthContextHolder.require()));
    }

    /**
     * 新增项目成员。
     *
     * @param projectId 项目 ID
     * @param request   请求体
     * @return 新成员 ID
     */
    @PostMapping("/{projectId}/members")
    @RequirePermission(ProjectPermissions.MEMBER_MANAGE)
    public Result<String> addMember(@PathVariable("projectId") String projectId,
                                    @RequestBody ProjectRequests.AddMemberRequest request) {
        AuthContext auth = AuthContextHolder.require();
        return Result.ok(projectMemberAppService.add(projectId, new AddProjectMemberCmd(
                request.userId(), request.projectRole(), request.contributionDesc(),
                request.validFrom(), request.validTo()), auth));
    }

    /**
     * 里程碑列表（当前生效基线）。
     *
     * @param projectId 项目 ID
     * @return 里程碑视图
     */
    @GetMapping("/{projectId}/milestones")
    @RequirePermission(ProjectPermissions.VIEW)
    public Result<List<ProjectRequests.MilestoneView>> milestones(
            @PathVariable("projectId") String projectId) {
        List<ProjectRequests.MilestoneView> items = milestoneService
                .list(projectId, AuthContextHolder.require()).stream()
                .map(ProjectController::toView)
                .toList();
        return Result.ok(items);
    }

    /**
     * 新增里程碑计划。
     *
     * @param projectId 项目 ID
     * @param request   请求体
     * @return 新里程碑视图
     */
    @PostMapping("/{projectId}/milestones")
    @RequirePermission(ProjectPermissions.MILESTONE_MANAGE)
    public Result<List<ProjectRequests.MilestoneView>> createMilestones(
            @PathVariable("projectId") String projectId,
            @RequestBody ProjectRequests.CreateMilestonesRequest request) {
        List<ProjectCreationCommand.MilestonePlan> plans = request.milestones() == null
                ? List.of() : request.milestones().stream()
                .map(item -> new ProjectCreationCommand.MilestonePlan(item.code(), item.title(),
                        item.planDate(), item.ownerId(), item.completionCriteria()))
                .toList();
        return Result.ok(milestoneService
                .createFromBaseline(projectId, request.baselineId(), plans).stream()
                .map(ProjectController::toView)
                .toList());
    }

    /**
     * 标记里程碑完成。
     *
     * @param projectId   项目 ID
     * @param milestoneId 里程碑 ID
     * @param actualDate  实际完成日期（可空取当天）
     * @return 完成后的里程碑
     */
    @PostMapping("/{projectId}/milestones/{milestoneId}/complete")
    @RequirePermission(ProjectPermissions.MILESTONE_MANAGE)
    public Result<ProjectRequests.MilestoneView> completeMilestone(
            @PathVariable("projectId") String projectId,
            @PathVariable("milestoneId") String milestoneId,
            @RequestParam(required = false) LocalDate actualDate) {
        return Result.ok(toView(milestoneService.complete(milestoneId, actualDate,
                AuthContextHolder.require())));
    }

    /**
     * 项目风险列表。
     *
     * @param projectId 项目 ID
     * @return 风险视图
     */
    @GetMapping("/{projectId}/risks")
    @RequirePermission(ProjectPermissions.VIEW)
    public Result<List<ProjectRiskView>> risks(@PathVariable("projectId") String projectId) {
        return Result.ok(projectRiskService.list(projectId, AuthContextHolder.require()));
    }

    /**
     * 登记项目风险。
     *
     * @param projectId 项目 ID
     * @param request   请求体
     * @return 风险视图
     */
    @PostMapping("/{projectId}/risks")
    @RequirePermission(ProjectPermissions.RISK_CREATE)
    public Result<ProjectRiskView> createRisk(@PathVariable("projectId") String projectId,
                                             @RequestBody ProjectRequests.CreateRiskRequest request) {
        AuthContext auth = AuthContextHolder.require();
        return Result.ok(projectRiskService.create(projectId, new CreateProjectRiskCmd(
                request.ownerId(), request.riskType(), request.title(), request.description(),
                request.riskLevel(), request.dueDate(), request.idempotencyKey()), auth));
    }

    /**
     * 关闭项目风险。
     *
     * @param projectId 项目 ID
     * @param riskId    风险 ID
     * @param request   请求体
     * @return 关闭后的风险视图
     */
    @PostMapping("/{projectId}/risks/{riskId}/close")
    @RequirePermission(ProjectPermissions.RISK_CLOSE)
    public Result<ProjectRiskView> closeRisk(@PathVariable("projectId") String projectId,
                                            @PathVariable("riskId") String riskId,
                                            @RequestBody ProjectRequests.CloseRiskRequest request) {
        AuthContext auth = AuthContextHolder.require();
        return Result.ok(projectRiskService.close(riskId,
                new CloseProjectRiskCmd(request.resolution(), request.evidenceFileId()), auth));
    }

    /**
     * 暂停项目。
     *
     * @param projectId 项目 ID
     * @param request   请求体（原因）
     * @return 新状态
     */
    @PostMapping("/{projectId}/pause")
    @RequirePermission(ProjectPermissions.STATE_CHANGE)
    public Result<ProjectStateChanged> pause(@PathVariable("projectId") String projectId,
                                             @RequestBody ProjectRequests.StateChangeRequest request) {
        return Result.ok(projectLifecycleService.pause(projectId, request.reason(),
                AuthContextHolder.require()));
    }

    /**
     * 恢复项目。
     *
     * @param projectId 项目 ID
     * @return 新状态
     */
    @PostMapping("/{projectId}/resume")
    @RequirePermission(ProjectPermissions.STATE_CHANGE)
    public Result<ProjectStateChanged> resume(@PathVariable("projectId") String projectId) {
        return Result.ok(projectLifecycleService.resume(projectId,
                AuthContextHolder.require()));
    }

    /**
     * 终止项目。
     *
     * @param projectId 项目 ID
     * @param request   请求体（原因必填）
     * @return 新状态
     */
    @PostMapping("/{projectId}/terminate")
    @RequirePermission(ProjectPermissions.STATE_CHANGE)
    public Result<ProjectStateChanged> terminate(@PathVariable("projectId") String projectId,
                                                 @RequestBody ProjectRequests.StateChangeRequest request) {
        return Result.ok(projectLifecycleService.terminate(projectId, request.reason(),
                AuthContextHolder.require()));
    }

    /**
     * 发起项目变更。
     *
     * @param projectId    项目 ID
     * @param idempotencyKey 幂等键请求头
     * @param request      请求体
     * @return 变更单与版本
     */
    @PostMapping("/{projectId}/changes")
    @RequirePermission(ProjectPermissions.CHANGE_CREATE)
    public Result<ChangeRequestCreated> createChange(
            @PathVariable("projectId") String projectId,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody ProjectRequests.CreateChangeRequest request) {
        AuthContext auth = AuthContextHolder.require();
        return Result.ok(changeRequestAppService.create(projectId,
                new CreateChangeRequestCmd(request.changeType(), request.effectiveDate(),
                        request.payload(), request.reason(), request.attachmentFileIds(),
                        ProjectRequests.parseRequestId(idempotencyKey)), auth));
    }

    /**
     * 发起负责人交接。
     *
     * @param projectId     项目 ID
     * @param idempotencyKey 幂等键请求头
     * @param request       请求体
     * @return 交接单与实例
     */
    @PostMapping("/{projectId}/handover")
    @RequirePermission(ProjectPermissions.HANDOVER)
    public Result<ProjectHandoverInitiated> handover(
            @PathVariable("projectId") String projectId,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody ProjectRequests.HandoverRequest request) {
        AuthContext auth = AuthContextHolder.require();
        List<InitiateHandoverCmd.PendingItem> items = request.pendingItems() == null
                ? List.of() : request.pendingItems().stream()
                .map(item -> new InitiateHandoverCmd.PendingItem(
                        item.type(), item.id(), item.action()))
                .toList();
        return Result.ok(projectHandoverAppService.initiate(projectId,
                new InitiateHandoverCmd(request.toUserId(), request.effectiveAt(), items,
                        request.reason(), ProjectRequests.parseRequestId(idempotencyKey)), auth));
    }

    private static ProjectRequests.MilestoneView toView(MilestoneEntity milestone) {
        return new ProjectRequests.MilestoneView(milestone.getId(), milestone.getCode(),
                milestone.getTitle(), milestone.getPlanDate(), milestone.getCurrentPlanDate(),
                milestone.getStatus());
    }
}
