package cn.edu.rpm.application.api;

import cn.edu.rpm.application.api.dto.ApplicationDetailView;
import cn.edu.rpm.application.api.dto.ApplicationListItem;
import cn.edu.rpm.application.api.dto.ApplicationOptionsView;
import cn.edu.rpm.application.api.dto.ApplicationActionRequests;
import cn.edu.rpm.application.api.dto.CreateApplicationRequest;
import cn.edu.rpm.application.api.dto.SubmitApplicationRequest;
import cn.edu.rpm.application.api.dto.UpdateDraftRequest;
import cn.edu.rpm.application.application.dto.ApplicationCreated;
import cn.edu.rpm.application.application.dto.ApplicationDraftSaved;
import cn.edu.rpm.application.application.dto.ApplicationListQuery;
import cn.edu.rpm.application.application.dto.ApplicationSubmission;
import cn.edu.rpm.application.application.dto.ApplicationWithdrawal;
import cn.edu.rpm.application.application.dto.CreateApplicationCmd;
import cn.edu.rpm.application.application.dto.DecisionCmd;
import cn.edu.rpm.application.application.dto.DecisionResult;
import cn.edu.rpm.application.application.dto.ExternalRegistrationResult;
import cn.edu.rpm.application.application.dto.FormalReviewCmd;
import cn.edu.rpm.application.application.dto.FormalReviewResult;
import cn.edu.rpm.application.application.dto.RegisterExternalProjectCmd;
import cn.edu.rpm.application.application.dto.SaveDraftCmd;
import cn.edu.rpm.application.application.dto.SubmitApplicationCmd;
import cn.edu.rpm.application.application.dto.WithdrawApplicationCmd;
import cn.edu.rpm.application.application.service.ApplicationCreateAppService;
import cn.edu.rpm.application.application.service.ApplicationDecisionAppService;
import cn.edu.rpm.application.application.service.ApplicationDraftAppService;
import cn.edu.rpm.application.application.service.ApplicationQueryAppService;
import cn.edu.rpm.application.application.service.ApplicationReviewAppService;
import cn.edu.rpm.application.application.service.ApplicationSubmitAppService;
import cn.edu.rpm.application.application.service.ApplicationWithdrawAppService;
import cn.edu.rpm.application.application.service.ExternalProjectAppService;
import cn.edu.rpm.application.domain.model.ApplicationPermissions;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.common.api.Result;
import cn.edu.rpm.common.api.PageVO;
import cn.edu.rpm.common.util.UuidUtils;
import cn.edu.rpm.iam.application.annotation.RequirePermission;
import cn.edu.rpm.iam.application.context.AuthContextHolder;
import cn.edu.rpm.iam.application.dto.AuthContext;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 申报接口（T-APP-015；技术文档 04 §2.2、§3.1、§3.2）。
 *
 * <p>三个端点：</p>
 * <ul>
 *   <li>{@code POST /api/v1/applications}：创建申报草稿；</li>
 *   <li>{@code PUT /api/v1/applications/{applicationId}/draft}：保存草稿
 *       （{@code expected_version} 乐观锁）；</li>
 *   <li>{@code POST /api/v1/applications/{applicationId}/submit}：提交审批，
 *       必须携带 {@code Idempotency-Key}。</li>
 * </ul>
 *
 * <p>Controller 只做请求体形态校验与身份提取；批次开放、模板、数据范围授权、
 * 表单/附件校验、幂等与审批启动全部在应用服务内完成（禁止在 Controller 写业务规则）。
 * {@code @RequirePermission} 仅功能权限粗筛，最终授权由应用服务入口的
 * {@code AuthorizationService.check(...)} 完成；未登录由
 * {@link AuthContextHolder#require()} 抛 {@code RPM-COMMON-401}。</p>
 */
@RestController
@RequestMapping("/api/v1/applications")
public class ApplicationController {

    private final ApplicationCreateAppService createAppService;

    private final ApplicationQueryAppService queryAppService;

    private final ApplicationDraftAppService draftAppService;

    private final ApplicationSubmitAppService submitAppService;

    private final ApplicationReviewAppService reviewAppService;

    private final ApplicationWithdrawAppService withdrawAppService;

    private final ApplicationDecisionAppService decisionAppService;

    private final ExternalProjectAppService externalProjectAppService;

    /**
     * 构造控制器。
     *
     * @param createAppService           创建服务
     * @param queryAppService            查询服务
     * @param draftAppService            草稿保存服务
     * @param submitAppService           提交服务
     * @param reviewAppService           形式审查服务
     * @param withdrawAppService         撤回服务
     * @param decisionAppService         决策服务
     * @param externalProjectAppService  外部项目登记服务
     */
    public ApplicationController(ApplicationCreateAppService createAppService,
                                 ApplicationQueryAppService queryAppService,
                                 ApplicationDraftAppService draftAppService,
                                 ApplicationSubmitAppService submitAppService,
                                 ApplicationReviewAppService reviewAppService,
                                 ApplicationWithdrawAppService withdrawAppService,
                                 ApplicationDecisionAppService decisionAppService,
                                 ExternalProjectAppService externalProjectAppService) {
        this.createAppService = createAppService;
        this.queryAppService = queryAppService;
        this.draftAppService = draftAppService;
        this.submitAppService = submitAppService;
        this.reviewAppService = reviewAppService;
        this.withdrawAppService = withdrawAppService;
        this.decisionAppService = decisionAppService;
        this.externalProjectAppService = externalProjectAppService;
    }

    /**
     * 查询申报列表。
     *
     * @param keyword     编号/标题关键字
     * @param projectType 项目类型
     * @param status      申报状态
     * @param batchId     批次 ID
     * @param page        页码（从 1 开始）
     * @param size        每页条数
     * @return 申报分页
     */
    @GetMapping
    @RequirePermission(ApplicationPermissions.VIEW)
    public Result<PageVO<ApplicationListItem>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String projectType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String batchId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(queryAppService.list(
                new ApplicationListQuery(keyword, projectType, status, batchId, page, size),
                AuthContextHolder.require()));
    }

    /**
     * 新建申报选项（开放批次、有效项目类型）。
     *
     * @return 下拉选项
     */
    @GetMapping("/options")
    public Result<ApplicationOptionsView> options() {
        AuthContextHolder.require();
        return Result.ok(queryAppService.options());
    }

    /**
     * 查询申报详情。
     *
     * @param applicationId 申报 ID
     * @return 申报详情
     */
    @GetMapping("/{applicationId}")
    @RequirePermission(ApplicationPermissions.VIEW)
    public Result<ApplicationDetailView> detail(
            @PathVariable("applicationId") String applicationId) {
        return Result.ok(queryAppService.detail(parseApplicationId(applicationId),
                AuthContextHolder.require()));
    }

    /**
     * 创建申报草稿。
     *
     * @param request 创建请求体
     * @return 申报 ID、编号、状态与版本号
     */
    @PostMapping
    @RequirePermission(ApplicationPermissions.CREATE)
    public Result<ApplicationCreated> create(@Valid @RequestBody CreateApplicationRequest request) {
        AuthContext auth = AuthContextHolder.require();
        return Result.ok(createAppService.create(new CreateApplicationCmd(
                request.batchId(), request.projectType(), request.title(), request.deptId()), auth));
    }

    /**
     * 保存申报草稿。
     *
     * @param applicationId 申报 ID
     * @param request       保存请求体
     * @return 主档状态/版本与草稿版本信息
     */
    @PutMapping("/{applicationId}/draft")
    @RequirePermission(ApplicationPermissions.EDIT)
    public Result<ApplicationDraftSaved> saveDraft(
            @PathVariable("applicationId") String applicationId,
            @Valid @RequestBody UpdateDraftRequest request) {
        AuthContext auth = AuthContextHolder.require();
        return Result.ok(draftAppService.save(applicationId, new SaveDraftCmd(
                request.expectedVersion(), request.title(), request.formData()), auth));
    }

    /**
     * 提交申报并启动审批。
     *
     * @param applicationId  申报 ID
     * @param idempotencyKey 幂等键请求头（必填；缺失由应用服务返回 {@code RPM-COMMON-400}）
     * @param request        提交请求体
     * @return 版本与审批实例信息
     */
    @PostMapping("/{applicationId}/submit")
    @RequirePermission(ApplicationPermissions.SUBMIT)
    public Result<ApplicationSubmission> submit(
            @PathVariable("applicationId") String applicationId,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody SubmitApplicationRequest request) {
        AuthContext auth = AuthContextHolder.require();
        return Result.ok(submitAppService.submit(applicationId, toCmd(request), idempotencyKey, auth));
    }

    /**
     * 撤回申报。
     *
     * @param applicationId  申报 ID
     * @param idempotencyKey 幂等键请求头
     * @param request        请求体
     * @return 撤回后的状态
     */
    @PostMapping("/{applicationId}/withdraw")
    @RequirePermission(ApplicationPermissions.WITHDRAW)
    public Result<ApplicationWithdrawal> withdraw(
            @PathVariable("applicationId") String applicationId,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody ApplicationActionRequests.WithdrawRequest request) {
        AuthContext auth = AuthContextHolder.require();
        return Result.ok(withdrawAppService.withdraw(applicationId,
                new WithdrawApplicationCmd(request.reason(), parseRequestId(idempotencyKey)),
                auth));
    }

    /**
     * 形式审查。
     *
     * @param applicationId  申报 ID
     * @param idempotencyKey 幂等键请求头
     * @param request        请求体
     * @return 审查结果
     */
    @PostMapping("/{applicationId}/formal-review")
    @RequirePermission(ApplicationPermissions.FORMAL_REVIEW)
    public Result<FormalReviewResult> formalReview(
            @PathVariable("applicationId") String applicationId,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody ApplicationActionRequests.FormalReviewRequest request) {
        AuthContext auth = AuthContextHolder.require();
        List<FormalReviewCmd.Issue> issues = request.issues() == null ? List.of()
                : request.issues().stream()
                .map(item -> new FormalReviewCmd.Issue(item.field(), item.message()))
                .toList();
        return Result.ok(reviewAppService.formalReview(applicationId,
                new FormalReviewCmd(request.versionId(), request.taskId(),
                        request.expectedTaskVersion(), request.decision(), issues,
                        request.comment(), parseRequestId(idempotencyKey)), auth));
    }

    /**
     * 立项决策。
     *
     * @param applicationId  申报 ID
     * @param idempotencyKey 幂等键请求头
     * @param request        请求体
     * @return 决策结果
     */
    @PostMapping("/{applicationId}/decision")
    @RequirePermission(ApplicationPermissions.DECIDE)
    public Result<DecisionResult> decision(
            @PathVariable("applicationId") String applicationId,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody ApplicationActionRequests.DecisionRequest request) {
        AuthContext auth = AuthContextHolder.require();
        return Result.ok(decisionAppService.decide(applicationId,
                new DecisionCmd(request.versionId(), request.decision(), request.comment(),
                        request.approvedStartDate(), request.approvedEndDate(),
                        parseRequestId(idempotencyKey)), auth));
    }

    /**
     * 外部项目登记立项。
     *
     * @param idempotencyKey 幂等键请求头
     * @param request        请求体
     * @return 申报、版本与项目标识
     */
    @PostMapping("/external-registration")
    @RequirePermission(ApplicationPermissions.EXTERNAL_REGISTER)
    public Result<ExternalRegistrationResult> registerExternal(
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody ApplicationActionRequests.ExternalRegistrationRequest request) {
        AuthContext auth = AuthContextHolder.require();
        return Result.ok(externalProjectAppService.register(new RegisterExternalProjectCmd(
                request.batchId(), request.projectType(), request.title(), request.deptId(),
                request.piId(), request.funderName(), request.approvalNo(), request.contractNo(),
                request.financialProjectNo(), request.approvedAmount(), request.startDate(),
                request.endDate(), request.verifiedBy(), request.evidenceFileIds(),
                parseRequestId(idempotencyKey)), auth));
    }

    private static java.util.UUID parseRequestId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return java.util.UUID.fromString(value.trim());
        } catch (IllegalArgumentException ex) {
            throw new BizException(ErrorCode.COMMON_400, "Idempotency-Key 非法");
        }
    }

    private static String parseApplicationId(String value) {
        try {
            return UuidUtils.format(UuidUtils.parse(value));
        } catch (IllegalArgumentException ex) {
            throw new BizException(ErrorCode.COMMON_400, "申报 ID 非法");
        }
    }

    private static SubmitApplicationCmd toCmd(SubmitApplicationRequest request) {
        return new SubmitApplicationCmd(
                request.expectedVersion(),
                request.comment(),
                request.formData(),
                request.attachments().stream()
                        .map(item -> new SubmitApplicationCmd.AttachmentCmd(
                                item.purpose(), item.fileId()))
                        .toList(),
                request.collaborators() == null ? null : request.collaborators().stream()
                        .map(item -> new SubmitApplicationCmd.CollaboratorCmd(
                                item.userId(), item.role(), item.contributionDesc()))
                        .toList());
    }
}
