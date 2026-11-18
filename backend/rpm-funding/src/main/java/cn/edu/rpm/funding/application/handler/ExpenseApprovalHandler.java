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
import cn.edu.rpm.funding.domain.service.FundEventAppender;
import cn.edu.rpm.funding.infrastructure.entity.ExpenseRequestEntity;
import cn.edu.rpm.funding.infrastructure.entity.FundEventEntity;
import cn.edu.rpm.funding.infrastructure.entity.PaymentRecordEntity;
import cn.edu.rpm.funding.infrastructure.repository.ExpenseRequestRepository;
import cn.edu.rpm.funding.infrastructure.repository.PaymentRecordRepository;
import cn.edu.rpm.workflow.domain.dto.ApprovalResult;
import cn.edu.rpm.workflow.domain.port.ApprovalResultHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 支出审批回调（T-FUND-022；技术文档 05 §1.2、07 §4.2）。
 *
 * <p>通过：追加 {@code APPROVE} 事件（占用转已批未付），状态 {@code APPROVED_UNPAID}；
 * 拒绝：追加 {@code RELEASE} 事件并置 {@code REJECTED}；退回：置 {@code SUPPLEMENT}，
 * 保留原占用不产生资金事件；撤回：按当前状态释放占用或取消未付金额。
 * 回调先校验 {@code bizVersionId} 仍是支出现行版本，过期记 {@code STALE_APPROVAL} 并抛
 * {@code RPM-WF-005}。</p>
 */
@Component
public class ExpenseApprovalHandler implements ApprovalResultHandler {

    /** 工作流业务类型：支出审批。 */
    public static final String BIZ_TYPE = "EXPENSE";

    /** 审计动作：支出审批通过。 */
    public static final String AUDIT_EXPENSE_APPROVED = "EXPENSE_APPROVED";

    /** 审计动作：支出审批拒绝。 */
    public static final String AUDIT_EXPENSE_REJECTED = "EXPENSE_REJECTED";

    /** 审计动作：支出退回补正。 */
    public static final String AUDIT_EXPENSE_RETURNED = "EXPENSE_RETURNED";

    /** 审计动作：审批撤回。 */
    public static final String AUDIT_EXPENSE_APPROVAL_WITHDRAWN = "EXPENSE_APPROVAL_WITHDRAWN";

    /** 审计动作：审批版本过期。 */
    public static final String AUDIT_STALE_APPROVAL = "STALE_APPROVAL";

    /** 审计对象类型：支出申请。 */
    static final String OBJECT_EXPENSE = "EXPENSE_REQUEST";

    private final ExpenseRequestRepository expenseRequestRepository;

    private final PaymentRecordRepository paymentRecordRepository;

    private final FundEventAppender fundEventAppender;

    private final AuditRecorder auditRecorder;

    private final OutboxAppendPort outboxAppendPort;

    private final Clock clock;

    /**
     * 构造支出审批回调。
     *
     * @param expenseRequestRepository 支出仓储
     * @param paymentRecordRepository  支付记录仓储（部分支付剩余金额）
     * @param fundEventAppender        资金事件追加器
     * @param auditRecorder            审计记录器
     * @param outboxAppendPort         Outbox 追加端口
     * @param clock                    时间基准
     */
    public ExpenseApprovalHandler(ExpenseRequestRepository expenseRequestRepository,
                                  PaymentRecordRepository paymentRecordRepository,
                                  FundEventAppender fundEventAppender,
                                  AuditRecorder auditRecorder,
                                  OutboxAppendPort outboxAppendPort,
                                  Clock clock) {
        this.expenseRequestRepository = Objects.requireNonNull(expenseRequestRepository,
                "expenseRequestRepository 不能为空");
        this.paymentRecordRepository = Objects.requireNonNull(paymentRecordRepository,
                "paymentRecordRepository 不能为空");
        this.fundEventAppender = Objects.requireNonNull(fundEventAppender, "fundEventAppender 不能为空");
        this.auditRecorder = Objects.requireNonNull(auditRecorder, "auditRecorder 不能为空");
        this.outboxAppendPort = Objects.requireNonNull(outboxAppendPort, "outboxAppendPort 不能为空");
        this.clock = Objects.requireNonNull(clock, "clock 不能为空");
    }

    @Override
    public String bizType() {
        return BIZ_TYPE;
    }

    /**
     * 支出审批通过（TC-FUND-028）。
     *
     * @param result 审批结果
     * @throws BizException 支出不存在 {@code RPM-COMMON-404}；审批版本过期 {@code RPM-WF-005}；
     *                      状态不允许 {@code RPM-COMMON-422}
     */
    @Override
    @Transactional
    public void onApproved(ApprovalResult result) {
        ExpenseRequestEntity expense = lockAndValidateVersion(result);
        if (!ExpenseRequestEntity.STATUS_IN_REVIEW.equals(expense.getStatus())) {
            if (isApprovedOrLater(expense.getStatus())) {
                return;
            }
            throw new BizException(ErrorCode.COMMON_422, "支出状态不允许审批通过");
        }
        append(expense, FundEventEntity.TYPE_APPROVE, expense.getAmount(), "EXPENSE_APPROVE",
                result.operatorId());
        expense.approve(OffsetDateTime.now(clock));
        persist(expense);
        publish(AUDIT_EXPENSE_APPROVED, expense, result.operatorId(), "支出审批通过");
    }

    /**
     * 支出审批拒绝（TC-FUND-029）。
     *
     * @param result 审批结果
     * @throws BizException 支出不存在 {@code RPM-COMMON-404}；审批版本过期 {@code RPM-WF-005}；
     *                      状态不允许 {@code RPM-COMMON-422}
     */
    @Override
    @Transactional
    public void onRejected(ApprovalResult result) {
        ExpenseRequestEntity expense = lockAndValidateVersion(result);
        if (ExpenseRequestEntity.STATUS_REJECTED.equals(expense.getStatus())) {
            return;
        }
        if (!ExpenseRequestEntity.STATUS_IN_REVIEW.equals(expense.getStatus())) {
            throw new BizException(ErrorCode.COMMON_422, "支出状态不允许审批拒绝");
        }
        append(expense, FundEventEntity.TYPE_RELEASE, expense.getAmount(), "EXPENSE_REJECT",
                result.operatorId());
        expense.reject();
        persist(expense);
        publish(AUDIT_EXPENSE_REJECTED, expense, result.operatorId(), "支出审批拒绝");
    }

    /**
     * 支出退回补正（TC-FUND-026）。
     *
     * @param result 审批结果
     * @throws BizException 支出不存在 {@code RPM-COMMON-404}；审批版本过期 {@code RPM-WF-005}；
     *                      状态不允许 {@code RPM-COMMON-422}
     */
    @Override
    @Transactional
    public void onReturned(ApprovalResult result) {
        ExpenseRequestEntity expense = lockAndValidateVersion(result);
        if (ExpenseRequestEntity.STATUS_SUPPLEMENT.equals(expense.getStatus())) {
            return;
        }
        if (!ExpenseRequestEntity.STATUS_IN_REVIEW.equals(expense.getStatus())) {
            throw new BizException(ErrorCode.COMMON_422, "支出状态不允许退回补正");
        }
        expense.markSupplement();
        persist(expense);
        publish(AUDIT_EXPENSE_RETURNED, expense, result.operatorId(), "支出退回补正，占用保留");
    }

    /**
     * 审批实例撤回/终止回调。
     *
     * @param result 审批结果
     * @throws BizException 支出不存在 {@code RPM-COMMON-404}；审批版本过期 {@code RPM-WF-005}
     */
    @Override
    @Transactional
    public void onWithdrawn(ApprovalResult result) {
        ExpenseRequestEntity expense = lockAndValidateVersion(result);
        if (ExpenseRequestEntity.STATUS_CANCELLED.equals(expense.getStatus())
                || ExpenseRequestEntity.STATUS_REJECTED.equals(expense.getStatus())
                || ExpenseRequestEntity.STATUS_PAID.equals(expense.getStatus())
                || ExpenseRequestEntity.STATUS_REVERSED.equals(expense.getStatus())) {
            return;
        }
        switch (expense.getStatus()) {
            case ExpenseRequestEntity.STATUS_IN_REVIEW,
                 ExpenseRequestEntity.STATUS_SUPPLEMENT -> append(expense, FundEventEntity.TYPE_RELEASE,
                    expense.getAmount(), "EXPENSE_APPROVAL_WITHDRAWN", result.operatorId());
            case ExpenseRequestEntity.STATUS_APPROVED_UNPAID -> append(expense, FundEventEntity.TYPE_CANCEL,
                    expense.getAmount(), "EXPENSE_APPROVAL_WITHDRAWN", result.operatorId());
            case ExpenseRequestEntity.STATUS_PARTIALLY_PAID -> appendRemainingCancel(expense,
                    result.operatorId());
            default -> {
                // 草稿或终态无占用可释放。
            }
        }
        expense.cancel();
        persist(expense);
        publish(AUDIT_EXPENSE_APPROVAL_WITHDRAWN, expense, result.operatorId(), "审批实例撤回");
    }

    private ExpenseRequestEntity lockAndValidateVersion(ApprovalResult result) {
        String expenseId = UuidUtils.formatCompact(result.bizId());
        ExpenseRequestEntity expense = expenseRequestRepository.selectForUpdate(expenseId)
                .orElseThrow(() -> new BizException(ErrorCode.COMMON_404, "支出不存在"));
        String versionId = UuidUtils.formatCompact(result.bizVersionId());
        if (!UuidUtils.sameId(expense.getCurrentVersionId(), versionId)) {
            auditNonCritical(result.operatorId(), expenseId, "审批版本不是当前版本，拒绝回调");
            throw new BizException(ErrorCode.WF_005, "支出审批版本已过期");
        }
        return expense;
    }

    private void append(ExpenseRequestEntity expense, String eventType, BigDecimal amount,
                        String source, UUID operatorId) {
        String suffix = FundEventEntity.TYPE_RELEASE.equals(eventType) ? "RELEASE" : eventType;
        fundEventAppender.append(new FundEventCommand(
                eventType,
                expense.getProjectId(),
                expense.getId(),
                expense.getBudgetVersionId(),
                expense.getBudgetLineId(),
                amount,
                expense.getRequestNo(),
                "expense:" + expense.getId() + ":" + suffix,
                null,
                "{\"source\":\"" + source + "\",\"expenseId\":\"" + expense.getId() + "\"}",
                operatorId));
    }

    private void appendRemainingCancel(ExpenseRequestEntity expense, UUID operatorId) {
        BigDecimal paid = paymentRecordRepository.findByExpenseRequestId(expense.getId()).stream()
                .filter(record -> PaymentRecordEntity.STATUS_APPLIED.equals(record.getStatus()))
                .map(PaymentRecordEntity::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal remaining = expense.getAmount().subtract(paid);
        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            append(expense, FundEventEntity.TYPE_CANCEL, remaining, "EXPENSE_APPROVAL_WITHDRAWN",
                    operatorId);
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
                Map.of("expenseId", expense.getId(), "status", expense.getStatus()),
                TraceIds.currentOrUntracked(), action + ":" + expense.getId()));
    }

    private void auditNonCritical(UUID actorId, String expenseId, String reason) {
        auditRecorder.recordNonCritical(new AuditEvent(
                TraceIds.currentOrUntracked(), actorId, null, AUDIT_STALE_APPROVAL, OBJECT_EXPENSE,
                UuidUtils.parse(expenseId), null, null, null, reason, null, null, clock.instant()));
    }

    private static boolean isApprovedOrLater(String status) {
        return ExpenseRequestEntity.STATUS_APPROVED_UNPAID.equals(status)
                || ExpenseRequestEntity.STATUS_PARTIALLY_PAID.equals(status)
                || ExpenseRequestEntity.STATUS_PAID.equals(status)
                || ExpenseRequestEntity.STATUS_REVERSED.equals(status);
    }
}
