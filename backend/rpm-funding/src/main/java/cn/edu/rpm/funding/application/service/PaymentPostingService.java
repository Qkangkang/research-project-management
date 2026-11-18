package cn.edu.rpm.funding.application.service;

import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.common.util.UuidUtils;
import cn.edu.rpm.funding.domain.model.FundEventCommand;
import cn.edu.rpm.funding.domain.service.FundEventAppender;
import cn.edu.rpm.funding.infrastructure.entity.ExpenseRequestEntity;
import cn.edu.rpm.funding.infrastructure.entity.FundEventEntity;
import cn.edu.rpm.funding.infrastructure.entity.PaymentRecordEntity;
import cn.edu.rpm.funding.infrastructure.repository.ExpenseRequestRepository;
import cn.edu.rpm.funding.infrastructure.repository.FundEventRepository;
import cn.edu.rpm.funding.infrastructure.repository.PaymentRecordRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 支付入账服务（T-FUND-027 / T-FUND-028；技术文档 05 §3.3、08 §10）。
 *
 * <p>统一支付回调与支付导入的入账口径：写 {@code payment_record}、追加
 * {@code PAY}/{@code PARTIAL_PAY} 资金事件、推进支出状态。金额判定按累计已付，
 * 不信任回调声明的 {@code PARTIAL_PAY} 类型。冲正按原支付事件反向追加
 * {@code REVERSE}，退款追加 {@code REFUND}。</p>
 */
@Service
public class PaymentPostingService {

    private final PaymentRecordRepository paymentRecordRepository;

    private final FundEventRepository fundEventRepository;

    private final FundEventAppender fundEventAppender;

    private final ExpenseRequestRepository expenseRequestRepository;

    /**
     * 构造支付入账服务。
     *
     * @param paymentRecordRepository 支付记录仓储
     * @param fundEventRepository     资金事件仓储
     * @param fundEventAppender       资金事件追加器
     * @param expenseRequestRepository 支出仓储
     */
    public PaymentPostingService(PaymentRecordRepository paymentRecordRepository,
                                 FundEventRepository fundEventRepository,
                                 FundEventAppender fundEventAppender,
                                 ExpenseRequestRepository expenseRequestRepository) {
        this.paymentRecordRepository = paymentRecordRepository;
        this.fundEventRepository = fundEventRepository;
        this.fundEventAppender = fundEventAppender;
        this.expenseRequestRepository = expenseRequestRepository;
    }

    /**
     * 支付入账（全额或部分由累计金额判定）。
     *
     * @param expense           支出
     * @param amount            本次金额
     * @param externalPaymentNo 外部支付单号
     * @param externalEventId   外部事件 ID
     * @param paidAt            支付时间
     * @param voucherNo         凭证号
     * @param rawPayload        原始报文
     * @return 入账结果
     * @throws BizException 状态不允许 {@code RPM-FUND-005}；超额 {@code RPM-FUND-009}；
     *                      并发版本冲突 {@code RPM-COMMON-409}
     */
    @org.springframework.transaction.annotation.Transactional
    public Posting post(ExpenseRequestEntity expense, BigDecimal amount,
                        String externalPaymentNo, String externalEventId,
                        OffsetDateTime paidAt, String voucherNo, String rawPayload) {
        requirePayable(expense);
        BigDecimal paidBefore = paidTotal(expense.getId());
        BigDecimal paidAfter = paidBefore.add(amount);
        if (paidAfter.compareTo(expense.getAmount()) > 0) {
            throw new BizException(ErrorCode.FUND_009, "支付金额超过申请金额");
        }
        boolean full = paidAfter.compareTo(expense.getAmount()) == 0;
        String eventType = full ? FundEventEntity.TYPE_PAY : FundEventEntity.TYPE_PARTIAL_PAY;

        PaymentRecordEntity payment = new PaymentRecordEntity();
        payment.setId(UuidUtils.formatCompact(UUID.randomUUID()));
        payment.setProjectId(expense.getProjectId());
        payment.setExpenseRequestId(expense.getId());
        payment.setExternalPaymentNo(externalPaymentNo);
        payment.setExternalEventId(externalEventId);
        payment.setAmount(amount);
        payment.setCurrency("CNY");
        payment.setPaidAt(paidAt);
        payment.setVoucherNo(voucherNo);
        payment.setStatus(PaymentRecordEntity.STATUS_RECEIVED);
        payment.setRawPayload(rawPayload == null ? "{}" : rawPayload);
        payment.markApplied();
        paymentRecordRepository.insert(payment);

        FundEventEntity event = fundEventAppender.append(new FundEventCommand(
                eventType, expense.getProjectId(), expense.getId(),
                expense.getBudgetVersionId(), expense.getBudgetLineId(), amount,
                externalPaymentNo, paymentKey(expense.getId(), eventType, externalPaymentNo),
                null, rawPayload, null));

        if (full) {
            expense.markPaid(paidAt);
        } else {
            expense.markPartiallyPaid();
        }
        if (expenseRequestRepository.update(expense) == 0) {
            throw new BizException(ErrorCode.COMMON_409, "支出并发更新冲突");
        }
        return new Posting(payment.getId(), expense.getStatus(), event.getId(),
                paidAfter, expense.getAmount().subtract(paidAfter));
    }

    /**
     * 冲正原支付（全额冲正，原支付记录保留并置 {@code REVERSED}）。
     *
     * @param expense 支出
     * @param payment 原支付记录
     * @param amount  冲正金额（必须等于原支付金额）
     * @param at      冲正时间
     * @return 冲正结果
     * @throws BizException 原事件缺失或金额不符 {@code RPM-FUND-009}；
     *                      并发版本冲突 {@code RPM-COMMON-409}
     */
    @org.springframework.transaction.annotation.Transactional
    public Posting reverse(ExpenseRequestEntity expense, PaymentRecordEntity payment,
                           BigDecimal amount, OffsetDateTime at) {
        Optional<FundEventEntity> original = findOriginalPaymentEvent(expense.getId(),
                payment.getExternalPaymentNo());
        FundEventEntity event = original.orElseThrow(() ->
                new BizException(ErrorCode.FUND_009, "外部支付单号无原支付事件"));
        if (event.getAmount().compareTo(amount) != 0) {
            throw new BizException(ErrorCode.FUND_009, "冲正金额必须等于原支付金额");
        }
        BigDecimal paidAfter = paidTotal(expense.getId()).subtract(amount);
        if (paidAfter.compareTo(BigDecimal.ZERO) < 0) {
            throw new BizException(ErrorCode.FUND_009, "冲正金额超过累计已支付");
        }

        FundEventEntity reverseEvent = fundEventAppender.append(new FundEventCommand(
                FundEventEntity.TYPE_REVERSE, expense.getProjectId(), expense.getId(),
                expense.getBudgetVersionId(), expense.getBudgetLineId(), amount,
                payment.getExternalPaymentNo(),
                "expense:" + expense.getId() + ":REVERSE:" + payment.getExternalPaymentNo(),
                event.getId(), payment.getRawPayload(), null));

        payment.markReversed();
        if (paymentRecordRepository.update(payment) == 0) {
            throw new BizException(ErrorCode.COMMON_409, "支付记录并发更新冲突");
        }
        if (paidAfter.compareTo(BigDecimal.ZERO) == 0) {
            expense.reverse();
        } else if (paidAfter.compareTo(expense.getAmount()) < 0) {
            expense.markPartiallyPaidAfterReversal();
        } else {
            expense.markPaid(at);
        }
        if (expenseRequestRepository.update(expense) == 0) {
            throw new BizException(ErrorCode.COMMON_409, "支出并发更新冲突");
        }
        return new Posting(payment.getId(), expense.getStatus(), reverseEvent.getId(),
                paidAfter, expense.getAmount().subtract(paidAfter));
    }

    /**
     * 退款入账（外部真实资金回流，追加 {@code REFUND}）。
     *
     * @param expense   支出
     * @param amount    退款金额
     * @param externalNo 退款单号
     * @param rawPayload 原始报文
     * @return 退款结果
     * @throws BizException 退款超过累计已付 {@code RPM-FUND-009}
     */
    @org.springframework.transaction.annotation.Transactional
    public Posting refund(ExpenseRequestEntity expense, BigDecimal amount,
                          String externalNo, String rawPayload) {
        BigDecimal paidBefore = paidTotal(expense.getId());
        if (amount.compareTo(paidBefore) > 0) {
            throw new BizException(ErrorCode.FUND_009, "退款金额超过累计已支付");
        }
        FundEventEntity event = fundEventAppender.append(new FundEventCommand(
                FundEventEntity.TYPE_REFUND, expense.getProjectId(), expense.getId(),
                expense.getBudgetVersionId(), expense.getBudgetLineId(), amount,
                externalNo, "expense:" + expense.getId() + ":REFUND:" + externalNo,
                null, rawPayload, null));
        BigDecimal paidAfter = paidBefore.subtract(amount);
        return new Posting(null, expense.getStatus(), event.getId(),
                paidAfter, expense.getAmount().subtract(paidAfter));
    }

    /**
     * 累计已支付（仅统计 {@code APPLIED} 支付记录）。
     *
     * @param expenseId 支出 ID
     * @return 累计已支付
     */
    public BigDecimal paidTotal(String expenseId) {
        List<PaymentRecordEntity> records = paymentRecordRepository.findByExpenseRequestId(expenseId);
        return records.stream()
                .filter(record -> PaymentRecordEntity.STATUS_APPLIED.equals(record.getStatus()))
                .map(PaymentRecordEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static void requirePayable(ExpenseRequestEntity expense) {
        if (!ExpenseRequestEntity.STATUS_APPROVED_UNPAID.equals(expense.getStatus())
                && !ExpenseRequestEntity.STATUS_PARTIALLY_PAID.equals(expense.getStatus())) {
            throw new BizException(ErrorCode.FUND_005, "支出状态不允许支付入账");
        }
    }

    private Optional<FundEventEntity> findOriginalPaymentEvent(String expenseId, String externalNo) {
        Optional<FundEventEntity> pay = fundEventRepository.findByIdempotencyKey(
                paymentKey(expenseId, FundEventEntity.TYPE_PAY, externalNo));
        return pay.isPresent() ? pay : fundEventRepository.findByIdempotencyKey(
                paymentKey(expenseId, FundEventEntity.TYPE_PARTIAL_PAY, externalNo));
    }

    private static String paymentKey(String expenseId, String eventType, String externalNo) {
        return "expense:" + expenseId + ":" + eventType + ":" + externalNo;
    }

    /**
     * 入账结果。
     *
     * @param paymentRecordId 支付记录 ID（退款可空）
     * @param expenseStatus   支出状态
     * @param fundEventId     资金事件 ID
     * @param paidTotal       累计已支付
     * @param remainingUnpaid 剩余未付
     */
    public record Posting(String paymentRecordId, String expenseStatus, String fundEventId,
                          BigDecimal paidTotal, BigDecimal remainingUnpaid) {
    }
}
