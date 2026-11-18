package cn.edu.rpm.funding.api;

import cn.edu.rpm.common.api.Result;
import cn.edu.rpm.funding.api.dto.FundingRequests;
import cn.edu.rpm.funding.application.dto.BudgetLineCmd;
import cn.edu.rpm.funding.application.dto.BudgetOverview;
import cn.edu.rpm.funding.application.dto.BudgetVersionDetail;
import cn.edu.rpm.funding.application.dto.CreateBudgetDraftCmd;
import cn.edu.rpm.funding.application.dto.FundSummary;
import cn.edu.rpm.funding.application.service.BudgetAppService;
import cn.edu.rpm.funding.application.service.FundQueryAppService;
import cn.edu.rpm.funding.domain.model.FundingPermissions;
import cn.edu.rpm.iam.application.annotation.RequirePermission;
import cn.edu.rpm.iam.application.context.AuthContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 预算与经费汇总接口（T-FUND-031；技术文档 05 §2）。
 *
 * <p>只做请求映射与身份提取；授权、版本与状态规则全部在应用服务。</p>
 */
@RestController
@RequestMapping("/api/v1/projects")
public class BudgetController {

    private final BudgetAppService budgetAppService;

    private final FundQueryAppService fundQueryAppService;

    /**
     * 构造控制器。
     *
     * @param budgetAppService  预算服务
     * @param fundQueryAppService 经费汇总服务
     */
    public BudgetController(BudgetAppService budgetAppService,
                            FundQueryAppService fundQueryAppService) {
        this.budgetAppService = budgetAppService;
        this.fundQueryAppService = fundQueryAppService;
    }

    /**
     * 预算版本与科目。
     *
     * @param projectId 项目 ID
     * @return 预算总览
     */
    @GetMapping("/{projectId}/budgets")
    @RequirePermission(FundingPermissions.VIEW)
    public Result<BudgetOverview> list(@PathVariable("projectId") String projectId) {
        return Result.ok(budgetAppService.list(projectId, AuthContextHolder.require()));
    }

    /**
     * 创建预算草稿。
     *
     * @param projectId 项目 ID
     * @param request   请求体
     * @return 草稿详情
     */
    @PostMapping("/{projectId}/budgets")
    @RequirePermission(FundingPermissions.BUDGET_EDIT)
    public Result<BudgetVersionDetail> create(@PathVariable("projectId") String projectId,
                                              @RequestBody FundingRequests.CreateBudgetRequest request) {
        List<BudgetLineCmd> lines = request.lines() == null ? List.of()
                : request.lines().stream()
                .map(line -> new BudgetLineCmd(line.subjectCode(), line.subjectName(),
                        line.amount()))
                .toList();
        return Result.ok(budgetAppService.createDraft(projectId,
                new CreateBudgetDraftCmd(request.sourceType(), request.controlLevel(),
                        request.effectiveFrom(), request.effectiveTo(), lines,
                        request.totalAmount()), AuthContextHolder.require()));
    }

    /**
     * 提交预算生效。
     *
     * @param projectId 项目 ID（路径约束）
     * @param budgetId  预算版本 ID
     * @return 生效后的预算
     */
    @PostMapping("/{projectId}/budgets/{budgetId}/submit")
    @RequirePermission(FundingPermissions.BUDGET_SUBMIT)
    public Result<BudgetVersionDetail> submit(@PathVariable("projectId") String projectId,
                                              @PathVariable("budgetId") String budgetId) {
        return Result.ok(budgetAppService.activate(budgetId, AuthContextHolder.require()));
    }

    /**
     * 经费汇总。
     *
     * @param projectId 项目 ID
     * @return 经费汇总
     */
    @GetMapping("/{projectId}/funds/summary")
    @RequirePermission(FundingPermissions.VIEW)
    public Result<FundSummary> summary(@PathVariable("projectId") String projectId) {
        return Result.ok(fundQueryAppService.summary(projectId, AuthContextHolder.require()));
    }
}
