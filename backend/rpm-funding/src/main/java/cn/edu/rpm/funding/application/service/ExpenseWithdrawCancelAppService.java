package cn.edu.rpm.funding.application.service;

import cn.edu.rpm.audit.domain.model.AuditEvent;
import cn.edu.rpm.audit.domain.service.AuditRecorder;
import cn.edu.rpm.common.domain.model.OutboxEvent;
import cn.edu.rpm.common.domain.port.OutboxAppendPort;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.common.util.TraceIds;
import cn.edu.rpm.common.util.UuidUtils;
import cn.edu.rpm.funding.application.dto.ExpenseActionCmd;
import cn.edu.rpm.funding.application.dto.ExpenseStatusResult;
import cn.edu.rpm.funding.domain.model.FundEventCommand;
import cn.edu.rpm.funding.domain.model.FundingPermissions;
import cn.edu.rpm.funding.domain.port.ExpenseWorkflowLauncher;
import cn.edu.rpm.funding.domain.service.FundEventAppender;
import cn.edu.rpm.funding.infrastructure.entity.ExpenseRequestEntity;
import cn.edu.rpm.funding.infrastructure.entity.FundEventEntity;
import cn.edu.rpm.funding.infrastructure.entity.PaymentRecordEntity;
import cn.edu.rpm.funding.infrastructure.repository.ExpenseRequestRepository;
import cn.edu.rpm.funding.infrastructure.repository.PaymentRecordRepository;
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

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 支出撤回与取消应用服务（T-FUND-021；技术文档 05 §1.4、07 §4.2）。
 *
 * <p>撤回仅适用于审批中支出，释放全部占用；取消按状态区分：待补正释放占用、
 * 已批未付取消未付金额、部分支付取消剩余未付并保留已付金额。所有资金动作通过
 * {@link FundEventAppender} 追加不可变事件，状态迁移使用实体显式方法。</p>
 */
@Service
public class ExpenseWithdrawCancelAppService {

    /** 审计动作：支出撤回。 */
    public static final String AUDIT_EXPENSE_WITHDRAWN = "EXPENSE_WITHDRAWN";

    /** 审计动作：支出取消。 */
    public static final String AUDIT_EXPENSE_CANCELLED = "EXPENSE_CANCELLED";

    /** 审计对象类型：支出申请。 */
    static final String OBJECT_EXPENSE = "EXPENSE_REQUEST";

    private static final String BIZ_TYPE_EXPENSE = ExpenseSubmitAppService.BIZ_TYPE_EXPENSE;

    private final ExpenseRequestRepository expenseRequestRepository;

    private final PaymentRecordRepository paymentRecordRepository;

    private final ProjectRepository projectRepository;

    private final ProjectMemberRepository projectMemberRepository;

    private final AuthorizationService authorizationService;

    private final FundEventAppender fundEventAppender;

    private final ExpenseWorkflowLauncher workflowLauncher;

    private final AuditRecorder auditRecorder;

    private final OutboxAppendPort outboxAppendPort;

    private final Clock clock;

    /**
     * 构造撤回/取消服务。
     *
     * @param expenseRequestRepository 支出仓储
     * @param paymentRecordRepository  支付记录仓储（部分支付已付金额）
     * @param projectRepository        项目仓储
     * @param projectMemberRepository  项目成员仓储（授权装载）
     * @param authorizationService     统一授权服务
     * @param fundEventAppender        资金事件追加器
     * @param workflowLauncher         审批启动端口（取消实例）
     * @param auditRecorder            审计记录器
     * @param outboxAppendPort         Outbox 追加端口
     * @param clock                    时间基准
     */
    public ExpenseWithdrawCancelAppService(ExpenseRequestRepository expenseRequestRepository,
                                           PaymentRecordRepository paymentRecordRepository,
                                           ProjectRepository projectRepository,
                                           ProjectMemberRepository projectMemberRepository,
                                           AuthorizationService authorizationService,
                                           FundEventAppender fundEventAppender,
                                           ExpenseWorkflowLauncher workflowLauncher,
                                           AuditRecorder auditRecorder,
                                           OutboxAppendPort outboxAppendPort,
                                           Clock clock) {
        this.expenseRequestRepository = Objects.requireNonNull(expenseRequestRepository,
                "expenseRequestRepository 不能为空");
        this.paymentRecordRepository = Objects.requireNonNull(paymentRecordRepository,
                "paymentRecordRepository 不能为空");
        this.projectRepository = Objects.requireNonNull(projectRepository, "projectRepository 不能为空");
        this.projectMemberRepository = Objects.requireNonNull(projectMemberRepository,
                "projectMemberRepository 不能为空");
        this.authorizationService = Objects.requireNonNull(authorizationService,
                "authorizationService 不能为空");
        this.fundEventAppender = Objects.requireNonNull(fundEventAppender, "fundEventAppender 不能为空");
        this.workflowLauncher = Objects.requireNonNull(workflowLauncher, "workflowLauncher 不能为空");
        this.auditRecorder = Objects.requireNonNull(auditRecorder, "auditRecorder 不能为空");
        this.outboxAppendPort = Objects.requireNonNull(outboxAppendPort, "outboxAppendPort 不能为空");
        this.clock = Objects.requireNonNull(clock, "clock 不能为空");
    }

    /**
     * 撤回审批中的支出（TC-FUND-030）。
     *
     * @param expenseId 支出 ID
     * @param cmd       撤回命令
     * @param auth      当前请求授权上下文
     * @return 撤回后的状态与释放金额
     * @throws BizException 支出不存在 {@code RPM-COMMON-404}；无权限 {@code RPM-COMMON-403}；
     *                      版本冲突 {@code RPM-COMMON-409}；状态不允许 {@code RPM-FUND-005}
     */
    @Transactional
    public ExpenseStatusResult withdraw(String expenseId, ExpenseActionCmd cmd, AuthContext auth) {
        requireAction(cmd);
        ExpenseRequestEntity expense = lockExpense(expenseId);
        ProjectEntity project = loadProject(expense.getProjectId());
        authorize(project, auth, FundingPermissions.EXPENSE_WITHDRAW);
        requireVersion(expense, cmd.expectedVersion());
        if (!ExpenseRequestEntity.STATUS_IN_REVIEW.equals(expense.getStatus())) {
            throw new BizException(ErrorCode.FUND_005, "仅审批中的支出可以撤回");
        }
        BigDecimal amount = expense.getAmount();
        appendRelease(expense, amount, "EXPENSE_WITHDRAW");
        workflowLauncher.cancel(BIZ_TYPE_EXPENSE, UuidUtils.parse(expense.getId()),
                UuidUtils.parse(expense.getCurrentVersionId()), auth.userId(),
                cmd.reason(), cmd.requestId());
        expense.cancel();
        persist(expense);
        publish(AUDIT_EXPENSE_WITHDRAWN, expense, auth.userId(), cmd.reason());
        return new ExpenseStatusResult(expense.getId(), expense.getStatus(), amount);
    }

    /**
     * 取消支出（TC-FUND-031、TC-FUND-032、TC-FUND-068）。
     *
     * @param expenseId 支出 ID
     * @param cmd       取消命令
     * @param auth      当前请求授权上下文
     * @return 取消后的状态与释放/取消金额
     * @throws BizException 支出不存在 {@code RPM-COMMON-404}；无权限 {@code RPM-COMMON-403}；
     *                      版本冲突 {@code RPM-COMMON-409}；状态不允许 {@code RPM-FUND-005}
     */
    @Transactional
    public ExpenseStatusResult cancel(String expenseId, ExpenseActionCmd cmd, AuthContext auth) {
        requireAction(cmd);
        ExpenseRequestEntity expense = lockExpense(expenseId);
        ProjectEntity project = loadProject(expense.getProjectId());
        authorize(project, auth, FundingPermissions.EXPENSE_CANCEL);
        requireVersion(expense, cmd.expectedVersion());

        BigDecimal released = switch (expense.getStatus()) {
            case ExpenseRequestEntity.STATUS_DRAFT -> null;
            case ExpenseRequestEntity.STATUS_IN_REVIEW,
                 ExpenseRequestEntity.STATUS_SUPPLEMENT -> cancelReserved(expense);
            case ExpenseRequestEntity.STATUS_APPROVED_UNPAID -> cancelUnpaid(expense);
            case ExpenseRequestEntity.STATUS_PARTIALLY_PAID -> cancelRemaining(expense);
            default -> throw new BizException(ErrorCode.FUND_005, "支出当前状态不允许取消");
        };
        if (ExpenseRequestEntity.STATUS_IN_REVIEW.equals(expense.getStatus())
                || ExpenseRequestEntity.STATUS_SUPPLEMENT.equals(expense.getStatus())) {
            cancelWorkflow(expense, auth.userId(), cmd.reason(), cmd.requestId());
        }
        expense.cancel();
        persist(expense);
        publish(AUDIT_EXPENSE_CANCELLED, expense, auth.userId(), cmd.reason());
        return new ExpenseStatusResult(expense.getId(), expense.getStatus(), released);
    }

    private BigDecimal cancelReserved(ExpenseRequestEntity expense) {
        BigDecimal amount = expense.getAmount();
        appendRelease(expense, amount, "EXPENSE_CANCEL");
        return amount;
    }

    private BigDecimal cancelUnpaid(ExpenseRequestEntity expense) {
        BigDecimal amount = expense.getAmount();
        appendCancel(expense, amount, "EXPENSE_CANCEL");
        return amount;
    }

    private BigDecimal cancelRemaining(ExpenseRequestEntity expense) {
        BigDecimal paid = paymentRecordRepository.findByExpenseRequestId(expense.getId()).stream()
                .filter(record -> PaymentRecordEntity.STATUS_APPLIED.equals(record.getStatus()))
                .map(PaymentRecordEntity::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal remaining = expense.getAmount().subtract(paid);
        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        appendCancel(expense, remaining, "EXPENSE_CANCEL_REMAINING");
        return remaining;
    }

    private void appendRelease(ExpenseRequestEntity expense, BigDecimal amount, String source) {
        fundEventAppender.append(new FundEventCommand(
                FundEventEntity.TYPE_RELEASE,
                expense.getProjectId(),
                expense.getId(),
                expense.getBudgetVersionId(),
                expense.getBudgetLineId(),
                amount,
                expense.getRequestNo(),
                "expense:" + expense.getId() + ":RELEASE",
                null,
                payload(source, expense.getId()),
                null));
    }

    private void appendCancel(ExpenseRequestEntity expense, BigDecimal amount, String source) {
        fundEventAppender.append(new FundEventCommand(
                FundEventEntity.TYPE_CANCEL,
                expense.getProjectId(),
                expense.getId(),
                expense.getBudgetVersionId(),
                expense.getBudgetLineId(),
                amount,
                expense.getRequestNo(),
                "expense:" + expense.getId() + ":CANCEL",
                null,
                payload(source, expense.getId()),
                null));
    }

    private void cancelWorkflow(ExpenseRequestEntity expense, UUID operatorId, String reason,
                                UUID requestId) {
        if (expense.getCurrentVersionId() == null || expense.getCurrentVersionId().isBlank()) {
            return;
        }
        workflowLauncher.cancel(BIZ_TYPE_EXPENSE, UuidUtils.parse(expense.getId()),
                UuidUtils.parse(expense.getCurrentVersionId()), operatorId, reason, requestId);
    }

    private ExpenseRequestEntity lockExpense(String expenseId) {
        return expenseRequestRepository.selectForUpdate(expenseId)
                .orElseThrow(() -> new BizException(ErrorCode.COMMON_404, "支出不存在"));
    }

    private ProjectEntity loadProject(String projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new BizException(ErrorCode.PROJ_001));
    }

    private void authorize(ProjectEntity project, AuthContext auth, String permission) {
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
    }

    private Set<UUID> activeMemberIds(String projectId) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        return projectMemberRepository.findActiveByProjectId(projectId, now).stream()
                .map(ProjectMemberEntity::getUserId)
                .map(UuidUtils::parse)
                .collect(Collectors.toSet());
    }

    private void requireAction(ExpenseActionCmd cmd) {
        if (cmd == null || cmd.expectedVersion() == null || cmd.requestId() == null
                || cmd.reason() == null || cmd.reason().isBlank()) {
            throw new BizException(ErrorCode.COMMON_400, "版本、原因与请求幂等键不能为空");
        }
    }

    private static void requireVersion(ExpenseRequestEntity expense, Integer expectedVersion) {
        if (!Objects.equals(expense.getVersion(), expectedVersion)) {
            throw new BizException(ErrorCode.COMMON_409, "支出已被并发修改，请刷新后重试");
        }
    }

    private void persist(ExpenseRequestEntity expense) {
        if (expenseRequestRepository.update(expense) == 0) {
            throw new BizException(ErrorCode.COMMON_409, "支出已被并发修改，请重试");
        }
    }

    private void publish(String action, ExpenseRequestEntity expense, UUID actorId, String reason) {
        auditRecorder.record(new AuditEvent(
                TraceIds.currentOrUntracked(), actorId, null, action, OBJECT_EXPENSE,
                UuidUtils.parse(expense.getId()), expense.getVersion(), null, null,
                reason, null, null, clock.instant()));
        outboxAppendPort.append(new OutboxEvent(UUID.randomUUID(), OBJECT_EXPENSE,
                UuidUtils.parse(expense.getId()), action,
                Map.of("expenseId", expense.getId(), "projectId", expense.getProjectId()),
                TraceIds.currentOrUntracked(), action + ":" + expense.getId()));
    }

    private static String payload(String source, String expenseId) {
        return "{\"source\":\"" + source + "\",\"expenseId\":\"" + expenseId + "\"}";
    }
}
