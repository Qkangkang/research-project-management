package cn.edu.rpm.funding.application.service;

import cn.edu.rpm.audit.domain.model.AuditEvent;
import cn.edu.rpm.audit.domain.service.AuditRecorder;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.common.util.TraceIds;
import cn.edu.rpm.common.util.UuidUtils;
import cn.edu.rpm.funding.application.dto.PaymentCallbackCmd;
import cn.edu.rpm.funding.application.dto.PaymentCallbackResult;
import cn.edu.rpm.funding.domain.service.FinanceSignatureVerifier;
import cn.edu.rpm.funding.infrastructure.entity.ExpenseRequestEntity;
import cn.edu.rpm.funding.infrastructure.entity.PaymentRecordEntity;
import cn.edu.rpm.funding.infrastructure.entity.ReconciliationDiffEntity;
import cn.edu.rpm.funding.infrastructure.repository.ExpenseRequestRepository;
import cn.edu.rpm.funding.infrastructure.repository.PaymentRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 财务支付回调应用服务（T-FUND-027；技术文档 05 §3.3、08 §11）。
 *
 * <p>先验签，再按 {@code external_event_id} 做报文级幂等、按
 * {@code external_payment_no} 做业务幂等。可入账时写支付记录、追加资金事件并推进
 * 支出状态；无匹配、晚到、超额、金额冲突与缺凭证退款不直接入账，转对账差异。</p>
 */
@Service
public class PaymentCallbackAppService {

    /** 回调事件：全额/部分支付。 */
    public static final String EVENT_PAY = "PAY";

    /** 回调事件：部分支付。 */
    public static final String EVENT_PARTIAL_PAY = "PARTIAL_PAY";

    /** 回调事件：冲正。 */
    public static final String EVENT_REVERSE = "REVERSE";

    /** 回调事件：退款。 */
    public static final String EVENT_REFUND = "REFUND";

    /** 审计动作：支付回调入账。 */
    public static final String AUDIT_PAYMENT_POSTED = "PAYMENT_CALLBACK_POSTED";

    /** 审计动作：支付回调进入差异。 */
    public static final String AUDIT_PAYMENT_DIFF = "PAYMENT_CALLBACK_DIFF";

    /** 审计动作：支付冲正。 */
    public static final String AUDIT_PAYMENT_REVERSED = "PAYMENT_CALLBACK_REVERSED";

    /** 审计动作：支付退款。 */
    public static final String AUDIT_PAYMENT_REFUNDED = "PAYMENT_CALLBACK_REFUNDED";

    /** 审计对象类型：支付记录。 */
    static final String OBJECT_PAYMENT = "PAYMENT_RECORD";

    private static final Set<String> EVENT_TYPES = Set.of(
            EVENT_PAY, EVENT_PARTIAL_PAY, EVENT_REVERSE, EVENT_REFUND);

    private static final Set<String> PAYABLE_STATUSES = Set.of(
            ExpenseRequestEntity.STATUS_APPROVED_UNPAID,
            ExpenseRequestEntity.STATUS_PARTIALLY_PAID);

    private static final Set<String> REFUNDABLE_STATUSES = Set.of(
            ExpenseRequestEntity.STATUS_PARTIALLY_PAID,
            ExpenseRequestEntity.STATUS_PAID);

    private final FinanceSignatureVerifier signatureVerifier;

    private final PaymentRecordRepository paymentRecordRepository;

    private final ExpenseRequestRepository expenseRequestRepository;

    private final PaymentPostingService paymentPostingService;

    private final CallbackDiffRecorder callbackDiffRecorder;

    private final AuditRecorder auditRecorder;

    private final Clock clock;

    /**
     * 构造支付回调服务。
     *
     * @param signatureVerifier       验签器
     * @param paymentRecordRepository 支付记录仓储
     * @param expenseRequestRepository 支出仓储
     * @param paymentPostingService   支付入账服务
     * @param callbackDiffRecorder    回调差异记录器
     * @param auditRecorder           审计记录器
     * @param clock                   时间基准
     */
    public PaymentCallbackAppService(FinanceSignatureVerifier signatureVerifier,
                                     PaymentRecordRepository paymentRecordRepository,
                                     ExpenseRequestRepository expenseRequestRepository,
                                     PaymentPostingService paymentPostingService,
                                     CallbackDiffRecorder callbackDiffRecorder,
                                     AuditRecorder auditRecorder,
                                     Clock clock) {
        this.signatureVerifier = signatureVerifier;
        this.paymentRecordRepository = paymentRecordRepository;
        this.expenseRequestRepository = expenseRequestRepository;
        this.paymentPostingService = paymentPostingService;
        this.callbackDiffRecorder = callbackDiffRecorder;
        this.auditRecorder = auditRecorder;
        this.clock = clock;
    }

    /**
     * 处理支付回调。
     *
     * @param cmd 回调命令（含原始报文与签名）
     * @return 受理结果；差异场景 {@code acceptedWithDiff=true}
     * @throws BizException 签名失败 {@code RPM-INT-002}；事件类型非法 {@code RPM-INT-003}；
     *                      参数非法 {@code RPM-COMMON-400}；币种/金额异常或单号冲突
     *                      {@code RPM-FUND-009}；报文载荷不一致 {@code RPM-INT-004}
     */
    @Transactional
    public PaymentCallbackResult handle(PaymentCallbackCmd cmd) {
        Objects.requireNonNull(cmd, "cmd 不能为空");
        signatureVerifier.verify(cmd.rawBody(), cmd.signature());
        validate(cmd);

        Optional<PaymentRecordEntity> byEvent = paymentRecordRepository
                .findByExternalEventId(cmd.externalEventId());
        if (byEvent.isPresent()) {
            return idempotent(byEvent.get(), cmd);
        }

        Optional<PaymentRecordEntity> byPaymentNo = paymentRecordRepository
                .findByExternalPaymentNo(cmd.externalPaymentNo());
        if (byPaymentNo.isPresent()) {
            PaymentRecordEntity existing = byPaymentNo.get();
            if (existing.getAmount().compareTo(cmd.amount()) != 0) {
                String diffId = recordDiff(ReconciliationDiffEntity.TYPE_AMOUNT_MISMATCH,
                        existing.getProjectId(), existing.getId(), cmd,
                        existing.getAmount());
                throw new BizException(ErrorCode.FUND_009,
                        "同一外部支付单号金额不一致，差异 " + diffId);
            }
            if (EVENT_REVERSE.equals(cmd.eventType())
                    && PaymentRecordEntity.STATUS_APPLIED.equals(existing.getStatus())) {
                return reverse(existing, cmd);
            }
            return idempotent(existing, cmd);
        }

        if (EVENT_REFUND.equals(cmd.eventType()) && isBlank(cmd.voucherNo())) {
            return acceptedWithDiff(recordDiff(ReconciliationDiffEntity.TYPE_STATUS_LAG,
                    null, null, cmd, null), null, null, null);
        }

        Optional<ExpenseRequestEntity> expenseOpt = expenseRequestRepository
                .findByRequestNo(cmd.externalExpenseNo());
        Set<String> allowedStatuses = EVENT_REFUND.equals(cmd.eventType())
                ? REFUNDABLE_STATUSES : PAYABLE_STATUSES;
        if (expenseOpt.isEmpty()
                || !allowedStatuses.contains(expenseOpt.get().getStatus())) {
            ExpenseRequestEntity expense = expenseOpt.orElse(null);
            String diffId = recordDiff(ReconciliationDiffEntity.TYPE_STATUS_LAG,
                    expense == null ? null : expense.getProjectId(), null, cmd, null);
            return acceptedWithDiff(diffId, null,
                    expense == null ? null : expense.getStatus(), expense);
        }

        ExpenseRequestEntity expense = expenseOpt.get();
        if (cmd.amount().compareTo(expense.getAmount()) > 0) {
            String diffId = recordDiff(ReconciliationDiffEntity.TYPE_AMOUNT_MISMATCH,
                    expense.getProjectId(), null, cmd, expense.getAmount());
            return acceptedWithDiff(diffId, null, expense.getStatus(), expense);
        }

        if (EVENT_REVERSE.equals(cmd.eventType())) {
            String diffId = recordDiff(ReconciliationDiffEntity.TYPE_STATUS_LAG,
                    expense.getProjectId(), null, cmd, expense.getAmount());
            return acceptedWithDiff(diffId, null, expense.getStatus(), expense);
        }

        PaymentPostingService.Posting posting = EVENT_REFUND.equals(cmd.eventType())
                ? paymentPostingService.refund(expense, cmd.amount(), cmd.externalPaymentNo(),
                        cmd.rawBody())
                : paymentPostingService.post(expense, cmd.amount(), cmd.externalPaymentNo(),
                        cmd.externalEventId(), cmd.paidAt(), cmd.voucherNo(), cmd.rawBody());
        audit(EVENT_REFUND.equals(cmd.eventType())
                ? AUDIT_PAYMENT_REFUNDED : AUDIT_PAYMENT_POSTED, posting.paymentRecordId(), cmd);
        return new PaymentCallbackResult(true, false, false, posting.paymentRecordId(),
                posting.expenseStatus(), posting.paidTotal(), posting.remainingUnpaid(), null);
    }

    private PaymentCallbackResult reverse(PaymentRecordEntity existing, PaymentCallbackCmd cmd) {
        ExpenseRequestEntity expense = expenseRequestRepository
                .findById(existing.getExpenseRequestId())
                .orElseThrow(() -> new BizException(ErrorCode.FUND_009, "支付记录关联支出不存在"));
        PaymentPostingService.Posting posting = paymentPostingService.reverse(
                expense, existing, cmd.amount(), cmd.paidAt() == null
                        ? OffsetDateTime.now(clock) : cmd.paidAt());
        audit(AUDIT_PAYMENT_REVERSED, existing.getId(), cmd);
        return new PaymentCallbackResult(true, false, false, existing.getId(),
                posting.expenseStatus(), posting.paidTotal(), posting.remainingUnpaid(), null);
    }

    private PaymentCallbackResult idempotent(PaymentRecordEntity existing, PaymentCallbackCmd cmd) {
        if (!Objects.equals(existing.getExternalPaymentNo(), cmd.externalPaymentNo())
                || existing.getAmount().compareTo(cmd.amount()) != 0) {
            throw new BizException(ErrorCode.INT_004, "同一外部事件回调载荷不一致");
        }
        ExpenseRequestEntity expense = expenseRequestRepository
                .findById(existing.getExpenseRequestId()).orElse(null);
        BigDecimal paid = paymentPostingService.paidTotal(existing.getExpenseRequestId());
        BigDecimal remaining = expense == null ? null : expense.getAmount().subtract(paid);
        return new PaymentCallbackResult(true, true, false, existing.getId(),
                expense == null ? null : expense.getStatus(), paid, remaining, null);
    }

    private PaymentCallbackResult acceptedWithDiff(String diffId, String paymentRecordId,
                                                   String expenseStatus,
                                                   ExpenseRequestEntity expense) {
        BigDecimal paid = expense == null ? null
                : paymentPostingService.paidTotal(expense.getId());
        BigDecimal remaining = expense == null ? null : expense.getAmount().subtract(paid);
        return new PaymentCallbackResult(true, false, true, paymentRecordId,
                expenseStatus, paid, remaining, diffId);
    }

    private String recordDiff(String diffType, String projectId, String paymentRecordId,
                              PaymentCallbackCmd cmd, BigDecimal systemAmount) {
        String diffId = callbackDiffRecorder.record(diffType, projectId, paymentRecordId,
                cmd.externalPaymentNo(), systemAmount, cmd.amount());
        audit(AUDIT_PAYMENT_DIFF, paymentRecordId, cmd);
        return diffId;
    }

    private void validate(PaymentCallbackCmd cmd) {
        if (cmd.eventType() == null || !EVENT_TYPES.contains(cmd.eventType())) {
            throw new BizException(ErrorCode.INT_003, "回调事件类型非法");
        }
        if (isBlank(cmd.externalEventId()) || isBlank(cmd.externalPaymentNo())
                || isBlank(cmd.externalExpenseNo())) {
            throw new BizException(ErrorCode.INT_003, "回调关键单号缺失");
        }
        if (cmd.amount() == null || cmd.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(ErrorCode.COMMON_400, "回调金额必须为正数");
        }
        if (!"CNY".equalsIgnoreCase(cmd.currency())) {
            throw new BizException(ErrorCode.FUND_009, "一期仅支持 CNY 币种");
        }
        if (cmd.paidAt() == null) {
            throw new BizException(ErrorCode.INT_003, "支付时间不能为空");
        }
    }

    private void audit(String action, String paymentRecordId, PaymentCallbackCmd cmd) {
        auditRecorder.record(new AuditEvent(TraceIds.currentOrUntracked(), null, null, action,
                OBJECT_PAYMENT, paymentRecordId == null || paymentRecordId.isBlank()
                        ? null : UuidUtils.parse(paymentRecordId),
                null, null, null, cmd == null ? null : cmd.externalEventId(), null, null,
                Instant.now(clock)));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
