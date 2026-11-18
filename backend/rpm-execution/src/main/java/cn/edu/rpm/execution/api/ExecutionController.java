package cn.edu.rpm.execution.api;

import cn.edu.rpm.common.api.PageVO;
import cn.edu.rpm.common.api.Result;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.execution.api.dto.ExecutionRequests;
import cn.edu.rpm.execution.application.dto.CompleteProjectTaskCmd;
import cn.edu.rpm.execution.application.dto.CreateProgressReportCmd;
import cn.edu.rpm.execution.application.dto.CreateProjectTaskCmd;
import cn.edu.rpm.execution.application.dto.DeliverableCreated;
import cn.edu.rpm.execution.application.dto.DeliverableView;
import cn.edu.rpm.execution.application.dto.ProgressReportCreated;
import cn.edu.rpm.execution.application.dto.ProgressReportReviewed;
import cn.edu.rpm.execution.application.dto.ProgressReportSubmitted;
import cn.edu.rpm.execution.application.dto.ProgressReportView;
import cn.edu.rpm.execution.application.dto.ProjectTaskCreated;
import cn.edu.rpm.execution.application.dto.ProjectTaskStateChanged;
import cn.edu.rpm.execution.application.dto.ProjectTaskView;
import cn.edu.rpm.execution.application.dto.ReviewProgressReportCmd;
import cn.edu.rpm.execution.application.dto.SubmitProgressReportCmd;
import cn.edu.rpm.execution.application.dto.UploadDeliverableCmd;
import cn.edu.rpm.execution.application.service.DeliverableService;
import cn.edu.rpm.execution.application.service.ProgressReportAppService;
import cn.edu.rpm.execution.application.service.ProgressReportReviewService;
import cn.edu.rpm.execution.application.service.ProgressReportSubmitAppService;
import cn.edu.rpm.execution.application.service.ProjectTaskAppService;
import cn.edu.rpm.execution.domain.model.ExecutionPermissions;
import cn.edu.rpm.iam.application.annotation.RequirePermission;
import cn.edu.rpm.iam.application.context.AuthContextHolder;
import cn.edu.rpm.iam.application.dto.AuthContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 执行与协作接口（T-EXEC-010；技术文档 04 §2.3、09 §4.1）。
 *
 * <p>覆盖周期报告列表/创建/提交/审核、交付物列表/上传、轻任务创建/列表/开始/完成/取消。
 * Controller 只做请求映射与功能权限粗筛；项目数据范围授权、状态机、幂等与事件
 * 全部在应用服务入口完成。</p>
 */
@RestController
@RequestMapping("/api/v1")
public class ExecutionController {

    private final ProgressReportAppService progressReportAppService;

    private final ProgressReportSubmitAppService progressReportSubmitAppService;

    private final ProgressReportReviewService progressReportReviewService;

    private final DeliverableService deliverableService;

    private final ProjectTaskAppService projectTaskAppService;

    /**
     * 构造执行接口控制器。
     *
     * @param progressReportAppService        报告查询/创建服务
     * @param progressReportSubmitAppService  报告提交服务
     * @param progressReportReviewService     报告审核服务
     * @param deliverableService              交付物服务
     * @param projectTaskAppService           轻任务服务
     */
    public ExecutionController(ProgressReportAppService progressReportAppService,
                               ProgressReportSubmitAppService progressReportSubmitAppService,
                               ProgressReportReviewService progressReportReviewService,
                               DeliverableService deliverableService,
                               ProjectTaskAppService projectTaskAppService) {
        this.progressReportAppService = progressReportAppService;
        this.progressReportSubmitAppService = progressReportSubmitAppService;
        this.progressReportReviewService = progressReportReviewService;
        this.deliverableService = deliverableService;
        this.projectTaskAppService = projectTaskAppService;
    }

    /**
     * 报告列表。
     *
     * @param projectId 项目 ID
     * @return 报告列表
     */
    @GetMapping("/projects/{projectId}/progress-reports")
    @RequirePermission(ExecutionPermissions.VIEW)
    public Result<List<ProgressReportView>> listReports(
            @PathVariable("projectId") String projectId) {
        AuthContext auth = AuthContextHolder.require();
        return Result.ok(progressReportAppService.list(projectId, auth));
    }

    /**
     * 创建报告草稿。
     *
     * @param projectId 项目 ID
     * @param request   请求体
     * @return 报告标识与到期日
     */
    @PostMapping("/projects/{projectId}/progress-reports")
    @RequirePermission(ExecutionPermissions.REPORT_EDIT)
    public Result<ProgressReportCreated> createReport(
            @PathVariable("projectId") String projectId,
            @RequestBody ExecutionRequests.CreateReportRequest request) {
        AuthContext auth = AuthContextHolder.require();
        return Result.ok(progressReportAppService.create(projectId,
                new CreateProgressReportCmd(request.periodType(), request.periodKey()), auth));
    }

    /**
     * 提交报告。
     *
     * @param reportId       报告 ID
     * @param idempotencyKey 幂等键
     * @param request        请求体
     * @return 新版本号与状态
     */
    @PostMapping("/progress-reports/{reportId}/submit")
    @RequirePermission(ExecutionPermissions.REPORT_SUBMIT)
    public Result<ProgressReportSubmitted> submitReport(
            @PathVariable("reportId") String reportId,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody ExecutionRequests.SubmitReportRequest request) {
        AuthContext auth = AuthContextHolder.require();
        return Result.ok(progressReportSubmitAppService.submit(reportId,
                new SubmitProgressReportCmd(request.expectedVersion(), request.content(),
                        request.deliverableFileIds(),
                        ExecutionRequests.parseRequestId(idempotencyKey)), auth));
    }

    /**
     * 审核报告（退回或通过）。
     *
     * @param reportId       报告 ID
     * @param idempotencyKey 幂等键
     * @param request        请求体
     * @return 审核结果
     */
    @PostMapping("/progress-reports/{reportId}/review")
    @RequirePermission(ExecutionPermissions.REPORT_REVIEW)
    public Result<ProgressReportReviewed> reviewReport(
            @PathVariable("reportId") String reportId,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody ExecutionRequests.ReviewReportRequest request) {
        AuthContext auth = AuthContextHolder.require();
        ReviewProgressReportCmd cmd = new ReviewProgressReportCmd(request.comment(),
                ExecutionRequests.parseRequestId(idempotencyKey));
        if ("RETURN".equalsIgnoreCase(request.action())) {
            return Result.ok(progressReportReviewService.returnReport(reportId, cmd, auth));
        }
        if ("APPROVE".equalsIgnoreCase(request.action())) {
            return Result.ok(progressReportReviewService.approve(reportId, cmd, auth));
        }
        throw new BizException(ErrorCode.COMMON_400, "审核动作必须为 RETURN 或 APPROVE");
    }

    /**
     * 交付物列表。
     *
     * @param projectId   项目 ID
     * @param milestoneId 里程碑过滤
     * @param page        页码
     * @param size        每页条数
     * @return 交付物分页
     */
    @GetMapping("/projects/{projectId}/deliverables")
    @RequirePermission(ExecutionPermissions.VIEW)
    public Result<PageVO<DeliverableView>> listDeliverables(
            @PathVariable("projectId") String projectId,
            @RequestParam(name = "milestoneId", required = false) String milestoneId,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        AuthContext auth = AuthContextHolder.require();
        return Result.ok(deliverableService.list(projectId, milestoneId, page, size, auth));
    }

    /**
     * 上传交付物。
     *
     * @param projectId 项目 ID
     * @param request   请求体
     * @return 交付物标识与已校验摘要
     */
    @PostMapping("/projects/{projectId}/deliverables")
    @RequirePermission(ExecutionPermissions.DELIVERABLE_UPLOAD)
    public Result<DeliverableCreated> uploadDeliverable(
            @PathVariable("projectId") String projectId,
            @RequestBody ExecutionRequests.UploadDeliverableRequest request) {
        AuthContext auth = AuthContextHolder.require();
        return Result.ok(deliverableService.upload(projectId, new UploadDeliverableCmd(
                request.fileId(), request.title(), request.milestoneId(),
                request.reportVersionId(), request.sha256()), auth));
    }

    /**
     * 创建并分派轻任务。
     *
     * @param projectId 项目 ID
     * @param request   请求体
     * @return 任务标识与状态
     */
    @PostMapping("/projects/{projectId}/tasks")
    @RequirePermission(ExecutionPermissions.TASK_ASSIGN)
    public Result<ProjectTaskCreated> createTask(
            @PathVariable("projectId") String projectId,
            @RequestBody ExecutionRequests.CreateTaskRequest request) {
        AuthContext auth = AuthContextHolder.require();
        return Result.ok(projectTaskAppService.create(projectId, new CreateProjectTaskCmd(
                request.title(), request.description(), request.assigneeId(),
                request.dueDate(), request.deliverableId()), auth));
    }

    /**
     * 项目任务列表。
     *
     * @param projectId 项目 ID
     * @param status    状态过滤
     * @return 任务列表
     */
    @GetMapping("/projects/{projectId}/tasks")
    @RequirePermission(ExecutionPermissions.VIEW)
    public Result<List<ProjectTaskView>> listTasks(
            @PathVariable("projectId") String projectId,
            @RequestParam(name = "status", required = false) String status) {
        AuthContext auth = AuthContextHolder.require();
        return Result.ok(projectTaskAppService.list(projectId, status, auth));
    }

    /**
     * 开始任务。
     *
     * @param taskId 任务 ID
     * @return 新状态
     */
    @PostMapping("/tasks/{taskId}/start")
    @RequirePermission(ExecutionPermissions.VIEW)
    public Result<ProjectTaskStateChanged> startTask(@PathVariable("taskId") String taskId) {
        AuthContext auth = AuthContextHolder.require();
        return Result.ok(projectTaskAppService.start(taskId, auth));
    }

    /**
     * 完成任务。
     *
     * @param taskId  任务 ID
     * @param request 请求体
     * @return 新状态
     */
    @PostMapping("/tasks/{taskId}/complete")
    @RequirePermission(ExecutionPermissions.VIEW)
    public Result<ProjectTaskStateChanged> completeTask(
            @PathVariable("taskId") String taskId,
            @RequestBody ExecutionRequests.CompleteTaskRequest request) {
        AuthContext auth = AuthContextHolder.require();
        return Result.ok(projectTaskAppService.complete(taskId,
                new CompleteProjectTaskCmd(request.deliverableId()), auth));
    }

    /**
     * 取消任务。
     *
     * @param taskId  任务 ID
     * @param request 请求体
     * @return 新状态
     */
    @PostMapping("/tasks/{taskId}/cancel")
    @RequirePermission(ExecutionPermissions.VIEW)
    public Result<ProjectTaskStateChanged> cancelTask(
            @PathVariable("taskId") String taskId,
            @RequestBody ExecutionRequests.CancelTaskRequest request) {
        AuthContext auth = AuthContextHolder.require();
        return Result.ok(projectTaskAppService.cancel(taskId, request.reason(), auth));
    }
}
