package cn.edu.rpm.funding.application.handler;

import cn.edu.rpm.audit.domain.model.AuditEvent;
import cn.edu.rpm.audit.domain.service.AuditRecorder;
import cn.edu.rpm.common.domain.model.OutboxEvent;
import cn.edu.rpm.common.domain.port.OutboxAppendPort;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.common.util.TraceIds;
import cn.edu.rpm.common.util.UuidUtils;
import cn.edu.rpm.funding.domain.model.FundEventCommand;
import cn.edu.rpm.funding.domain.port.ExpenseWorkflowLauncher;
import cn.edu.rpm.funding.domain.service.FundEventAppender;
import cn.edu.rpm.funding.infrastructure.entity.BudgetBalanceEntity;
import cn.edu.rpm.funding.infrastructure.entity.BudgetLineEntity;
import cn.edu.rpm.funding.infrastructure.entity.BudgetVersionEntity;
import cn.edu.rpm.funding.infrastructure.entity.ExpenseRequestEntity;
import cn.edu.rpm.funding.infrastructure.entity.ExpenseSpecialApprovalEntity;
import cn.edu.rpm.funding.infrastructure.entity.FundEventEntity;
import cn.edu.rpm.funding.infrastructure.entity.PaymentRecordEntity;
import cn.edu.rpm.funding.infrastructure.repository.BudgetBalanceRepository;
import cn.edu.rpm.funding.infrastructure.repository.BudgetLineRepository;
import cn.edu.rpm.funding.infrastructure.repository.BudgetVersionRepository;
import cn.edu.rpm.funding.infrastructure.repository.ExpenseRequestRepository;
import cn.edu.rpm.funding.infrastructure.repository.ExpenseSpecialApprovalRepository;
import cn.edu.rpm.funding.infrastructure.repository.PaymentRecordRepository;
import cn.edu.rpm.project.infrastructure.entity.ProjectEntity;
import cn.edu.rpm.project.infrastructure.repository.ProjectRepository;
import cn.edu.rpm.workflow.domain.dto.ApprovalResult;
import cn.edu.rpm.workflow.domain.port.ApprovalResultHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 特批审批回调（T-FUND-025；技术文档 08 §7.1、07 §7.2）。
 *
 * <p>职责分离在服务层二次校验：审批人不得为申请人、支出经办人或项目 PI。
 * 批准时同一事务内生成预算调整草稿版本、追加 {@code ADJUST} 提高批准额、
 * 追加受控 {@code RESERVE}、把支出重新置为审批中并启动支出审批流。
 * 到期任务对未完成支付的批准特批置 {@code EXPIRED} 并释放占用。</p>
 */
@Component
public class SpecialApprovalApprovalHandler implements ApprovalResultHandler {

    /** 工作流业务类型：特批审批。 */
    public static final String BIZ_TYPE = "SPECIAL_APPROVAL";

    /** 工作流业务类型：支出审批。 */
    public static final String BIZ_TYPE_EXPENSE = "EXPENSE";

    /** 审计动作：特批批准。 */
    public static final String AUDIT_APPROVED = "SPECIAL_APPROVAL_APPROVED";

    /** 审计动作：特批拒绝。 */
    public static final String AUDIT_REJECTED = "SPECIAL_APPROVAL_REJECTED";

    /** 审计动作：特批撤回。 */
    public static final String AUDIT_WITHDRAWN = "SPECIAL_APPROVAL_WITHDRAWN";

    /** 审计动作：特批退回（仅记录）。 */
    public static final String AUDIT_RETURNED = "SPECIAL_APPROVAL_RETURNED";

    /** 审计动作：特批过期。 */
    public static final String AUDIT_EXPIRED = "SPECIAL_APPROVAL_EXPIRED";

    /** 审计动作：超上限加签确认。 */
    public static final String AUDIT_COUNTERSIGN = "SPECIAL_APPROVAL_COUNTERSIGN";

    /** 审计动作：审批版本过期。 */
    public static final String AUDIT_STALE_APPROVAL = "STALE_APPROVAL";

    /** 审计对象类型：特批申请。 */
    static final String OBJECT_SPECIAL = "EXPENSE_SPECIAL_APPROVAL";

    private final ExpenseSpecialApprovalRepository specialApprovalRepository;

    private final ExpenseRequestRepository expenseRequestRepository;

    private final PaymentRecordRepository paymentRecordRepository;

    private final ProjectRepository projectRepository;

    private final BudgetVersionRepository budgetVersionRepository;

    private final BudgetLineRepository budgetLineRepository;

    private final BudgetBalanceRepository budgetBalanceRepository;

    private final FundEventAppender fundEventAppender;

    private final ExpenseWorkflowLauncher workflowLauncher;

    private final AuditRecorder auditRecorder;

    private final OutboxAppendPort outboxAppendPort;

    private final Clock clock;

    private final int validDays;

    private final BigDecimal maxAmount;

    /**
     * 构造特批回调处理器。
     *
     * @param specialApprovalRepository 特批仓储
     * @param expenseRequestRepository  支出仓储
     * @param paymentRecordRepository   支付记录仓储（部分支付剩余金额）
     * @param projectRepository         项目仓储
     * @param budgetVersionRepository   预算版本仓储
     * @param budgetLineRepository      预算科目仓储
     * @param budgetBalanceRepository   余额投影仓储
     * @param fundEventAppender         资金事件追加器
     * @param workflowLauncher          审批启动端口（重启支出审批）
     * @param auditRecorder             审计记录器
     * @param outboxAppendPort          Outbox 追加端口
     * @param clock                     时间基准
     * @param validDays                 特批有效期天数
     * @param maxAmount                 单笔特批额度上限；{@code 0} 表示不限制
     */
    public SpecialApprovalApprovalHandler(ExpenseSpecialApprovalRepository specialApprovalRepository,
                                          ExpenseRequestRepository expenseRequestRepository,
                                          PaymentRecordRepository paymentRecordRepository,
                                          ProjectRepository projectRepository,
                                          BudgetVersionRepository budgetVersionRepository,
                                          BudgetLineRepository budgetLineRepository,
                                          BudgetBalanceRepository budgetBalanceRepository,
                                          FundEventAppender fundEventAppender,
                                          ExpenseWorkflowLauncher workflowLauncher,
                                          AuditRecorder auditRecorder,
                                          OutboxAppendPort outboxAppendPort,
                                          Clock clock,
                                          @Value("${rpm.funding.special-approval.valid-days:30}")
                                          int validDays,
                                          @Value("${rpm.funding.special-approval.max-amount:0}")
                                          BigDecimal maxAmount) {
        this.specialApprovalRepository = Objects.requireNonNull(specialApprovalRepository,
                "specialApprovalRepository 不能为空");
        this.expenseRequestRepository = Objects.requireNonNull(expenseRequestRepository,
                "expenseRequestRepository 不能为空");
        this.paymentRecordRepository = Objects.requireNonNull(paymentRecordRepository,
                "paymentRecordRepository 不能为空");
        this.projectRepository = Objects.requireNonNull(projectRepository, "projectRepository 不能为空");
        this.budgetVersionRepository = Objects.requireNonNull(budgetVersionRepository,
                "budgetVersionRepository 不能为空");
        this.budgetLineRepository = Objects.requireNonNull(budgetLineRepository,
                "budgetLineRepository 不能为空");
        this.budgetBalanceRepository = Objects.requireNonNull(budgetBalanceRepository,
                "budgetBalanceRepository 不能为空");
        this.fundEventAppender = Objects.requireNonNull(fundEventAppender, "fundEventAppender 不能为空");
        this.workflowLauncher = Objects.requireNonNull(workflowLauncher, "workflowLauncher 不能为空");
        this.auditRecorder = Objects.requireNonNull(auditRecorder, "auditRecorder 不能为空");
        this.outboxAppendPort = Objects.requireNonNull(outboxAppendPort, "outboxAppendPort 不能为空");
        this.clock = Objects.requireNonNull(clock, "clock 不能为空");
        if (validDays <= 0) {
            throw new IllegalArgumentException("特批有效期天数必须为正数");
        }
        this.validDays = validDays;
        this.maxAmount = maxAmount == null ? BigDecimal.ZERO : maxAmount;
    }

    @Override
    public String bizType() {
        return BIZ_TYPE;
    }

    /**
     * 特批批准（TC-FUND-104、TC-FUND-105）。
     *
     * @param result 审批结果
     * @throws BizException 特批不存在 {@code RPM-COMMON-404}；审批版本过期 {@code RPM-WF-005}；
     *                      违反职责分离 {@code RPM-WF-003}；状态/预算信息不满足 {@code RPM-COMMON-422}
     */
    @Override
    @Transactional
    public void onApproved(ApprovalResult result) {
        ExpenseSpecialApprovalEntity approval = lockAndValidateVersion(result);
        if (ExpenseSpecialApprovalEntity.STATUS_APPROVED.equals(approval.getStatus())) {
            return;
        }
        if (!ExpenseSpecialApprovalEntity.STATUS_PENDING.equals(approval.getStatus())) {
            throw new BizException(ErrorCode.COMMON_422, "特批状态不允许批准");
        }
        ExpenseRequestEntity expense = expenseRequestRepository
                .selectForUpdate(approval.getExpenseRequestId())
                .orElseThrow(() -> new BizException(ErrorCode.COMMON_404, "支出不存在"));
        ProjectEntity project = projectRepository.findById(expense.getProjectId())
                .orElseThrow(() -> new BizException(ErrorCode.PROJ_001));
        checkSeparation(approval, expense, project, result.operatorId());
        requireBudgetLine(expense);

        OffsetDateTime now = OffsetDateTime.now(clock);
        LocalDate validUntil = LocalDate.now(clock).plusDays(validDays);
        String adjustmentVersionId = createAdjustmentDraft(approval, expense, project, result.operatorId());
        appendAdjust(approval, expense, result.operatorId());
        appendReserve(approval, expense, result.operatorId());

        expense.submitForReview(approval.getExpenseVersionId(), approval.getApplicantId(), now);
        if (expenseRequestRepository.update(expense) == 0) {
            throw new BizException(ErrorCode.COMMON_409, "支出已被并发修改，请重试");
        }
        startExpenseWorkflow(approval, expense, result.operatorId());

        approval.approve(UuidUtils.formatCompact(result.operatorId()), now, validUntil,
                adjustmentVersionId);
        if (specialApprovalRepository.update(approval) == 0) {
            throw new BizException(ErrorCode.COMMON_409, "特批已被并发修改，请重试");
        }
        if (maxAmount.compareTo(BigDecimal.ZERO) > 0 && approval.getOverAmount().compareTo(maxAmount) > 0) {
            audit(result.operatorId(), AUDIT_COUNTERSIGN, approval.getId(),
                    "特批金额超过上限，需院领导加签");
        }
        publish(AUDIT_APPROVED, approval, result.operatorId(), "特批批准");
    }

    /**
     * 特批拒绝。
     *
     * @param result 审批结果
     * @throws BizException 特批不存在 {@code RPM-COMMON-404}；审批版本过期 {@code RPM-WF-005}；
     *                      状态不允许 {@code RPM-COMMON-422}
     */
    @Override
    @Transactional
    public void onRejected(ApprovalResult result) {
        ExpenseSpecialApprovalEntity approval = lockAndValidateVersion(result);
        if (ExpenseSpecialApprovalEntity.STATUS_REJECTED.equals(approval.getStatus())) {
            return;
        }
        if (!ExpenseSpecialApprovalEntity.STATUS_PENDING.equals(approval.getStatus())) {
            throw new BizException(ErrorCode.COMMON_422, "特批状态不允许拒绝");
        }
        String reason = result.comment() == null || result.comment().isBlank()
                ? "特批审批拒绝" : result.comment();
        approval.reject(reason);
        if (specialApprovalRepository.update(approval) == 0) {
            throw new BizException(ErrorCode.COMMON_409, "特批已被并发修改，请重试");
        }
        publish(AUDIT_REJECTED, approval, result.operatorId(), reason);
    }

    /**
     * 特批退回：特批不支持补正，仅记录审计，保持 {@code PENDING}。
     *
     * @param result 审批结果
     */
    @Override
    @Transactional
    public void onReturned(ApprovalResult result) {
        ExpenseSpecialApprovalEntity approval = lockAndValidateVersion(result);
        audit(result.operatorId(), AUDIT_RETURNED, approval.getId(), "特批退回，保持待审批");
    }

    /**
     * 特批实例撤回。
     *
     * @param result 审批结果
     */
    @Override
    @Transactional
    public void onWithdrawn(ApprovalResult result) {
        ExpenseSpecialApprovalEntity approval = lockAndValidateVersion(result);
        if (!ExpenseSpecialApprovalEntity.STATUS_PENDING.equals(approval.getStatus())) {
            return;
        }
        approval.cancel();
        if (specialApprovalRepository.update(approval) == 0) {
            throw new BizException(ErrorCode.COMMON_409, "特批已被并发修改，请重试");
        }
        publish(AUDIT_WITHDRAWN, approval, result.operatorId(), "特批申请撤回");
    }

    /**
     * 有效期届满处理（TC-FUND-106；由到期任务调用）。
     *
     * <p>对 {@code valid_until < today} 的批准特批逐笔置 {@code EXPIRED}：
     * 审批中/待补正支出释放占用并取消流程；已批未付取消未付金额；部分支付取消剩余；
     * 已完成支付或冲正的支出不再释放。</p>
     *
     * @param today 业务日期
     * @return 处理的特批数量
     */
    @Transactional
    public int expireDue(LocalDate today) {
        Objects.requireNonNull(today, "today 不能为空");
        List<ExpenseSpecialApprovalEntity> due = specialApprovalRepository
                .findApprovedExpiringBefore(today);
        int expired = 0;
        for (ExpenseSpecialApprovalEntity candidate : due) {
            ExpenseSpecialApprovalEntity approval = specialApprovalRepository
                    .selectForUpdate(candidate.getId()).orElse(null);
            if (approval == null || !ExpenseSpecialApprovalEntity.STATUS_APPROVED.equals(approval.getStatus())) {
                continue;
            }
            expireOne(approval);
            expired++;
        }
        return expired;
    }

    private void expireOne(ExpenseSpecialApprovalEntity approval) {
        ExpenseRequestEntity expense = expenseRequestRepository
                .selectForUpdate(approval.getExpenseRequestId())
                .orElseThrow(() -> new BizException(ErrorCode.COMMON_404, "支出不存在"));
        switch (expense.getStatus()) {
            case ExpenseRequestEntity.STATUS_IN_REVIEW,
                 ExpenseRequestEntity.STATUS_SUPPLEMENT -> {
                appendExpireRelease(approval, expense);
                cancelExpenseWorkflow(approval, expense);
                expense.cancel();
                persistExpense(expense);
            }
            case ExpenseRequestEntity.STATUS_APPROVED_UNPAID -> {
                appendCancel(approval, expense, expense.getAmount());
                expense.cancel();
                persistExpense(expense);
            }
            case ExpenseRequestEntity.STATUS_PARTIALLY_PAID -> {
                BigDecimal remaining = expense.getAmount().subtract(paidAmount(expense.getId()));
                if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                    appendCancel(approval, expense, remaining);
                }
                expense.cancel();
                persistExpense(expense);
            }
            default -> {
                // 已支付/已冲正/已取消：额度已消耗或无需释放。
            }
        }
        approval.expire();
        if (specialApprovalRepository.update(approval) == 0) {
            throw new BizException(ErrorCode.COMMON_409, "特批已被并发修改，请重试");
        }
        UUID actor = parseOrNull(approval.getApproverId());
        publish(AUDIT_EXPIRED, approval, actor, "特批有效期届满，占用已释放");
    }

    private void appendAdjust(ExpenseSpecialApprovalEntity approval, ExpenseRequestEntity expense,
                              UUID operatorId) {
        fundEventAppender.append(new FundEventCommand(
                FundEventEntity.TYPE_ADJUST,
                expense.getProjectId(),
                expense.getId(),
                expense.getBudgetVersionId(),
                expense.getBudgetLineId(),
                approval.getOverAmount(),
                null,
                "special:" + approval.getId() + ":ADJUST",
                null,
                payload("SPECIAL_APPROVAL_ADJUST", approval.getId(), expense.getId()),
                operatorId));
    }

    private void appendReserve(ExpenseSpecialApprovalEntity approval, ExpenseRequestEntity expense,
                               UUID operatorId) {
        fundEventAppender.append(new FundEventCommand(
                FundEventEntity.TYPE_RESERVE,
                expense.getProjectId(),
                expense.getId(),
                expense.getBudgetVersionId(),
                expense.getBudgetLineId(),
                expense.getAmount(),
                expense.getRequestNo(),
                "expense:" + expense.getId() + ":RESERVE",
                null,
                payload("SPECIAL_APPROVAL_RESERVE", approval.getId(), expense.getId()),
                operatorId));
    }

    private void appendExpireRelease(ExpenseSpecialApprovalEntity approval, ExpenseRequestEntity expense) {
        fundEventAppender.append(new FundEventCommand(
                FundEventEntity.TYPE_RELEASE,
                expense.getProjectId(),
                expense.getId(),
                expense.getBudgetVersionId(),
                expense.getBudgetLineId(),
                expense.getAmount(),
                expense.getRequestNo(),
                "special:" + approval.getId() + ":EXPIRE_RELEASE",
                null,
                payload("SPECIAL_APPROVAL_EXPIRE", approval.getId(), expense.getId()),
                parseOrNull(approval.getApproverId())));
    }

    private void appendCancel(ExpenseSpecialApprovalEntity approval, ExpenseRequestEntity expense,
                              BigDecimal amount) {
        fundEventAppender.append(new FundEventCommand(
                FundEventEntity.TYPE_CANCEL,
                expense.getProjectId(),
                expense.getId(),
                expense.getBudgetVersionId(),
                expense.getBudgetLineId(),
                amount,
                expense.getRequestNo(),
                "special:" + approval.getId() + ":EXPIRE_CANCEL",
                null,
                payload("SPECIAL_APPROVAL_EXPIRE", approval.getId(), expense.getId()),
                parseOrNull(approval.getApproverId())));
    }

    private String createAdjustmentDraft(ExpenseSpecialApprovalEntity approval,
                                         ExpenseRequestEntity expense,
                                         ProjectEntity project,
                                         UUID operatorId) {
        BudgetVersionEntity effective = budgetVersionRepository
                .findEffectiveByProjectId(project.getId()).orElse(null);
        if (effective == null) {
            return null;
        }
        List<BudgetLineEntity> effectiveLines = budgetLineRepository
                .findByBudgetVersionId(effective.getId());
        int nextVersionNo = budgetVersionRepository.findByProjectId(project.getId()).stream()
                .map(BudgetVersionEntity::getVersionNo)
                .filter(Objects::nonNull)
                .max(Integer::compareTo).orElse(0) + 1;

        BudgetVersionEntity draft = new BudgetVersionEntity();
        draft.setId(ExpenseAppServiceIds.newId());
        draft.setProjectId(project.getId());
        draft.setVersionNo(nextVersionNo);
        draft.setTotalAmount(orZero(effective.getTotalAmount()).add(approval.getOverAmount()));
        draft.setSourceType(BudgetVersionEntity.SOURCE_ADJUSTMENT);
        draft.setControlLevel(effective.getControlLevel());
        draft.setStatus(BudgetVersionEntity.STATUS_DRAFT);
        draft.setEffectiveFrom(effective.getEffectiveFrom());
        draft.setEffectiveTo(effective.getEffectiveTo());
        draft.setCreatedBy(UuidUtils.formatCompact(operatorId));
        budgetVersionRepository.insert(draft);

        List<BudgetLineEntity> copies = new ArrayList<>();
        for (BudgetLineEntity line : effectiveLines) {
            BudgetLineEntity copy = new BudgetLineEntity();
            copy.setId(ExpenseAppServiceIds.newId());
            copy.setBudgetVersionId(draft.getId());
            copy.setSubjectCode(line.getSubjectCode());
            copy.setSubjectName(line.getSubjectName());
            boolean target = UuidUtils.sameId(line.getId(), expense.getBudgetLineId());
            copy.setAmount(target ? orZero(line.getAmount()).add(approval.getOverAmount()) : line.getAmount());
            copy.setControlLevel(line.getControlLevel());
            copy.setDescription(line.getDescription());
            budgetLineRepository.insert(copy);
            BudgetBalanceEntity balance = new BudgetBalanceEntity();
            balance.setId(ExpenseAppServiceIds.newId());
            balance.setBudgetLineId(copy.getId());
            budgetBalanceRepository.insert(balance);
            copies.add(copy);
        }
        return draft.getId();
    }

    private void startExpenseWorkflow(ExpenseSpecialApprovalEntity approval,
                                      ExpenseRequestEntity expense, UUID operatorId) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("requestNo", expense.getRequestNo());
        variables.put("specialApprovalId", approval.getId());
        variables.put("expenseId", expense.getId());
        workflowLauncher.startExpense(new ExpenseWorkflowLauncher.ExpenseWorkflowStart(
                BIZ_TYPE_EXPENSE,
                UuidUtils.parse(expense.getProjectId()),
                UuidUtils.parse(expense.getId()),
                UuidUtils.parse(approval.getExpenseVersionId()),
                parseOrNull(approval.getApplicantId()),
                expense.getAmount(),
                variables));
    }

    private void cancelExpenseWorkflow(ExpenseSpecialApprovalEntity approval,
                                       ExpenseRequestEntity expense) {
        UUID operator = parseOrNull(approval.getApproverId());
        if (operator == null || expense.getCurrentVersionId() == null
                || expense.getCurrentVersionId().isBlank()) {
            return;
        }
        workflowLauncher.cancel(BIZ_TYPE_EXPENSE, UuidUtils.parse(expense.getId()),
                UuidUtils.parse(expense.getCurrentVersionId()), operator, "特批有效期届满",
                UUID.nameUUIDFromBytes(("special-expire:" + approval.getId())
                        .getBytes(StandardCharsets.UTF_8)));
    }

    private BigDecimal paidAmount(String expenseId) {
        return paymentRecordRepository.findByExpenseRequestId(expenseId).stream()
                .filter(record -> PaymentRecordEntity.STATUS_APPLIED.equals(record.getStatus()))
                .map(PaymentRecordEntity::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void checkSeparation(ExpenseSpecialApprovalEntity approval, ExpenseRequestEntity expense,
                                 ProjectEntity project, UUID operatorId) {
        if (UuidUtils.sameId(approval.getApplicantId(), operatorId)
                || UuidUtils.sameId(expense.getCreatedBy(), operatorId)
                || UuidUtils.sameId(expense.getSubmittedBy(), operatorId)
                || UuidUtils.sameId(project.getPiId(), operatorId)) {
            throw new BizException(ErrorCode.WF_003, "特批审批人不得为申请人、经办人或项目 PI");
        }
    }

    private static void requireBudgetLine(ExpenseRequestEntity expense) {
        if (expense.getBudgetLineId() == null || expense.getBudgetLineId().isBlank()
                || expense.getBudgetVersionId() == null || expense.getBudgetVersionId().isBlank()) {
            throw new BizException(ErrorCode.COMMON_422, "无预算特批需先建立预算科目后审批");
        }
    }

    private ExpenseSpecialApprovalEntity lockAndValidateVersion(ApprovalResult result) {
        String approvalId = UuidUtils.formatCompact(result.bizId());
        ExpenseSpecialApprovalEntity approval = specialApprovalRepository.selectForUpdate(approvalId)
                .orElseThrow(() -> new BizException(ErrorCode.COMMON_404, "特批申请不存在"));
        String versionId = UuidUtils.formatCompact(result.bizVersionId());
        if (!UuidUtils.sameId(approval.getExpenseVersionId(), versionId)) {
            auditNonCritical(result.operatorId(), approvalId, "特批审批版本不是当前版本，拒绝回调");
            throw new BizException(ErrorCode.WF_005, "特批审批版本已过期");
        }
        return approval;
    }

    private void persistExpense(ExpenseRequestEntity expense) {
        if (expenseRequestRepository.update(expense) == 0) {
            throw new BizException(ErrorCode.COMMON_409, "支出已被并发修改，请重试");
        }
    }

    private void publish(String action, ExpenseSpecialApprovalEntity approval, UUID actorId, String reason) {
        audit(actorId, action, approval.getId(), reason);
        outboxAppendPort.append(new OutboxEvent(UUID.randomUUID(), OBJECT_SPECIAL,
                UuidUtils.parse(approval.getId()), action,
                Map.of("approvalId", approval.getId(),
                        "expenseId", approval.getExpenseRequestId(),
                        "status", approval.getStatus()),
                TraceIds.currentOrUntracked(), action + ":" + approval.getId()));
    }

    private void audit(UUID actorId, String action, String objectId, String reason) {
        auditRecorder.record(new AuditEvent(
                TraceIds.currentOrUntracked(), actorId, null, action, OBJECT_SPECIAL,
                UuidUtils.parse(objectId), null, null, null, reason, null, null, clock.instant()));
    }

    private void auditNonCritical(UUID actorId, String objectId, String reason) {
        auditRecorder.recordNonCritical(new AuditEvent(
                TraceIds.currentOrUntracked(), actorId, null, AUDIT_STALE_APPROVAL, OBJECT_SPECIAL,
                UuidUtils.parse(objectId), null, null, null, reason, null, null, clock.instant()));
    }

    private static String payload(String source, String approvalId, String expenseId) {
        return "{\"source\":\"" + source + "\",\"approvalId\":\"" + approvalId
                + "\",\"expenseId\":\"" + expenseId + "\"}";
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static UUID parseOrNull(String value) {
        return value == null || value.isBlank() ? null : UuidUtils.parse(value);
    }

    /** 供本类生成的紧凑 UUID；避免跨服务访问实例方法。 */
    private static final class ExpenseAppServiceIds {
        private ExpenseAppServiceIds() {
        }

        static String newId() {
            return UuidUtils.formatCompact(UUID.randomUUID());
        }
    }
}
