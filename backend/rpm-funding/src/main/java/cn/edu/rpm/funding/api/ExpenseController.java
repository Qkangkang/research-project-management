package cn.edu.rpm.funding.api;

import cn.edu.rpm.common.api.Result;
import cn.edu.rpm.funding.api.dto.FundingRequests;
import cn.edu.rpm.funding.application.dto.CreateExpenseCmd;
import cn.edu.rpm.funding.application.dto.ExpenseActionCmd;
import cn.edu.rpm.funding.application.dto.ExpenseStatusResult;
import cn.edu.rpm.funding.application.dto.ExpenseSubmitted;
import cn.edu.rpm.funding.application.dto.ExpenseView;
import cn.edu.rpm.funding.application.dto.SubmitExpenseCmd;
import cn.edu.rpm.funding.application.service.ExpenseAppService;
import cn.edu.rpm.funding.application.service.ExpenseSubmitAppService;
import cn.edu.rpm.funding.application.service.ExpenseWithdrawCancelAppService;
import cn.edu.rpm.funding.domain.model.FundingPermissions;
import cn.edu.rpm.iam.application.annotation.RequirePermission;
import cn.edu.rpm.iam.application.context.AuthContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * 支出接口（T-FUND-031；技术文档 05 §2）。
 *
 * <p>Controller 不做金额/状态判断；提交幂等键通过 {@code Idempotency-Key} 头透传。</p>
 */
@RestController
@RequestMapping("/api/v1")
public class ExpenseController {

    private final ExpenseAppService expenseAppService;

    private final ExpenseSubmitAppService expenseSubmitAppService;

    private final ExpenseWithdrawCancelAppService withdrawCancelAppService;

    /**
     * 构造控制器。
     *
     * @param expenseAppService        支出查询服务
     * @param expenseSubmitAppService  支出提交服务
     * @param withdrawCancelAppService 撤回/取消服务
     */
    public ExpenseController(ExpenseAppService expenseAppService,
                             ExpenseSubmitAppService expenseSubmitAppService,
                             ExpenseWithdrawCancelAppService withdrawCancelAppService) {
        this.expenseAppService = expenseAppService;
        this.expenseSubmitAppService = expenseSubmitAppService;
        this.withdrawCancelAppService = withdrawCancelAppService;
    }

    /**
     * 创建支出草稿。
     *
     * @param projectId 项目 ID
     * @param request   请求体
     * @return 支出视图
     */
    @PostMapping("/projects/{projectId}/expenses")
    @RequirePermission(FundingPermissions.EXPENSE_CREATE)
    public Result<ExpenseView> create(@PathVariable("projectId") String projectId,
                                      @RequestBody FundingRequests.CreateExpenseRequest request) {
        return Result.ok(expenseAppService.createDraft(projectId, new CreateExpenseCmd(
                request.budgetVersionId(), request.budgetLineId(), request.amount(),
                request.purpose(), request.payeeName(), request.payeeBankAccountMasked(),
                request.payeeBankAccountEncrypted(), request.payeeBankName(),
                request.idempotencyKey()), AuthContextHolder.require()));
    }

    /**
     * 项目支出列表。
     *
     * @param projectId 项目 ID
     * @return 支出列表
     */
    @GetMapping("/projects/{projectId}/expenses")
    @RequirePermission(FundingPermissions.VIEW)
    public Result<List<ExpenseView>> list(@PathVariable("projectId") String projectId) {
        return Result.ok(expenseAppService.list(projectId, AuthContextHolder.require()));
    }

    /**
     * 支出详情。
     *
     * @param expenseId 支出 ID
     * @return 支出视图（账号脱敏）
     */
    @GetMapping("/expenses/{expenseId}")
    @RequirePermission(FundingPermissions.VIEW)
    public Result<ExpenseView> detail(@PathVariable("expenseId") String expenseId) {
        return Result.ok(expenseAppService.detail(expenseId, AuthContextHolder.require()));
    }

    /**
     * 提交支出并占用。
     *
     * @param expenseId      支出 ID
     * @param idempotencyKey 幂等键
     * @param request        请求体
     * @return 提交结果
     */
    @PostMapping("/expenses/{expenseId}/submit")
    @RequirePermission(FundingPermissions.EXPENSE_SUBMIT)
    public Result<ExpenseSubmitted> submit(
            @PathVariable("expenseId") String expenseId,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody FundingRequests.SubmitExpenseRequest request) {
        return Result.ok(expenseSubmitAppService.submit(expenseId,
                new SubmitExpenseCmd(request.expectedVersion(), request.budgetVersionId(),
                        request.budgetLineId(), request.amount(), request.purpose(),
                        request.attachmentFileIds(), request.specialReason()),
                idempotencyKey, AuthContextHolder.require()));
    }

    /**
     * 撤回审批中的支出。
     *
     * @param expenseId      支出 ID
     * @param idempotencyKey 工作流取消幂等键
     * @param request        请求体
     * @return 状态结果
     */
    @PostMapping("/expenses/{expenseId}/withdraw")
    @RequirePermission(FundingPermissions.EXPENSE_WITHDRAW)
    public Result<ExpenseStatusResult> withdraw(
            @PathVariable("expenseId") String expenseId,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody FundingRequests.ExpenseActionRequest request) {
        return Result.ok(withdrawCancelAppService.withdraw(expenseId,
                actionCmd(request, idempotencyKey), AuthContextHolder.require()));
    }

    /**
     * 取消支出。
     *
     * @param expenseId      支出 ID
     * @param idempotencyKey 工作流取消幂等键
     * @param request        请求体
     * @return 状态结果
     */
    @PostMapping("/expenses/{expenseId}/cancel")
    @RequirePermission(FundingPermissions.EXPENSE_CANCEL)
    public Result<ExpenseStatusResult> cancel(
            @PathVariable("expenseId") String expenseId,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody FundingRequests.ExpenseActionRequest request) {
        return Result.ok(withdrawCancelAppService.cancel(expenseId,
                actionCmd(request, idempotencyKey), AuthContextHolder.require()));
    }

    private static ExpenseActionCmd actionCmd(FundingRequests.ExpenseActionRequest request,
                                              String idempotencyKey) {
        UUID requestId = null;
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            try {
                requestId = UUID.fromString(idempotencyKey);
            } catch (IllegalArgumentException ex) {
                requestId = UUID.nameUUIDFromBytes(
                        idempotencyKey.getBytes(StandardCharsets.UTF_8));
            }
        }
        return new ExpenseActionCmd(request.expectedVersion(), request.reason(), requestId);
    }
}
