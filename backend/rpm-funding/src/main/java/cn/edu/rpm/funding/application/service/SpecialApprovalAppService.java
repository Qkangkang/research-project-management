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
import cn.edu.rpm.funding.application.dto.SpecialApprovalCmd;
import cn.edu.rpm.funding.application.dto.SpecialApprovalView;
import cn.edu.rpm.funding.domain.model.FundingPermissions;
import cn.edu.rpm.funding.domain.port.ExpenseWorkflowLauncher;
import cn.edu.rpm.funding.infrastructure.entity.ExpenseRequestEntity;
import cn.edu.rpm.funding.infrastructure.entity.ExpenseRequestVersionEntity;
import cn.edu.rpm.funding.infrastructure.entity.ExpenseSpecialApprovalEntity;
import cn.edu.rpm.funding.infrastructure.repository.ExpenseRequestRepository;
import cn.edu.rpm.funding.infrastructure.repository.ExpenseRequestVersionRepository;
import cn.edu.rpm.funding.infrastructure.repository.ExpenseSpecialApprovalRepository;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 支出特批应用服务（T-FUND-024；技术文档 08 §7.1、07 §7）。
 *
 * <p>发起特批时校验类型、理由长度（≥20 字）、金额与重复未决申请；每次都生成新的
 * 不可变快照版本作为特批依据（避免同一业务版本重复启动流程实例）；随后启动独立
 * 特批审批节点。单笔金额超过
 * {@code rpm.funding.special-approval.max-amount} 时向流程传入 {@code countersignRequired}
 * 变量，由模板追加院领导加签节点。</p>
 */
@Service
public class SpecialApprovalAppService {

    /** 工作流业务类型：特批审批。 */
    public static final String BIZ_TYPE_SPECIAL_APPROVAL = "SPECIAL_APPROVAL";

    /** 审计动作：特批申请发起。 */
    public static final String AUDIT_SPECIAL_REQUESTED = "SPECIAL_APPROVAL_REQUESTED";

    /** 审计动作：超上限需加签。 */
    public static final String AUDIT_SPECIAL_COUNTERSIGN = "SPECIAL_APPROVAL_COUNTERSIGN_REQUIRED";

    /** 审计对象类型：特批申请。 */
    static final String OBJECT_SPECIAL = "EXPENSE_SPECIAL_APPROVAL";

    /** 允许发起特批的支出状态。 */
    private static final Set<String> INITIABLE_STATUSES = Set.of(
            ExpenseRequestEntity.STATUS_DRAFT, ExpenseRequestEntity.STATUS_SUPPLEMENT);

    /** 允许的特批类型。 */
    private static final Set<String> SPECIAL_TYPES = Set.of(
            ExpenseSpecialApprovalEntity.TYPE_OVER_BUDGET,
            ExpenseSpecialApprovalEntity.TYPE_NO_BUDGET,
            ExpenseSpecialApprovalEntity.TYPE_OVER_LIMIT);

    /** 特批理由最小长度（字符）。 */
    private static final int MIN_REASON_LENGTH = 20;

    private final ExpenseRequestRepository expenseRequestRepository;

    private final ExpenseRequestVersionRepository versionRepository;

    private final ExpenseSpecialApprovalRepository specialApprovalRepository;

    private final ProjectRepository projectRepository;

    private final ProjectMemberRepository projectMemberRepository;

    private final AuthorizationService authorizationService;

    private final ExpenseWorkflowLauncher workflowLauncher;

    private final AuditRecorder auditRecorder;

    private final OutboxAppendPort outboxAppendPort;

    private final CanonicalJsonHasher jsonHasher;

    private final Clock clock;

    private final BigDecimal maxAmount;

    /**
     * 构造特批应用服务。
     *
     * @param expenseRequestRepository    支出仓储
     * @param versionRepository           支出不可变版本仓储
     * @param specialApprovalRepository   特批仓储
     * @param projectRepository           项目仓储
     * @param projectMemberRepository     项目成员仓储（授权装载）
     * @param authorizationService        统一授权服务
     * @param workflowLauncher            审批启动端口
     * @param auditRecorder               审计记录器
     * @param outboxAppendPort            Outbox 追加端口
     * @param jsonHasher                  规范化 JSON 工具
     * @param clock                       时间基准
     * @param maxAmount                   单笔特批额度上限；{@code 0} 表示不限制
     */
    public SpecialApprovalAppService(ExpenseRequestRepository expenseRequestRepository,
                                     ExpenseRequestVersionRepository versionRepository,
                                     ExpenseSpecialApprovalRepository specialApprovalRepository,
                                     ProjectRepository projectRepository,
                                     ProjectMemberRepository projectMemberRepository,
                                     AuthorizationService authorizationService,
                                     ExpenseWorkflowLauncher workflowLauncher,
                                     AuditRecorder auditRecorder,
                                     OutboxAppendPort outboxAppendPort,
                                     CanonicalJsonHasher jsonHasher,
                                     Clock clock,
                                     @Value("${rpm.funding.special-approval.max-amount:0}")
                                     BigDecimal maxAmount) {
        this.expenseRequestRepository = Objects.requireNonNull(expenseRequestRepository,
                "expenseRequestRepository 不能为空");
        this.versionRepository = Objects.requireNonNull(versionRepository, "versionRepository 不能为空");
        this.specialApprovalRepository = Objects.requireNonNull(specialApprovalRepository,
                "specialApprovalRepository 不能为空");
        this.projectRepository = Objects.requireNonNull(projectRepository, "projectRepository 不能为空");
        this.projectMemberRepository = Objects.requireNonNull(projectMemberRepository,
                "projectMemberRepository 不能为空");
        this.authorizationService = Objects.requireNonNull(authorizationService,
                "authorizationService 不能为空");
        this.workflowLauncher = Objects.requireNonNull(workflowLauncher, "workflowLauncher 不能为空");
        this.auditRecorder = Objects.requireNonNull(auditRecorder, "auditRecorder 不能为空");
        this.outboxAppendPort = Objects.requireNonNull(outboxAppendPort, "outboxAppendPort 不能为空");
        this.jsonHasher = Objects.requireNonNull(jsonHasher, "jsonHasher 不能为空");
        this.clock = Objects.requireNonNull(clock, "clock 不能为空");
        this.maxAmount = maxAmount == null ? BigDecimal.ZERO : maxAmount;
    }

    /**
     * 发起特批申请（TC-FUND-102、TC-FUND-107）。
     *
     * @param expenseId 支出 ID
     * @param cmd       特批命令
     * @param auth      当前请求授权上下文
     * @return 特批视图（{@code PENDING}）
     * @throws BizException 支出不存在 {@code RPM-COMMON-404}；无权限 {@code RPM-COMMON-403}；
     *                      参数非法 {@code RPM-COMMON-400}；状态不允许 {@code RPM-COMMON-422}；
     *                      重复未决申请 {@code RPM-COMMON-409}
     */
    @Transactional
    public SpecialApprovalView initiate(String expenseId, SpecialApprovalCmd cmd, AuthContext auth) {
        Objects.requireNonNull(cmd, "cmd 不能为空");
        Objects.requireNonNull(auth, "auth 不能为空");
        ExpenseRequestEntity expense = expenseRequestRepository.selectForUpdate(expenseId)
                .orElseThrow(() -> new BizException(ErrorCode.COMMON_404, "支出不存在"));
        ProjectEntity project = projectRepository.findById(expense.getProjectId())
                .orElseThrow(() -> new BizException(ErrorCode.PROJ_001));
        authorize(project, auth, FundingPermissions.EXPENSE_EDIT);
        if (!INITIABLE_STATUSES.contains(expense.getStatus())) {
            throw new BizException(ErrorCode.COMMON_422, "支出当前状态不允许发起特批");
        }
        String specialType = validate(cmd);
        if (!specialApprovalRepository.findByExpenseRequestIdAndStatus(
                expense.getId(), ExpenseSpecialApprovalEntity.STATUS_PENDING).isEmpty()) {
            throw new BizException(ErrorCode.COMMON_409, "该支出存在未决特批申请");
        }

        String expenseVersionId = ensureExpenseVersion(expense, auth);
        boolean countersignRequired = maxAmount.compareTo(BigDecimal.ZERO) > 0
                && cmd.overAmount().compareTo(maxAmount) > 0;
        ExpenseSpecialApprovalEntity approval = new ExpenseSpecialApprovalEntity();
        approval.setId(ExpenseAppService.newCompactId());
        approval.setExpenseRequestId(expense.getId());
        approval.setExpenseVersionId(expenseVersionId);
        approval.setSpecialType(specialType);
        approval.setBudgetVersionId(expense.getBudgetVersionId());
        approval.setBudgetLineId(expense.getBudgetLineId());
        approval.setOverAmount(cmd.overAmount());
        approval.setReason(cmd.reason().trim());
        approval.setApplicantId(UuidUtils.formatCompact(auth.userId()));
        approval.setApplicantName(applicantName(auth));
        approval.setEvidenceFileIds(jsonHasher.canonicalJson(
                cmd.evidenceFileIds() == null ? List.of() : cmd.evidenceFileIds()));
        approval.setStatus(ExpenseSpecialApprovalEntity.STATUS_PENDING);

        Map<String, Object> variables = new HashMap<>();
        variables.put("expenseId", expense.getId());
        variables.put("overAmount", cmd.overAmount());
        variables.put("specialType", specialType);
        variables.put("countersignRequired", countersignRequired);
        String instanceId = workflowLauncher.startSpecialApproval(
                new ExpenseWorkflowLauncher.ExpenseWorkflowStart(
                        BIZ_TYPE_SPECIAL_APPROVAL,
                        UuidUtils.parse(expense.getProjectId()),
                        UuidUtils.parse(approval.getId()),
                        UuidUtils.parse(expenseVersionId),
                        auth.userId(),
                        cmd.overAmount(),
                        variables));
        approval.attachWorkflowInstance(instanceId);
        specialApprovalRepository.insert(approval);

        audit(auth.userId(), AUDIT_SPECIAL_REQUESTED, approval.getId(), "发起支出特批");
        if (countersignRequired) {
            audit(auth.userId(), AUDIT_SPECIAL_COUNTERSIGN, approval.getId(),
                    "特批金额超过上限，追加院领导加签");
        }
        outboxAppendPort.append(new OutboxEvent(UUID.randomUUID(), OBJECT_SPECIAL,
                UuidUtils.parse(approval.getId()), AUDIT_SPECIAL_REQUESTED,
                Map.of("approvalId", approval.getId(), "expenseId", expense.getId(),
                        "overAmount", cmd.overAmount()),
                TraceIds.currentOrUntracked(), AUDIT_SPECIAL_REQUESTED + ":" + approval.getId()));
        return toView(approval);
    }

    /**
     * 查询特批详情。
     *
     * @param approvalId 特批 ID
     * @param auth       当前请求授权上下文
     * @return 特批视图
     * @throws BizException 特批不存在 {@code RPM-COMMON-404}；无权限 {@code RPM-COMMON-403}
     */
    @Transactional(readOnly = true)
    public SpecialApprovalView detail(String approvalId, AuthContext auth) {
        Objects.requireNonNull(auth, "auth 不能为空");
        ExpenseSpecialApprovalEntity approval = specialApprovalRepository.findById(approvalId)
                .orElseThrow(() -> new BizException(ErrorCode.COMMON_404, "特批申请不存在"));
        requireViewPermission(approval.getExpenseRequestId(), auth);
        return toView(approval);
    }

    /**
     * 查询某支出的特批历史。
     *
     * @param expenseId 支出 ID
     * @param auth      当前请求授权上下文
     * @return 特批视图列表；无数据返回空列表
     * @throws BizException 支出不存在 {@code RPM-COMMON-404}；无权限 {@code RPM-COMMON-403}
     */
    @Transactional(readOnly = true)
    public List<SpecialApprovalView> list(String expenseId, AuthContext auth) {
        Objects.requireNonNull(auth, "auth 不能为空");
        requireViewPermission(expenseId, auth);
        return specialApprovalRepository.findByExpenseRequestId(expenseId).stream()
                .map(SpecialApprovalAppService::toView)
                .toList();
    }

    private String ensureExpenseVersion(ExpenseRequestEntity expense, AuthContext auth) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("expenseId", expense.getId());
        payload.put("requestNo", expense.getRequestNo());
        payload.put("projectId", expense.getProjectId());
        payload.put("budgetVersionId", expense.getBudgetVersionId());
        payload.put("budgetLineId", expense.getBudgetLineId());
        payload.put("amount", expense.getAmount());
        payload.put("purpose", expense.getPurpose());
        payload.put("payeeName", expense.getPayeeName());
        payload.put("payeeAccountMasked", expense.getPayeeBankAccountMasked());
        String canonical = jsonHasher.canonicalJson(payload);

        ExpenseRequestVersionEntity version = new ExpenseRequestVersionEntity();
        version.setId(ExpenseAppService.newCompactId());
        version.setExpenseRequestId(expense.getId());
        version.setVersionNo(nextVersionNo(expense.getId()));
        version.setPayload(canonical);
        version.setContentHash(jsonHasher.sha256(canonical));
        version.setSubmittedBy(UuidUtils.formatCompact(auth.userId()));
        version.setSubmittedAt(OffsetDateTime.now(clock));
        versionRepository.insert(version);

        expense.setCurrentVersionId(version.getId());
        if (expenseRequestRepository.update(expense) == 0) {
            throw new BizException(ErrorCode.COMMON_409, "支出已被并发修改，请重试");
        }
        return version.getId();
    }

    private int nextVersionNo(String expenseId) {
        return versionRepository.findByExpenseRequestId(expenseId).stream()
                .map(ExpenseRequestVersionEntity::getVersionNo)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(0) + 1;
    }

    private String validate(SpecialApprovalCmd cmd) {
        String type = cmd.specialType() == null ? "" : cmd.specialType().trim().toUpperCase(Locale.ROOT);
        if (!SPECIAL_TYPES.contains(type)) {
            throw new BizException(ErrorCode.COMMON_400, "特批类型非法");
        }
        if (cmd.reason() == null || cmd.reason().trim().length() < MIN_REASON_LENGTH) {
            throw new BizException(ErrorCode.COMMON_400, "特批理由不能少于 " + MIN_REASON_LENGTH + " 字");
        }
        if (cmd.overAmount() == null || cmd.overAmount().compareTo(BigDecimal.ZERO) <= 0
                || cmd.overAmount().stripTrailingZeros().scale() > 2) {
            throw new BizException(ErrorCode.COMMON_400, "特批金额必须为正数且最多两位小数");
        }
        return type;
    }

    private void requireViewPermission(String expenseId, AuthContext auth) {
        ExpenseRequestEntity expense = expenseRequestRepository.findById(expenseId)
                .orElseThrow(() -> new BizException(ErrorCode.COMMON_404, "支出不存在"));
        ProjectEntity project = projectRepository.findById(expense.getProjectId())
                .orElseThrow(() -> new BizException(ErrorCode.PROJ_001));
        authorize(project, auth, FundingPermissions.VIEW);
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
                TraceIds.currentOrUntracked(), actorId, null, action, OBJECT_SPECIAL,
                UuidUtils.parse(objectId), null, null, null, reason, null, null, clock.instant()));
    }

    private static String applicantName(AuthContext auth) {
        return auth.employeeNo() == null || auth.employeeNo().isBlank()
                ? UuidUtils.formatCompact(auth.userId()) : auth.employeeNo();
    }

    static SpecialApprovalView toView(ExpenseSpecialApprovalEntity approval) {
        return new SpecialApprovalView(
                approval.getId(),
                approval.getExpenseRequestId(),
                approval.getExpenseVersionId(),
                approval.getSpecialType(),
                approval.getOverAmount(),
                approval.getReason(),
                approval.getStatus(),
                approval.getWfInstanceId(),
                approval.getApproverId(),
                approval.getApprovedAt(),
                approval.getValidUntil(),
                approval.getAdjustmentVersionId());
    }
}
