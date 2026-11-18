package cn.edu.rpm.funding.application.service;

import cn.edu.rpm.audit.domain.model.AuditEvent;
import cn.edu.rpm.audit.domain.service.AuditRecorder;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.common.util.TraceIds;
import cn.edu.rpm.common.util.UuidUtils;
import cn.edu.rpm.funding.application.dto.BudgetLineCmd;
import cn.edu.rpm.funding.application.dto.BudgetLineDetail;
import cn.edu.rpm.funding.application.dto.BudgetOverview;
import cn.edu.rpm.funding.application.dto.BudgetVersionDetail;
import cn.edu.rpm.funding.application.dto.BudgetVersionSummary;
import cn.edu.rpm.funding.application.dto.CreateBudgetDraftCmd;
import cn.edu.rpm.funding.domain.model.FundEventCommand;
import cn.edu.rpm.funding.domain.model.FundingPermissions;
import cn.edu.rpm.funding.domain.service.FundEventAppender;
import cn.edu.rpm.funding.infrastructure.entity.BudgetBalanceEntity;
import cn.edu.rpm.funding.infrastructure.entity.BudgetLineEntity;
import cn.edu.rpm.funding.infrastructure.entity.BudgetVersionEntity;
import cn.edu.rpm.funding.infrastructure.entity.FundEventEntity;
import cn.edu.rpm.funding.infrastructure.repository.BudgetBalanceRepository;
import cn.edu.rpm.funding.infrastructure.repository.BudgetLineRepository;
import cn.edu.rpm.funding.infrastructure.repository.BudgetVersionRepository;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 预算应用服务（T-FUND-017；技术文档 05 §3.1、07 §4.1）。
 *
 * <p>创建草稿时校验明细合计、科目唯一与金额正数，按项目现有最大版本号递增；
 * 激活时保证同一项目单一 {@code EFFECTIVE} 版本，并逐科目追加 {@code ADJUST} 事件
 * 建立批准额投影（可全量重放）。预算变更（{@code BUDGET} 类型）通过
 * {@link #applyApprovedChange} 激活草稿并替代旧生效版本。</p>
 */
@Service
public class BudgetAppService {

    /** 审计动作：预算草稿创建。 */
    public static final String AUDIT_BUDGET_CREATED = "BUDGET_VERSION_CREATED";

    /** 审计动作：预算版本生效。 */
    public static final String AUDIT_BUDGET_ACTIVATED = "BUDGET_VERSION_ACTIVATED";

    /** 审计对象类型：预算版本。 */
    static final String OBJECT_BUDGET_VERSION = "BUDGET_VERSION";

    /** 允许创建预算的项目状态。 */
    private static final Set<String> BUDGETABLE_STATUSES = Set.of(
            ProjectEntity.STATUS_PENDING_START, ProjectEntity.STATUS_EXECUTING);

    /** 允许的来源类型。 */
    private static final Set<String> SOURCE_TYPES = Set.of(
            BudgetVersionEntity.SOURCE_GRANT,
            BudgetVersionEntity.SOURCE_CONTRACT,
            BudgetVersionEntity.SOURCE_MATCHING,
            BudgetVersionEntity.SOURCE_ADJUSTMENT,
            BudgetVersionEntity.SOURCE_MIGRATION);

    /** 允许的控制级别。 */
    private static final Set<String> CONTROL_LEVELS = Set.of(
            BudgetVersionEntity.CONTROL_BLOCK,
            BudgetVersionEntity.CONTROL_WARN,
            BudgetVersionEntity.CONTROL_APPROVAL);

    private final BudgetVersionRepository budgetVersionRepository;

    private final BudgetLineRepository budgetLineRepository;

    private final BudgetBalanceRepository budgetBalanceRepository;

    private final ProjectRepository projectRepository;

    private final ProjectMemberRepository projectMemberRepository;

    private final AuthorizationService authorizationService;

    private final AuditRecorder auditRecorder;

    private final FundEventAppender fundEventAppender;

    private final Clock clock;

    /**
     * 构造预算应用服务。
     *
     * @param budgetVersionRepository 预算版本仓储
     * @param budgetLineRepository    预算科目仓储
     * @param budgetBalanceRepository 余额投影仓储
     * @param projectRepository       项目仓储
     * @param projectMemberRepository 项目成员仓储（授权装载）
     * @param authorizationService    统一授权服务
     * @param auditRecorder           审计记录器
     * @param fundEventAppender       资金事件追加器
     * @param clock                   时间基准
     */
    public BudgetAppService(BudgetVersionRepository budgetVersionRepository,
                            BudgetLineRepository budgetLineRepository,
                            BudgetBalanceRepository budgetBalanceRepository,
                            ProjectRepository projectRepository,
                            ProjectMemberRepository projectMemberRepository,
                            AuthorizationService authorizationService,
                            AuditRecorder auditRecorder,
                            FundEventAppender fundEventAppender,
                            Clock clock) {
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
        this.auditRecorder = Objects.requireNonNull(auditRecorder, "auditRecorder 不能为空");
        this.fundEventAppender = Objects.requireNonNull(fundEventAppender, "fundEventAppender 不能为空");
        this.clock = Objects.requireNonNull(clock, "clock 不能为空");
    }

    /**
     * 创建预算草稿（TC-FUND-001～TC-FUND-005、TC-FUND-011）。
     *
     * @param projectId 项目 ID
     * @param cmd       创建命令
     * @param auth      当前请求授权上下文
     * @return 新预算版本明细（状态 {@code DRAFT}，余额批准额为零）
     * @throws BizException 项目不存在 {@code RPM-PROJ-001}；无权限 {@code RPM-COMMON-403}；
     *                      项目状态不允许 {@code RPM-PROJ-002}；
     *                      明细/科目/金额非法 {@code RPM-FUND-004}
     */
    @Transactional
    public BudgetVersionDetail createDraft(String projectId, CreateBudgetDraftCmd cmd, AuthContext auth) {
        Objects.requireNonNull(cmd, "cmd 不能为空");
        Objects.requireNonNull(auth, "auth 不能为空");
        ProjectEntity project = loadProject(projectId);
        authorize(project, auth, FundingPermissions.BUDGET_EDIT);
        if (!BUDGETABLE_STATUSES.contains(project.getStatus())) {
            throw new BizException(ErrorCode.PROJ_002, "项目当前状态不允许创建预算");
        }

        ValidatedBudget validated = validate(cmd);
        int nextVersionNo = budgetVersionRepository.findByProjectId(project.getId()).stream()
                .map(BudgetVersionEntity::getVersionNo)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(0) + 1;

        BudgetVersionEntity version = new BudgetVersionEntity();
        version.setId(newCompactId());
        version.setProjectId(project.getId());
        version.setVersionNo(nextVersionNo);
        version.setTotalAmount(validated.totalAmount());
        version.setSourceType(cmd.sourceType().trim().toUpperCase(Locale.ROOT));
        version.setControlLevel(cmd.controlLevel().trim().toUpperCase(Locale.ROOT));
        version.setStatus(BudgetVersionEntity.STATUS_DRAFT);
        version.setEffectiveFrom(cmd.effectiveFrom());
        version.setEffectiveTo(cmd.effectiveTo());
        budgetVersionRepository.insert(version);

        List<BudgetLineEntity> lines = new ArrayList<>();
        List<BudgetLineDetail> details = new ArrayList<>();
        for (BudgetLineCmd lineCmd : validated.lines()) {
            BudgetLineEntity line = new BudgetLineEntity();
            line.setId(newCompactId());
            line.setBudgetVersionId(version.getId());
            line.setSubjectCode(lineCmd.subjectCode().trim());
            line.setSubjectName(lineCmd.subjectName().trim());
            line.setAmount(lineCmd.amount());
            line.setControlLevel(version.getControlLevel());
            budgetLineRepository.insert(line);

            BudgetBalanceEntity balance = new BudgetBalanceEntity();
            balance.setId(newCompactId());
            balance.setBudgetLineId(line.getId());
            budgetBalanceRepository.insert(balance);

            lines.add(line);
            details.add(toLineDetail(line, balance));
        }

        auditRecorder.record(new AuditEvent(
                TraceIds.currentOrUntracked(),
                auth.userId(),
                null,
                AUDIT_BUDGET_CREATED,
                OBJECT_BUDGET_VERSION,
                UuidUtils.parse(version.getId()),
                version.getVersion(),
                null,
                null,
                "创建预算草稿 v" + version.getVersionNo(),
                null,
                null,
                clock.instant()));
        return toDetail(version, details);
    }

    /**
     * 激活预算版本（TC-FUND-007、TC-FUND-008）。
     *
     * <p>同一项目已有生效版本时返回 {@code RPM-FUND-007}，旧版本保持 {@code EFFECTIVE}；
     * 首次激活后逐科目追加 {@code ADJUST} 事件，把批准额从零调整为科目金额。</p>
     *
     * @param budgetVersionId 预算版本 ID
     * @param auth            当前请求授权上下文
     * @return 生效后的预算版本明细
     * @throws BizException 版本不存在 {@code RPM-COMMON-404}；无权限 {@code RPM-COMMON-403}；
     *                      已有生效版本 {@code RPM-FUND-007}；明细为空 {@code RPM-FUND-004}；
     *                      余额行缺失 {@code RPM-FUND-002}
     */
    @Transactional
    public BudgetVersionDetail activate(String budgetVersionId, AuthContext auth) {
        Objects.requireNonNull(auth, "auth 不能为空");
        BudgetVersionEntity version = budgetVersionRepository.findById(budgetVersionId)
                .orElseThrow(() -> new BizException(ErrorCode.COMMON_404, "预算版本不存在"));
        ProjectEntity project = loadProject(version.getProjectId());
        authorize(project, auth, FundingPermissions.BUDGET_SUBMIT);
        return doActivate(version, auth.userId(), null, true);
    }

    /**
     * 预算变更批准后生效（TC-FUND-006、TC-CHG-107；技术文档 08 §8.2）。
     *
     * <p>只接受已关联变更单的草稿版本；生效时旧版本置 {@code SUPERSEDED}，
     * 新版本 {@code EFFECTIVE}，并逐科目追加 {@code ADJUST} 事件。</p>
     *
     * @param projectId        项目 ID
     * @param budgetVersionId  预算草稿版本 ID
     * @param changeRequestId  变更单 ID
     * @param changeVersionId  变更版本 ID
     * @param operatorId       操作人用户 ID
     * @return 生效后的预算版本明细
     * @throws BizException 未关联变更单或版本不匹配 {@code RPM-COMMON-422}；
     *                      余额行缺失 {@code RPM-FUND-002}
     */
    @Transactional
    public BudgetVersionDetail applyApprovedChange(String projectId, String budgetVersionId,
                                                   String changeRequestId, String changeVersionId,
                                                   UUID operatorId) {
        if (isBlank(changeRequestId) || isBlank(changeVersionId)) {
            throw new BizException(ErrorCode.COMMON_422, "预算调整必须关联变更单与变更版本");
        }
        BudgetVersionEntity version = budgetVersionRepository.findById(budgetVersionId)
                .orElseThrow(() -> new BizException(ErrorCode.COMMON_422, "预算调整草稿不存在"));
        if (!UuidUtils.sameId(version.getProjectId(), projectId)
                || !BudgetVersionEntity.STATUS_DRAFT.equals(version.getStatus())) {
            throw new BizException(ErrorCode.COMMON_422, "预算版本与变更单不匹配或不是草稿");
        }
        return doActivate(version, operatorId, changeRequestId, false);
    }

    /**
     * 查询项目预算总览（TC-FUND-009、TC-FUND-010）。
     *
     * @param projectId 项目 ID
     * @param auth      当前请求授权上下文
     * @return 版本摘要与生效版本明细；无预算时 {@code versions=[]} 且
     *         {@code effectiveBudget=null}
     * @throws BizException 项目不存在 {@code RPM-PROJ-001}；无权限 {@code RPM-COMMON-403}
     */
    @Transactional(readOnly = true)
    public BudgetOverview list(String projectId, AuthContext auth) {
        Objects.requireNonNull(auth, "auth 不能为空");
        ProjectEntity project = loadProject(projectId);
        authorize(project, auth, FundingPermissions.VIEW);

        List<BudgetVersionSummary> summaries = budgetVersionRepository.findByProjectId(project.getId())
                .stream()
                .map(BudgetAppService::toSummary)
                .toList();
        BudgetVersionDetail effective = budgetVersionRepository
                .findEffectiveByProjectId(project.getId())
                .map(version -> toDetail(version, loadLineDetails(version)))
                .orElse(null);
        return new BudgetOverview(summaries, effective);
    }

    private BudgetVersionDetail doActivate(BudgetVersionEntity version, UUID actorId,
                                           String changeRequestId, boolean forbidExistingEffective) {
        budgetVersionRepository.findEffectiveByProjectId(version.getProjectId())
                .ifPresent(existing -> {
                    if (forbidExistingEffective) {
                        throw new BizException(ErrorCode.FUND_007, "同一项目已有生效预算版本");
                    }
                    if (!Objects.equals(existing.getId(), version.getId())) {
                        existing.setStatus(BudgetVersionEntity.STATUS_SUPERSEDED);
                        if (budgetVersionRepository.update(existing) == 0) {
                            throw new BizException(ErrorCode.COMMON_409, "旧预算版本并发变更");
                        }
                    }
                });

        List<BudgetLineEntity> lines = budgetLineRepository.findByBudgetVersionId(version.getId());
        if (lines.isEmpty()) {
            throw new BizException(ErrorCode.FUND_004, "预算版本缺少科目明细");
        }
        Map<String, BudgetBalanceEntity> balances = budgetBalanceRepository
                .findByBudgetLineIds(lines.stream().map(BudgetLineEntity::getId).toList())
                .stream()
                .collect(Collectors.toMap(BudgetBalanceEntity::getBudgetLineId, balance -> balance));

        version.setStatus(BudgetVersionEntity.STATUS_EFFECTIVE);
        version.setApprovedBy(UuidUtils.formatCompact(actorId));
        version.setApprovedAt(OffsetDateTime.now(clock));
        if (changeRequestId != null) {
            version.setChangeRequestId(changeRequestId);
        }
        if (budgetVersionRepository.update(version) == 0) {
            throw new BizException(ErrorCode.COMMON_409, "预算版本并发变更");
        }

        List<BudgetLineDetail> details = new ArrayList<>();
        for (BudgetLineEntity line : lines) {
            BudgetBalanceEntity balance = balances.get(line.getId());
            if (balance == null) {
                throw new BizException(ErrorCode.FUND_002, "预算科目缺少余额行");
            }
            fundEventAppender.append(new FundEventCommand(
                    FundEventEntity.TYPE_ADJUST,
                    version.getProjectId(),
                    null,
                    version.getId(),
                    line.getId(),
                    line.getAmount(),
                    null,
                    "budget:" + version.getId() + ":line:" + line.getId() + ":ADJUST",
                    null,
                    "{\"source\":\"BUDGET_ACTIVATION\",\"budgetVersionId\":\"" + version.getId() + "\"}",
                    actorId));
            details.add(toLineDetail(line, balance));
        }

        auditRecorder.record(new AuditEvent(
                TraceIds.currentOrUntracked(),
                actorId,
                null,
                AUDIT_BUDGET_ACTIVATED,
                OBJECT_BUDGET_VERSION,
                UuidUtils.parse(version.getId()),
                version.getVersion(),
                null,
                null,
                "预算版本生效 v" + version.getVersionNo(),
                null,
                null,
                clock.instant()));
        return toDetail(version, details);
    }

    private List<BudgetLineDetail> loadLineDetails(BudgetVersionEntity version) {
        List<BudgetLineEntity> lines = budgetLineRepository.findByBudgetVersionId(version.getId());
        if (lines.isEmpty()) {
            return List.of();
        }
        Map<String, BudgetBalanceEntity> balances = budgetBalanceRepository
                .findByBudgetLineIds(lines.stream().map(BudgetLineEntity::getId).toList())
                .stream()
                .collect(Collectors.toMap(BudgetBalanceEntity::getBudgetLineId, balance -> balance));
        return lines.stream()
                .map(line -> toLineDetail(line, balances.get(line.getId())))
                .toList();
    }

    private ValidatedBudget validate(CreateBudgetDraftCmd cmd) {
        if (isBlank(cmd.sourceType()) || !SOURCE_TYPES.contains(cmd.sourceType().trim().toUpperCase(Locale.ROOT))) {
            throw new BizException(ErrorCode.FUND_004, "预算来源类型非法");
        }
        if (isBlank(cmd.controlLevel())
                || !CONTROL_LEVELS.contains(cmd.controlLevel().trim().toUpperCase(Locale.ROOT))) {
            throw new BizException(ErrorCode.FUND_004, "预算控制级别非法");
        }
        if (cmd.effectiveFrom() == null || cmd.effectiveTo() == null
                || cmd.effectiveFrom().isAfter(cmd.effectiveTo())) {
            throw new BizException(ErrorCode.FUND_004, "预算生效区间非法");
        }
        if (cmd.lines() == null || cmd.lines().isEmpty()) {
            throw new BizException(ErrorCode.FUND_004, "预算科目明细不能为空");
        }
        if (cmd.totalAmount() == null || cmd.totalAmount().compareTo(BigDecimal.ZERO) <= 0
                || cmd.totalAmount().stripTrailingZeros().scale() > 2) {
            throw new BizException(ErrorCode.FUND_004, "预算总额必须为正数且最多两位小数");
        }
        Set<String> subjects = new HashSet<>();
        BigDecimal sum = BigDecimal.ZERO;
        for (BudgetLineCmd line : cmd.lines()) {
            if (line == null || isBlank(line.subjectCode()) || isBlank(line.subjectName())) {
                throw new BizException(ErrorCode.FUND_004, "科目编码与名称不能为空");
            }
            if (!subjects.add(line.subjectCode().trim())) {
                throw new BizException(ErrorCode.FUND_004, "科目编码重复: " + line.subjectCode().trim());
            }
            if (line.amount() == null || line.amount().compareTo(BigDecimal.ZERO) <= 0
                    || line.amount().stripTrailingZeros().scale() > 2) {
                throw new BizException(ErrorCode.FUND_004, "科目金额必须为正数且最多两位小数");
            }
            sum = sum.add(line.amount());
        }
        if (sum.compareTo(cmd.totalAmount()) != 0) {
            throw new BizException(ErrorCode.FUND_004, "预算明细合计与总额不一致");
        }
        return new ValidatedBudget(cmd.totalAmount(), cmd.lines());
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

    private static BudgetVersionDetail toDetail(BudgetVersionEntity version, List<BudgetLineDetail> lines) {
        return new BudgetVersionDetail(version.getId(), version.getProjectId(), version.getVersionNo(),
                version.getTotalAmount(), version.getStatus(), version.getControlLevel(),
                version.getEffectiveFrom(), version.getEffectiveTo(), List.copyOf(lines));
    }

    private static BudgetVersionSummary toSummary(BudgetVersionEntity version) {
        return new BudgetVersionSummary(version.getId(), version.getVersionNo(), version.getTotalAmount(),
                version.getStatus(), version.getControlLevel(), version.getEffectiveFrom(),
                version.getEffectiveTo());
    }

    private static BudgetLineDetail toLineDetail(BudgetLineEntity line, BudgetBalanceEntity balance) {
        BigDecimal approved = balance == null ? BigDecimal.ZERO : nullToZero(balance.getApprovedAmount());
        BigDecimal reserved = balance == null ? BigDecimal.ZERO : nullToZero(balance.getReservedAmount());
        BigDecimal unpaid = balance == null ? BigDecimal.ZERO : nullToZero(balance.getApprovedUnpaidAmount());
        BigDecimal paid = balance == null ? BigDecimal.ZERO : nullToZero(balance.getPaidAmount());
        BigDecimal available = balance == null ? BigDecimal.ZERO : balance.availableAmount();
        return new BudgetLineDetail(line.getId(), line.getSubjectCode(), line.getSubjectName(),
                line.getAmount(), approved, reserved, unpaid, paid, available);
    }

    private static BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String newCompactId() {
        return UuidUtils.formatCompact(UUID.randomUUID());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record ValidatedBudget(BigDecimal totalAmount, List<BudgetLineCmd> lines) {
    }
}
