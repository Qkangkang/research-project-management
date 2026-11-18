package cn.edu.rpm.output.api;

import cn.edu.rpm.common.api.PageVO;
import cn.edu.rpm.common.api.Result;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.iam.application.annotation.RequirePermission;
import cn.edu.rpm.iam.application.context.AuthContextHolder;
import cn.edu.rpm.iam.application.dto.AuthContext;
import cn.edu.rpm.output.api.dto.CreateOutputRequest;
import cn.edu.rpm.output.api.dto.RelateOutputRequest;
import cn.edu.rpm.output.api.dto.ReviewOutputRequest;
import cn.edu.rpm.output.api.dto.SaveOutputDraftRequest;
import cn.edu.rpm.output.api.dto.WithdrawOutputRequest;
import cn.edu.rpm.output.api.dto.WithdrawReviewRequest;
import cn.edu.rpm.output.application.dto.CreateOutputCmd;
import cn.edu.rpm.output.application.dto.DuplicateCandidateVO;
import cn.edu.rpm.output.application.dto.LibrarySearchQuery;
import cn.edu.rpm.output.application.dto.OutputCreated;
import cn.edu.rpm.output.application.dto.OutputDetailVO;
import cn.edu.rpm.output.application.dto.OutputDraftSaved;
import cn.edu.rpm.output.application.dto.OutputLibraryDetailVO;
import cn.edu.rpm.output.application.dto.OutputLibraryItemVO;
import cn.edu.rpm.output.application.dto.OutputRelationVO;
import cn.edu.rpm.output.application.dto.OutputReviewResult;
import cn.edu.rpm.output.application.dto.OutputSearchQuery;
import cn.edu.rpm.output.application.dto.OutputStatItem;
import cn.edu.rpm.output.application.dto.OutputStatsQuery;
import cn.edu.rpm.output.application.dto.OutputSubmission;
import cn.edu.rpm.output.application.dto.OutputSummaryVO;
import cn.edu.rpm.output.application.dto.OutputWithdrawResult;
import cn.edu.rpm.output.application.dto.RelateOutputCmd;
import cn.edu.rpm.output.application.dto.ReviewOutputCmd;
import cn.edu.rpm.output.application.dto.SaveOutputDraftCmd;
import cn.edu.rpm.output.application.dto.WithdrawOutputCmd;
import cn.edu.rpm.output.application.dto.WithdrawReviewCmd;
import cn.edu.rpm.output.application.service.OutputAppService;
import cn.edu.rpm.output.application.service.OutputQueryService;
import cn.edu.rpm.output.application.service.OutputRelationAppService;
import cn.edu.rpm.output.application.service.OutputReviewService;
import cn.edu.rpm.output.application.service.OutputSubmitAppService;
import cn.edu.rpm.output.application.service.OutputWithdrawAppService;
import cn.edu.rpm.output.domain.model.OutputPermissions;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 成果接口（T-OUT-016；技术文档 05 §4.1、09 §5.1）。
 *
 * <p>端点：</p>
 * <ul>
 *   <li>{@code POST /api/v1/outputs}：登记成果；</li>
 *   <li>{@code GET /api/v1/outputs/{outputId}}：成果详情；</li>
 *   <li>{@code PUT /api/v1/outputs/{outputId}/draft}：保存草稿（已审核成果自动进入修订）；</li>
 *   <li>{@code POST /api/v1/outputs/{outputId}/revisions}：显式修订生成新版本；</li>
 *   <li>{@code POST /api/v1/outputs/{outputId}/submit}：提交审核；</li>
 *   <li>{@code POST /api/v1/outputs/{outputId}/relations}、{@code DELETE .../{relationId}}：关联/撤销；</li>
 *   <li>{@code POST /api/v1/outputs/{outputId}/review}：审核；</li>
 *   <li>{@code GET /api/v1/outputs/duplicates?outputId=}：疑似重复提示；</li>
 *   <li>{@code POST /api/v1/outputs/{outputId}/withdraw}、{@code .../withdraw/review}：撤回/撤回审核；</li>
 *   <li>{@code GET /api/v1/outputs}、{@code /outputs/library/search}、
 *       {@code /outputs/library/stats}、{@code /outputs/{id}/library-detail}：检索与统计；</li>
 *   <li>{@code GET /api/v1/projects/{projectId}/outputs}：项目成果列表。</li>
 * </ul>
 *
 * <p>Controller 只做请求形态校验与身份提取；类型模板、状态机、数据范围、查重、
 * 幂等与审计全部在应用服务内完成。{@code @RequirePermission} 仅功能权限粗筛，
 * 最终授权由应用服务入口的 {@code AuthorizationService.check(...)} 判定；
 * 未登录由 {@link AuthContextHolder#require()} 抛 {@code RPM-COMMON-401}。</p>
 */
@RestController
@RequestMapping("/api/v1")
public class OutputController {

    private final OutputAppService outputAppService;

    private final OutputSubmitAppService submitAppService;

    private final OutputReviewService reviewService;

    private final OutputRelationAppService relationAppService;

    private final OutputWithdrawAppService withdrawAppService;

    private final OutputQueryService queryService;

    /**
     * 构造控制器。
     *
     * @param outputAppService   登记/修订服务
     * @param submitAppService   提交服务
     * @param reviewService      审核服务
     * @param relationAppService 关联服务
     * @param withdrawAppService 撤回服务
     * @param queryService       查询服务
     */
    public OutputController(OutputAppService outputAppService,
                            OutputSubmitAppService submitAppService,
                            OutputReviewService reviewService,
                            OutputRelationAppService relationAppService,
                            OutputWithdrawAppService withdrawAppService,
                            OutputQueryService queryService) {
        this.outputAppService = outputAppService;
        this.submitAppService = submitAppService;
        this.reviewService = reviewService;
        this.relationAppService = relationAppService;
        this.withdrawAppService = withdrawAppService;
        this.queryService = queryService;
    }

    /**
     * 登记成果草稿。
     *
     * @param request 登记请求
     * @return 成果 ID、状态、初始版本与疑似重复提示
     */
    @PostMapping("/outputs")
    @RequirePermission(OutputPermissions.CREATE)
    public Result<OutputCreated> create(@Valid @RequestBody CreateOutputRequest request) {
        AuthContext auth = AuthContextHolder.require();
        return Result.ok(outputAppService.createDraft(new CreateOutputCmd(
                request.outputType(), request.title(), request.doi(), request.patentNo(),
                request.completedAt(), request.ownerId(), request.deptId(), request.metadata(),
                request.evidenceFileIds()), auth));
    }

    /**
     * 成果详情。
     *
     * @param outputId 成果 ID
     * @return 详情视图
     */
    @GetMapping("/outputs/{outputId}")
    @RequirePermission(OutputPermissions.VIEW)
    public Result<OutputDetailVO> detail(@PathVariable("outputId") String outputId) {
        AuthContext auth = AuthContextHolder.require();
        return Result.ok(queryService.detail(outputId, auth));
    }

    /**
     * 保存成果草稿（非草稿状态自动进入修订）。
     *
     * @param outputId 成果 ID
     * @param request  保存请求
     * @return 保存后的状态与版本
     */
    @PutMapping("/outputs/{outputId}/draft")
    @RequirePermission(OutputPermissions.EDIT)
    public Result<OutputDraftSaved> saveDraft(@PathVariable("outputId") String outputId,
                                              @Valid @RequestBody SaveOutputDraftRequest request) {
        AuthContext auth = AuthContextHolder.require();
        return Result.ok(outputAppService.saveDraft(outputId, toSaveCmd(request), auth));
    }

    /**
     * 修订成果（生成新版本，旧版本与旧审核意见保留）。
     *
     * @param outputId 成果 ID
     * @param request  修订请求
     * @return 修订后的草稿版本
     */
    @PostMapping("/outputs/{outputId}/revisions")
    @RequirePermission(OutputPermissions.EDIT)
    public Result<OutputDraftSaved> revise(@PathVariable("outputId") String outputId,
                                           @Valid @RequestBody SaveOutputDraftRequest request) {
        AuthContext auth = AuthContextHolder.require();
        return Result.ok(outputAppService.revise(outputId, toSaveCmd(request), auth));
    }

    /**
     * 提交成果审核。
     *
     * @param outputId 成果 ID
     * @return 提交后的状态与固定版本
     */
    @PostMapping("/outputs/{outputId}/submit")
    @RequirePermission(OutputPermissions.SUBMIT)
    public Result<OutputSubmission> submit(@PathVariable("outputId") String outputId) {
        AuthContext auth = AuthContextHolder.require();
        return Result.ok(submitAppService.submit(outputId, auth));
    }

    /**
     * 关联成果到项目。
     *
     * @param outputId 成果 ID
     * @param request  关联请求
     * @return 关联结果（含 relation_id）
     */
    @PostMapping("/outputs/{outputId}/relations")
    @RequirePermission(OutputPermissions.RELATE)
    public Result<OutputRelationVO> relate(@PathVariable("outputId") String outputId,
                                           @Valid @RequestBody RelateOutputRequest request) {
        AuthContext auth = AuthContextHolder.require();
        return Result.ok(relationAppService.relate(outputId, new RelateOutputCmd(
                request.projectId(), request.contributionDesc(),
                request.attributionRatio()), auth));
    }

    /**
     * 撤销成果-项目关联（软删除）。
     *
     * @param outputId   成果 ID
     * @param relationId 关联 ID
     * @return 撤销后的关联
     */
    @DeleteMapping("/outputs/{outputId}/relations/{relationId}")
    @RequirePermission(OutputPermissions.RELATE)
    public Result<OutputRelationVO> revokeRelation(
            @PathVariable("outputId") String outputId,
            @PathVariable("relationId") String relationId) {
        AuthContext auth = AuthContextHolder.require();
        return Result.ok(relationAppService.revoke(outputId, relationId, auth));
    }

    /**
     * 审核成果。
     *
     * @param outputId 成果 ID
     * @param request  审核请求
     * @return 审核结果
     */
    @PostMapping("/outputs/{outputId}/review")
    @RequirePermission(OutputPermissions.REVIEW)
    public Result<OutputReviewResult> review(@PathVariable("outputId") String outputId,
                                             @Valid @RequestBody ReviewOutputRequest request) {
        AuthContext auth = AuthContextHolder.require();
        return Result.ok(reviewService.review(outputId, new ReviewOutputCmd(
                request.versionId(), request.decision(), request.comment()), auth));
    }

    /**
     * 查询成果的待确认疑似重复候选。
     *
     * @param outputId 成果 ID（必填）
     * @return 候选分页（无候选为空列表）
     */
    @GetMapping("/outputs/duplicates")
    @RequirePermission(OutputPermissions.VIEW)
    public Result<PageVO<DuplicateCandidateVO>> duplicates(
            @RequestParam(name = "outputId", required = false) String outputId) {
        if (outputId == null || outputId.isBlank()) {
            throw new BizException(ErrorCode.COMMON_400, "outputId 不能为空");
        }
        AuthContext auth = AuthContextHolder.require();
        return Result.ok(queryService.duplicates(outputId, auth));
    }

    /**
     * 发起成果撤回申请。
     *
     * @param outputId 成果 ID
     * @param request  撤回申请
     * @return 当前状态与是否需要审核
     */
    @PostMapping("/outputs/{outputId}/withdraw")
    @RequirePermission(OutputPermissions.EDIT)
    public Result<OutputWithdrawResult> withdraw(@PathVariable("outputId") String outputId,
                                                 @Valid @RequestBody WithdrawOutputRequest request) {
        AuthContext auth = AuthContextHolder.require();
        return Result.ok(withdrawAppService.withdraw(
                outputId, new WithdrawOutputCmd(request.reason()), auth));
    }

    /**
     * 科研管理审核撤回申请。
     *
     * @param outputId 成果 ID
     * @param request  撤回审核请求
     * @return 审核后的状态
     */
    @PostMapping("/outputs/{outputId}/withdraw/review")
    @RequirePermission(OutputPermissions.REVIEW)
    public Result<OutputWithdrawResult> reviewWithdraw(
            @PathVariable("outputId") String outputId,
            @Valid @RequestBody WithdrawReviewRequest request) {
        AuthContext auth = AuthContextHolder.require();
        return Result.ok(withdrawAppService.reviewWithdraw(
                outputId, new WithdrawReviewCmd(request.approved(), request.comment()), auth));
    }

    /**
     * 「我的成果 + 已公开成果」检索。
     *
     * @param keyword    标题/DOI/专利号关键字
     * @param outputType 成果类型
     * @param status     成果状态
     * @param deptId     部门 ID
     * @param year       完成年度
     * @param page       页码（从 1 开始）
     * @param size       每页条数（≤100）
     * @return 分页摘要
     */
    @GetMapping("/outputs")
    @RequirePermission(OutputPermissions.VIEW)
    public Result<PageVO<OutputSummaryVO>> search(
            @RequestParam(name = "q", required = false) String keyword,
            @RequestParam(name = "outputType", required = false) String outputType,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "deptId", required = false) String deptId,
            @RequestParam(name = "year", required = false) Integer year,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        AuthContext auth = AuthContextHolder.require();
        return Result.ok(queryService.search(new OutputSearchQuery(
                keyword, outputType, status, deptId, year, page, size), auth));
    }

    /**
     * 全院成果库检索。
     *
     * @param keyword    标题/DOI/专利号关键字
     * @param outputType 成果类型
     * @param deptId     部门 ID
     * @param year       完成年度
     * @param page       页码
     * @param size       每页条数（≤100）
     * @return 分页元数据
     */
    @GetMapping("/outputs/library/search")
    @RequirePermission(OutputPermissions.LIBRARY_VIEW)
    public Result<PageVO<OutputLibraryItemVO>> librarySearch(
            @RequestParam(name = "q", required = false) String keyword,
            @RequestParam(name = "outputType", required = false) String outputType,
            @RequestParam(name = "deptId", required = false) String deptId,
            @RequestParam(name = "year", required = false) Integer year,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        AuthContext auth = AuthContextHolder.require();
        return Result.ok(queryService.librarySearch(new LibrarySearchQuery(
                keyword, outputType, deptId, year, page, size), auth));
    }

    /**
     * 全院成果库详情（仅元数据）。
     *
     * @param outputId 成果 ID
     * @return 元数据详情
     */
    @GetMapping("/outputs/{outputId}/library-detail")
    @RequirePermission(OutputPermissions.LIBRARY_VIEW)
    public Result<OutputLibraryDetailVO> libraryDetail(@PathVariable("outputId") String outputId) {
        AuthContext auth = AuthContextHolder.require();
        return Result.ok(queryService.libraryDetail(outputId, auth));
    }

    /**
     * 全院成果统计（按类型/年度/部门分组）。
     *
     * @param groupBy 分组维度：TYPE / YEAR / DEPT
     * @param deptId  部门过滤
     * @param year    完成年度过滤
     * @return 分组统计
     */
    @GetMapping("/outputs/library/stats")
    @RequirePermission(OutputPermissions.LIBRARY_VIEW)
    public Result<List<OutputStatItem>> stats(
            @RequestParam(name = "groupBy") String groupBy,
            @RequestParam(name = "deptId", required = false) String deptId,
            @RequestParam(name = "year", required = false) Integer year) {
        AuthContext auth = AuthContextHolder.require();
        return Result.ok(queryService.stats(new OutputStatsQuery(groupBy, deptId, year), auth));
    }

    /**
     * 项目成果列表。
     *
     * @param projectId 项目 ID
     * @return 成果摘要列表
     */
    @GetMapping("/projects/{projectId}/outputs")
    @RequirePermission(OutputPermissions.PROJECT_VIEW)
    public Result<List<OutputSummaryVO>> projectOutputs(
            @PathVariable("projectId") String projectId) {
        AuthContext auth = AuthContextHolder.require();
        return Result.ok(queryService.projectOutputs(projectId, auth));
    }

    private static SaveOutputDraftCmd toSaveCmd(SaveOutputDraftRequest request) {
        return new SaveOutputDraftCmd(request.title(), request.doi(), request.patentNo(),
                request.completedAt(), request.metadata(), request.evidenceFileIds());
    }
}
