package cn.edu.rpm.funding.application.service;

import cn.edu.rpm.audit.domain.model.AuditEvent;
import cn.edu.rpm.audit.domain.service.AuditRecorder;
import cn.edu.rpm.common.domain.service.NumberGenerator;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.common.util.TraceIds;
import cn.edu.rpm.common.util.UuidUtils;
import cn.edu.rpm.funding.application.dto.CreateExpenseCmd;
import cn.edu.rpm.funding.application.dto.ExpenseView;
import cn.edu.rpm.funding.domain.model.FundingPermissions;
import cn.edu.rpm.funding.infrastructure.entity.BudgetLineEntity;
import cn.edu.rpm.funding.infrastructure.entity.BudgetVersionEntity;
import cn.edu.rpm.funding.infrastructure.entity.ExpenseRequestEntity;
import cn.edu.rpm.funding.infrastructure.repository.BudgetLineRepository;
import cn.edu.rpm.funding.infrastructure.repository.BudgetVersionRepository;
import cn.edu.rpm.funding.infrastructure.repository.ExpenseRequestRepository;
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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 支出应用服务（T-FUND-019；技术文档 05 §3.2、07 §4.2）。
 *
 * <p>创建草稿时校验项目授权、项目状态、预算版本/科目归属、金额与用途；
 * 银行账号只保存脱敏值与加密值，查询响应只返回脱敏值。创建幂等键命中时
 * 返回首次创建的支出，不重复插入。</p>
 */
@Service
public class ExpenseAppService {

    /** 支出单号规则编码。 */
    public static final String RULE_EXPENSE_REQUEST_NO = "EXPENSE_REQUEST_NO";

    /** 审计动作：支出草稿创建。 */
    public static final String AUDIT_EXPENSE_CREATED = "EXPENSE_CREATED";

    /** 审计对象类型：支出申请。 */
    static final String OBJECT_EXPENSE = "EXPENSE_REQUEST";

    /** 允许创建支出草稿的项目状态。 */
    private static final Set<String> CREATABLE_STATUSES = Set.of(
            ProjectEntity.STATUS_PENDING_START, ProjectEntity.STATUS_EXECUTING);

    private final ExpenseRequestRepository expenseRequestRepository;

    private final BudgetVersionRepository budgetVersionRepository;

    private final BudgetLineRepository budgetLineRepository;

    private final ProjectRepository projectRepository;

    private final ProjectMemberRepository projectMemberRepository;

    private final AuthorizationService authorizationService;

    private final AuditRecorder auditRecorder;

    private final NumberGenerator numberGenerator;

    private final Clock clock;

    /**
     * 构造支出应用服务。
     *
     * @param expenseRequestRepository 支出仓储
     * @param budgetVersionRepository  预算版本仓储
     * @param budgetLineRepository     预算科目仓储
     * @param projectRepository        项目仓储
     * @param projectMemberRepository  项目成员仓储（授权装载）
     * @param authorizationService     统一授权服务
     * @param auditRecorder            审计记录器
     * @param numberGenerator          业务编号生成器
     * @param clock                    时间基准
     */
    public ExpenseAppService(ExpenseRequestRepository expenseRequestRepository,
                             BudgetVersionRepository budgetVersionRepository,
                             BudgetLineRepository budgetLineRepository,
                             ProjectRepository projectRepository,
                             ProjectMemberRepository projectMemberRepository,
                             AuthorizationService authorizationService,
                             AuditRecorder auditRecorder,
                             NumberGenerator numberGenerator,
                             Clock clock) {
        this.expenseRequestRepository = Objects.requireNonNull(expenseRequestRepository,
                "expenseRequestRepository 不能为空");
        this.budgetVersionRepository = Objects.requireNonNull(budgetVersionRepository,
                "budgetVersionRepository 不能为空");
        this.budgetLineRepository = Objects.requireNonNull(budgetLineRepository,
                "budgetLineRepository 不能为空");
        this.projectRepository = Objects.requireNonNull(projectRepository, "projectRepository 不能为空");
        this.projectMemberRepository = Objects.requireNonNull(projectMemberRepository,
                "projectMemberRepository 不能为空");
        this.authorizationService = Objects.requireNonNull(authorizationService,
                "authorizationService 不能为空");
        this.auditRecorder = Objects.requireNonNull(auditRecorder, "auditRecorder 不能为空");
        this.numberGenerator = Objects.requireNonNull(numberGenerator, "numberGenerator 不能为空");
        this.clock = Objects.requireNonNull(clock, "clock 不能为空");
    }

    /**
     * 创建支出草稿（TC-FUND-039、TC-FUND-069）。
     *
     * @param projectId 项目 ID
     * @param cmd       创建命令
     * @param auth      当前请求授权上下文
     * @return 新建（或幂等命中）的支出视图
     * @throws BizException 项目不存在 {@code RPM-PROJ-001}；无权限 {@code RPM-COMMON-403}；
     *                      项目状态不允许 {@code RPM-PROJ-002}；参数非法 {@code RPM-COMMON-400}；
     *                      预算版本/科目不合法 {@code RPM-COMMON-404}/{@code RPM-FUND-004}
     */
    @Transactional
    public ExpenseView createDraft(String projectId, CreateExpenseCmd cmd, AuthContext auth) {
        Objects.requireNonNull(cmd, "cmd 不能为空");
        Objects.requireNonNull(auth, "auth 不能为空");
        ProjectEntity project = loadProject(projectId);
        authorize(project, auth, FundingPermissions.EXPENSE_CREATE);
        if (!CREATABLE_STATUSES.contains(project.getStatus())) {
            throw new BizException(ErrorCode.PROJ_002, "项目当前状态不允许创建支出");
        }
        String idempotencyKey = trimToNull(cmd.idempotencyKey());
        if (idempotencyKey != null) {
            Optional<ExpenseRequestEntity> existing = expenseRequestRepository
                    .findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                if (!project.getId().equals(existing.get().getProjectId())) {
                    throw new BizException(ErrorCode.COMMON_409, "创建幂等键已被其他项目使用");
                }
                return toView(existing.get());
            }
        }
        requireAmount(cmd.amount());
        requireText(cmd.purpose(), "用途说明不能为空");
        BudgetVersionEntity budgetVersion = requireBudgetVersion(project, cmd.budgetVersionId());
        requireBudgetLine(budgetVersion, cmd.budgetLineId());

        ExpenseRequestEntity expense = new ExpenseRequestEntity();
        expense.setId(newCompactId());
        expense.setRequestNo(numberGenerator.next(RULE_EXPENSE_REQUEST_NO));
        expense.setProjectId(project.getId());
        expense.setBudgetVersionId(budgetVersion.getId());
        expense.setBudgetLineId(cmd.budgetLineId().trim());
        expense.setAmount(cmd.amount());
        expense.setPurpose(cmd.purpose().trim());
        expense.setPayeeName(trimToNull(cmd.payeeName()));
        expense.setPayeeBankAccountMasked(trimToNull(cmd.payeeBankAccountMasked()));
        expense.setPayeeBankAccountEncrypted(trimToNull(cmd.payeeBankAccountEncrypted()));
        expense.setPayeeBankName(trimToNull(cmd.payeeBankName()));
        expense.setStatus(ExpenseRequestEntity.STATUS_DRAFT);
        expense.setIdempotencyKey(idempotencyKey);
        expense.setCreatedBy(UuidUtils.formatCompact(auth.userId()));
        expenseRequestRepository.insert(expense);

        audit(auth.userId(), AUDIT_EXPENSE_CREATED, expense.getId(),
                "创建支出草稿 " + expense.getRequestNo());
        return toView(expense);
    }

    /**
     * 查询支出详情（TC-FUND-039）。
     *
     * @param expenseId 支出 ID
     * @param auth      当前请求授权上下文
     * @return 支出视图（银行账号仅脱敏值）
     * @throws BizException 支出不存在 {@code RPM-COMMON-404}；无权限 {@code RPM-COMMON-403}
     */
    @Transactional(readOnly = true)
    public ExpenseView detail(String expenseId, AuthContext auth) {
        Objects.requireNonNull(auth, "auth 不能为空");
        ExpenseRequestEntity expense = expenseRequestRepository.findById(expenseId)
                .orElseThrow(() -> new BizException(ErrorCode.COMMON_404, "支出不存在"));
        ProjectEntity project = loadProject(expense.getProjectId());
        authorize(project, auth, FundingPermissions.VIEW);
        return toView(expense);
    }

    /**
     * 查询项目支出列表。
     *
     * @param projectId 项目 ID
     * @param auth      当前请求授权上下文
     * @return 支出视图列表；无数据返回空列表
     * @throws BizException 项目不存在 {@code RPM-PROJ-001}；无权限 {@code RPM-COMMON-403}
     */
    @Transactional(readOnly = true)
    public List<ExpenseView> list(String projectId, AuthContext auth) {
        Objects.requireNonNull(auth, "auth 不能为空");
        ProjectEntity project = loadProject(projectId);
        authorize(project, auth, FundingPermissions.VIEW);
        return expenseRequestRepository.findByProjectId(project.getId()).stream()
                .map(ExpenseAppService::toView)
                .toList();
    }

    /**
     * 查询支出并加行级锁（提交/撤回/取消共用）。
     *
     * @param expenseId 支出 ID
     * @return 加锁的支出
     * @throws BizException 支出不存在 {@code RPM-COMMON-404}
     */
    ExpenseRequestEntity lockExpense(String expenseId) {
        return expenseRequestRepository.selectForUpdate(expenseId)
                .orElseThrow(() -> new BizException(ErrorCode.COMMON_404, "支出不存在"));
    }

    private BudgetVersionEntity requireBudgetVersion(ProjectEntity project, String budgetVersionId) {
        requireText(budgetVersionId, "预算版本不能为空");
        BudgetVersionEntity version = budgetVersionRepository.findById(budgetVersionId.trim())
                .orElseThrow(() -> new BizException(ErrorCode.COMMON_404, "预算版本不存在"));
        if (!project.getId().equals(version.getProjectId())) {
            throw new BizException(ErrorCode.FUND_004, "预算版本不属于当前项目");
        }
        return version;
    }

    private void requireBudgetLine(BudgetVersionEntity version, String budgetLineId) {
        requireText(budgetLineId, "预算科目不能为空");
        BudgetLineEntity line = budgetLineRepository.findById(budgetLineId.trim())
                .orElseThrow(() -> new BizException(ErrorCode.FUND_004, "预算科目不存在"));
        if (!version.getId().equals(line.getBudgetVersionId())) {
            throw new BizException(ErrorCode.FUND_004, "预算科目不属于指定预算版本");
        }
    }

    static void requireAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0
                || amount.stripTrailingZeros().scale() > 2) {
            throw new BizException(ErrorCode.COMMON_400, "支出金额必须为正数且最多两位小数");
        }
    }

    static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BizException(ErrorCode.COMMON_400, message);
        }
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

    private void audit(UUID actorId, String action, String objectId, String reason) {
        auditRecorder.record(new AuditEvent(
                TraceIds.currentOrUntracked(),
                actorId,
                null,
                action,
                OBJECT_EXPENSE,
                UuidUtils.parse(objectId),
                null,
                null,
                null,
                reason,
                null,
                null,
                clock.instant()));
    }

    static ExpenseView toView(ExpenseRequestEntity expense) {
        return new ExpenseView(
                expense.getId(),
                expense.getRequestNo(),
                expense.getProjectId(),
                expense.getBudgetVersionId(),
                expense.getBudgetLineId(),
                expense.getAmount(),
                expense.getPurpose(),
                expense.getPayeeName(),
                expense.getPayeeBankAccountMasked(),
                expense.getStatus(),
                expense.getCurrentVersionId(),
                expense.getVersion(),
                expense.getSubmittedAt(),
                expense.getCreatedAt());
    }

    static String newCompactId() {
        return UuidUtils.formatCompact(UUID.randomUUID());
    }

    static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
