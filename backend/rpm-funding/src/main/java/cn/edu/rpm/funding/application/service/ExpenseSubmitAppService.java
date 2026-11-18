package cn.edu.rpm.funding.application.service;

import cn.edu.rpm.audit.domain.model.AuditEvent;
import cn.edu.rpm.audit.domain.service.AuditRecorder;
import cn.edu.rpm.common.domain.model.OutboxEvent;
import cn.edu.rpm.common.domain.port.OutboxAppendPort;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.common.util.CanonicalJsonHasher;
import cn.edu.rpm.common.util.TraceIds;
import cn.edu.rpm.common.util.UuidUtils;
import cn.edu.rpm.funding.application.dto.ExpenseSubmitted;
import cn.edu.rpm.funding.application.dto.SubmitExpenseCmd;
import cn.edu.rpm.funding.domain.model.BudgetDecision;
import cn.edu.rpm.funding.domain.model.ExpenseContext;
import cn.edu.rpm.funding.domain.model.FundEventCommand;
import cn.edu.rpm.funding.domain.model.FundingPermissions;
import cn.edu.rpm.funding.domain.port.ExpenseWorkflowLauncher;
import cn.edu.rpm.funding.domain.service.BudgetControlPolicyRegistry;
import cn.edu.rpm.funding.domain.service.FundEventAppender;
import cn.edu.rpm.funding.infrastructure.entity.BudgetBalanceEntity;
import cn.edu.rpm.funding.infrastructure.entity.BudgetLineEntity;
import cn.edu.rpm.funding.infrastructure.entity.BudgetVersionEntity;
import cn.edu.rpm.funding.infrastructure.entity.ExpenseRequestEntity;
import cn.edu.rpm.funding.infrastructure.entity.ExpenseRequestVersionEntity;
import cn.edu.rpm.funding.infrastructure.entity.FundEventEntity;
import cn.edu.rpm.funding.infrastructure.repository.BudgetBalanceRepository;
import cn.edu.rpm.funding.infrastructure.repository.BudgetLineRepository;
import cn.edu.rpm.funding.infrastructure.repository.BudgetVersionRepository;
import cn.edu.rpm.funding.infrastructure.repository.ExpenseRequestRepository;
import cn.edu.rpm.funding.infrastructure.repository.ExpenseRequestVersionRepository;
import cn.edu.rpm.funding.infrastructure.repository.FundEventRepository;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 支出提交应用服务（T-FUND-020；技术文档 05 §3.2、07 §4.2）。
 *
 * <p>提交在单一事务内完成：锁定支出行、校验项目/状态/版本/预算/材料、锁定余额行、
 * 按控制级别策略判定、追加唯一 {@code RESERVE} 事件、写不可变提交版本、启动审批。
 * 同一支出重复提交（退回补正后）复用固定 {@code RESERVE} 幂等键，不会重复占用；
 * 同一提交幂等键重复请求返回首次结果。</p>
 */
@Service
public class ExpenseSubmitAppService {

    /** 工作流业务类型：支出审批。 */
    public static final String BIZ_TYPE_EXPENSE = "EXPENSE";

    /** 审计动作：支出提交。 */
    public static final String AUDIT_EXPENSE_SUBMITTED = "EXPENSE_SUBMITTED";

    /** 审计对象类型：支出申请。 */
    static final String OBJECT_EXPENSE = "EXPENSE_REQUEST";

    private final ExpenseRequestRepository expenseRequestRepository;

    private final ExpenseRequestVersionRepository versionRepository;

    private final FundEventRepository fundEventRepository;

    private final BudgetVersionRepository budgetVersionRepository;

    private final BudgetLineRepository budgetLineRepository;

    private final BudgetBalanceRepository budgetBalanceRepository;

    private final ProjectRepository projectRepository;

    private final ProjectMemberRepository projectMemberRepository;

    private final AuthorizationService authorizationService;

    private final BudgetControlPolicyRegistry policyRegistry;

    private final FundEventAppender fundEventAppender;

    private final ExpenseWorkflowLauncher workflowLauncher;

    private final AuditRecorder auditRecorder;

    private final OutboxAppendPort outboxAppendPort;

    private final CanonicalJsonHasher jsonHasher;

    private final Clock clock;

    /**
     * 构造支出提交服务。
     *
     * @param expenseRequestRepository 支出仓储
     * @param versionRepository        支出不可变版本仓储
     * @param fundEventRepository      资金事件仓储（幂等重放）
     * @param budgetVersionRepository  预算版本仓储
     * @param budgetLineRepository     预算科目仓储
     * @param budgetBalanceRepository  余额投影仓储
     * @param projectRepository        项目仓储
     * @param projectMemberRepository  项目成员仓储（授权装载）
     * @param authorizationService     统一授权服务
     * @param policyRegistry           预算控制策略注册器
     * @param fundEventAppender        资金事件追加器
     * @param workflowLauncher         审批启动端口
     * @param auditRecorder            审计记录器
     * @param outboxAppendPort         Outbox 追加端口
     * @param jsonHasher               规范化 JSON 工具
     * @param clock                    时间基准
     */
    public ExpenseSubmitAppService(ExpenseRequestRepository expenseRequestRepository,
                                   ExpenseRequestVersionRepository versionRepository,
                                   FundEventRepository fundEventRepository,
                                   BudgetVersionRepository budgetVersionRepository,
                                   BudgetLineRepository budgetLineRepository,
                                   BudgetBalanceRepository budgetBalanceRepository,
                                   ProjectRepository projectRepository,
                                   ProjectMemberRepository projectMemberRepository,
                                   AuthorizationService authorizationService,
                                   BudgetControlPolicyRegistry policyRegistry,
                                   FundEventAppender fundEventAppender,
                                   ExpenseWorkflowLauncher workflowLauncher,
                                   AuditRecorder auditRecorder,
                                   OutboxAppendPort outboxAppendPort,
                                   CanonicalJsonHasher jsonHasher,
                                   Clock clock) {
        this.expenseRequestRepository = Objects.requireNonNull(expenseRequestRepository,
                "expenseRequestRepository 不能为空");
        this.versionRepository = Objects.requireNonNull(versionRepository, "versionRepository 不能为空");
        this.fundEventRepository = Objects.requireNonNull(fundEventRepository,
                "fundEventRepository 不能为空");
        this.budgetVersionRepository = Objects.requireNonNull(budgetVersionRepository,
                "budgetVersionRepository 不能为空");
        this.budgetLineRepository = Objects.requireNonNull(budgetLineRepository,
                "budgetLineRepository 不能为空");
        this.budgetBalanceRepository = Objects.requireNonNull(budgetBalanceRepository,
                "budgetBalanceRepository 不能为空");
        this.projectRepository = Objects.requireNonNull(projectRepository, "projectRepository 不能为空");
        this.projectMemberRepository = Objects.requireNonNull(projectMemberRepository,
                "projectMemberRepository 不能为空");
        this.authorizationService = Objects.requireNonNull(authorizationService,
                "authorizationService 不能为空");
        this.policyRegistry = Objects.requireNonNull(policyRegistry, "policyRegistry 不能为空");
        this.fundEventAppender = Objects.requireNonNull(fundEventAppender, "fundEventAppender 不能为空");
        this.workflowLauncher = Objects.requireNonNull(workflowLauncher, "workflowLauncher 不能为空");
        this.auditRecorder = Objects.requireNonNull(auditRecorder, "auditRecorder 不能为空");
        this.outboxAppendPort = Objects.requireNonNull(outboxAppendPort, "outboxAppendPort 不能为空");
        this.jsonHasher = Objects.requireNonNull(jsonHasher, "jsonHasher 不能为空");
        this.clock = Objects.requireNonNull(clock, "clock 不能为空");
    }

    /**
     * 提交支出并占用预算（TC-FUND-012～TC-FUND-027、TC-FUND-069）。
     *
     * @param expenseId      支出 ID
     * @param cmd            提交命令
     * @param idempotencyKey 提交幂等键
     * @param auth           当前请求授权上下文
     * @return 提交结果（资金事件、版本号、可用余额、风险提示、审批实例）
     * @throws BizException 支出不存在 {@code RPM-COMMON-404}；无权限 {@code RPM-COMMON-403}；
     *                      版本冲突 {@code RPM-COMMON-409}；参数非法 {@code RPM-COMMON-400}；
     *                      材料缺失 {@code RPM-COMMON-422}；状态不允许 {@code RPM-FUND-005}；
     *                      预算未生效/科目缺失 {@code RPM-FUND-002}/{@code RPM-FUND-004}；
     *                      余额不足 {@code RPM-FUND-001}；需先调整/特批 {@code RPM-FUND-006}；
     *                      重复事件载荷不一致 {@code RPM-FUND-003}
     */
    @Transactional
    public ExpenseSubmitted submit(String expenseId, SubmitExpenseCmd cmd, String idempotencyKey,
                                   AuthContext auth) {
        Objects.requireNonNull(cmd, "cmd 不能为空");
        Objects.requireNonNull(auth, "auth 不能为空");
        ExpenseAppService.requireText(idempotencyKey, "提交幂等键不能为空");
        String key = idempotencyKey.trim();

        ExpenseRequestEntity expense = expenseRequestRepository.selectForUpdate(expenseId)
                .orElseThrow(() -> new BizException(ErrorCode.COMMON_404, "支出不存在"));
        ProjectEntity project = loadProject(expense.getProjectId());
        authorize(project, auth, FundingPermissions.EXPENSE_SUBMIT);

        String replayExpenseId = versionRepository.findBySubmitIdempotencyKey(key)
                .map(ExpenseRequestVersionEntity::getExpenseRequestId)
                .orElse(null);
        if (replayExpenseId != null) {
            if (!expenseId.equals(replayExpenseId)) {
                throw new BizException(ErrorCode.COMMON_409, "提交幂等键已被其他支出使用");
            }
            if (cmd.amount() != null && expense.getAmount() != null
                    && expense.getAmount().compareTo(cmd.amount()) != 0) {
                throw new BizException(ErrorCode.FUND_003, "重复提交幂等键但金额不一致");
            }
            return replay(expense, key);
        }

        if (!ProjectEntity.STATUS_EXECUTING.equals(project.getStatus())) {
            throw new BizException(ErrorCode.FUND_005, "项目当前状态不允许提交支出");
        }
        if (!ExpenseRequestEntity.STATUS_DRAFT.equals(expense.getStatus())
                && !ExpenseRequestEntity.STATUS_SUPPLEMENT.equals(expense.getStatus())) {
            throw new BizException(ErrorCode.FUND_005, "支出当前状态不允许提交");
        }
        if (cmd.expectedVersion() == null || !Objects.equals(expense.getVersion(), cmd.expectedVersion())) {
            throw new BizException(ErrorCode.COMMON_409, "支出已被并发修改，请刷新后重试");
        }
        validateSubmit(cmd);

        BudgetVersionEntity budgetVersion = budgetVersionRepository.findById(cmd.budgetVersionId().trim())
                .orElseThrow(() -> new BizException(ErrorCode.FUND_002, "预算版本不存在"));
        if (!BudgetVersionEntity.STATUS_EFFECTIVE.equals(budgetVersion.getStatus())
                || !project.getId().equals(budgetVersion.getProjectId())) {
            throw new BizException(ErrorCode.FUND_002, "预算版本未生效或不属于当前项目");
        }
        BudgetLineEntity line = budgetLineRepository.findById(cmd.budgetLineId().trim())
                .orElseThrow(() -> new BizException(ErrorCode.FUND_004, "预算科目不存在"));
        if (!budgetVersion.getId().equals(line.getBudgetVersionId())) {
            throw new BizException(ErrorCode.FUND_004, "预算科目不属于指定预算版本");
        }
        BudgetBalanceEntity balance = budgetBalanceRepository.selectForUpdate(line.getId())
                .orElseThrow(() -> new BizException(ErrorCode.FUND_002, "预算科目余额不存在"));

        String reserveKey = reserveKey(expense.getId());
        FundEventEntity existingReserve = fundEventRepository.findByIdempotencyKey(reserveKey).orElse(null);
        boolean alreadyReserved = existingReserve != null;
        if (existingReserve != null && existingReserve.getAmount().compareTo(cmd.amount()) != 0) {
            throw new BizException(ErrorCode.FUND_003, "补正后金额变化，请先取消原占用");
        }
        BudgetDecision decision = alreadyReserved
                ? BudgetDecision.allow()
                : policyRegistry.policy(controlLevel(line, budgetVersion))
                        .decide(balance, cmd.amount(), new ExpenseContext(project.getId(), expense.getId()));

        OffsetDateTime now = OffsetDateTime.now(clock);
        ExpenseRequestVersionEntity version = buildVersion(expense, cmd, auth, now, key);
        versionRepository.insert(version);

        expense.setBudgetVersionId(budgetVersion.getId());
        expense.setBudgetLineId(line.getId());
        expense.setAmount(cmd.amount());
        expense.setPurpose(cmd.purpose().trim());
        FundEventEntity reserveEvent = alreadyReserved
                ? existingReserve
                : fundEventAppender.append(new FundEventCommand(
                        FundEventEntity.TYPE_RESERVE,
                        expense.getProjectId(),
                        expense.getId(),
                        budgetVersion.getId(),
                        line.getId(),
                        cmd.amount(),
                        expense.getRequestNo(),
                        reserveKey,
                        null,
                        eventPayload("EXPENSE_SUBMIT", expense.getId(), version.getId()),
                        auth.userId()));
        expense.submitForReview(version.getId(), UuidUtils.formatCompact(auth.userId()), now);
        if (expenseRequestRepository.update(expense) == 0) {
            throw new BizException(ErrorCode.COMMON_409, "支出已被并发修改，请重试");
        }

        String instanceId = workflowLauncher.startExpense(new ExpenseWorkflowLauncher.ExpenseWorkflowStart(
                BIZ_TYPE_EXPENSE,
                UuidUtils.parse(expense.getProjectId()),
                UuidUtils.parse(expense.getId()),
                UuidUtils.parse(version.getId()),
                auth.userId(),
                cmd.amount(),
                workflowVariables(expense, budgetVersion, decision)));
        outboxAppendPort.append(new OutboxEvent(UUID.randomUUID(), OBJECT_EXPENSE,
                UuidUtils.parse(expense.getId()), AUDIT_EXPENSE_SUBMITTED,
                Map.of("expenseId", expense.getId(), "projectId", expense.getProjectId(),
                        "versionId", version.getId(), "fundEventId", reserveEvent.getId()),
                TraceIds.currentOrUntracked(), "EXPENSE_SUBMITTED:" + version.getId()));
        audit(auth.userId(), expense.getId(), "支出提交 " + expense.getRequestNo());

        BigDecimal availableAfter = alreadyReserved
                ? balance.availableAmount()
                : balance.availableAmount().subtract(cmd.amount());
        return new ExpenseSubmitted(expense.getId(), reserveEvent.getId(), version.getVersionNo(),
                availableAfter, decision.hasWarning() ? decision.riskWarning() : null, instanceId);
    }

    private ExpenseSubmitted replay(ExpenseRequestEntity expense, String submitIdempotencyKey) {
        ExpenseRequestVersionEntity version = versionRepository
                .findBySubmitIdempotencyKey(submitIdempotencyKey).orElseThrow();
        FundEventEntity reserveEvent = fundEventRepository.findByIdempotencyKey(reserveKey(expense.getId()))
                .orElseThrow(() -> new BizException(ErrorCode.FUND_003, "提交版本缺少占用事件"));
        BigDecimal available = budgetBalanceRepository.findByBudgetLineId(expense.getBudgetLineId())
                .map(BudgetBalanceEntity::availableAmount)
                .orElse(null);
        String instanceId = workflowLauncher.findInstanceId(BIZ_TYPE_EXPENSE,
                UuidUtils.parse(expense.getId()), UuidUtils.parse(version.getId())).orElse(null);
        return new ExpenseSubmitted(expense.getId(), reserveEvent.getId(), version.getVersionNo(),
                available, null, instanceId);
    }

    private ExpenseRequestVersionEntity buildVersion(ExpenseRequestEntity expense, SubmitExpenseCmd cmd,
                                                    AuthContext auth, OffsetDateTime now, String key) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("expenseId", expense.getId());
        payload.put("requestNo", expense.getRequestNo());
        payload.put("projectId", expense.getProjectId());
        payload.put("budgetVersionId", cmd.budgetVersionId().trim());
        payload.put("budgetLineId", cmd.budgetLineId().trim());
        payload.put("amount", cmd.amount());
        payload.put("purpose", cmd.purpose().trim());
        payload.put("payeeName", expense.getPayeeName());
        payload.put("payeeAccountMasked", expense.getPayeeBankAccountMasked());
        payload.put("attachmentFileIds", cmd.attachmentFileIds());
        payload.put("specialReason", cmd.specialReason());
        String canonical = jsonHasher.canonicalJson(payload);

        ExpenseRequestVersionEntity version = new ExpenseRequestVersionEntity();
        version.setId(ExpenseAppService.newCompactId());
        version.setExpenseRequestId(expense.getId());
        version.setVersionNo(nextVersionNo(expense.getId()));
        version.setPayload(canonical);
        version.setContentHash(jsonHasher.sha256(canonical));
        version.setSubmittedBy(UuidUtils.formatCompact(auth.userId()));
        version.setSubmittedAt(now);
        version.setSubmitIdempotencyKey(key);
        return version;
    }

    private int nextVersionNo(String expenseId) {
        return versionRepository.findByExpenseRequestId(expenseId).stream()
                .map(ExpenseRequestVersionEntity::getVersionNo)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(0) + 1;
    }

    private void validateSubmit(SubmitExpenseCmd cmd) {
        ExpenseAppService.requireAmount(cmd.amount());
        ExpenseAppService.requireText(cmd.purpose(), "用途说明不能为空");
        ExpenseAppService.requireText(cmd.budgetVersionId(), "预算版本不能为空");
        ExpenseAppService.requireText(cmd.budgetLineId(), "预算科目不能为空");
        if (cmd.attachmentFileIds() == null || cmd.attachmentFileIds().isEmpty()) {
            throw new BizException(ErrorCode.COMMON_422, "合同/事项说明等必填附件不能为空");
        }
    }

    private static String controlLevel(BudgetLineEntity line, BudgetVersionEntity version) {
        return line.getControlLevel() == null || line.getControlLevel().isBlank()
                ? version.getControlLevel() : line.getControlLevel();
    }

    private static String reserveKey(String expenseId) {
        return "expense:" + expenseId + ":RESERVE";
    }

    private static String eventPayload(String source, String expenseId, String versionId) {
        return "{\"source\":\"" + source + "\",\"expenseId\":\"" + expenseId
                + "\",\"versionId\":\"" + versionId + "\"}";
    }

    private static Map<String, Object> workflowVariables(ExpenseRequestEntity expense,
                                                         BudgetVersionEntity version,
                                                         BudgetDecision decision) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("requestNo", expense.getRequestNo());
        variables.put("budgetVersionId", version.getId());
        variables.put("controlLevel", version.getControlLevel());
        if (decision.hasWarning()) {
            variables.put("riskWarning", decision.riskWarning());
        }
        return variables;
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

    private void audit(UUID actorId, String expenseId, String reason) {
        auditRecorder.record(new AuditEvent(
                TraceIds.currentOrUntracked(),
                actorId,
                null,
                AUDIT_EXPENSE_SUBMITTED,
                OBJECT_EXPENSE,
                UuidUtils.parse(expenseId),
                null,
                null,
                null,
                reason,
                null,
                null,
                clock.instant()));
    }
}
