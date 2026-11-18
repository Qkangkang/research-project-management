package cn.edu.rpm.funding.application.service;

import cn.edu.rpm.audit.domain.service.AuditRecorder;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.funding.application.dto.PaymentCallbackCmd;
import cn.edu.rpm.funding.application.dto.PaymentCallbackResult;
import cn.edu.rpm.funding.domain.model.FundEventCommand;
import cn.edu.rpm.funding.domain.service.FinanceSignatureVerifier;
import cn.edu.rpm.funding.domain.service.FundEventAppender;
import cn.edu.rpm.funding.infrastructure.entity.ExpenseRequestEntity;
import cn.edu.rpm.funding.infrastructure.entity.FundEventEntity;
import cn.edu.rpm.funding.infrastructure.entity.PaymentRecordEntity;
import cn.edu.rpm.funding.infrastructure.entity.ReconciliationDiffEntity;
import cn.edu.rpm.funding.infrastructure.repository.ExpenseRequestRepository;
import cn.edu.rpm.funding.infrastructure.repository.FundEventRepository;
import cn.edu.rpm.funding.infrastructure.repository.PaymentRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PaymentCallbackAppService} 纯单元测试（T-FUND-027）。
 *
 * <p>覆盖 TC-FUND-040～052、TC-FUND-113、TC-FUND-115、TC-FUND-117。</p>
 */
@ExtendWith(MockitoExtension.class)
class PaymentCallbackAppServiceTest {

    private static final String EXPENSE_ID = "22222222222222222222222222222222";
    private static final String EXPENSE_NO = "EXP-2026-0001";
    private static final String PROJECT_ID = "11111111111111111111111111111111";
    private static final String VERSION_ID = "33333333333333333333333333333333";
    private static final String LINE_ID = "44444444444444444444444444444444";
    private static final String SECRET = "test-secret";
    private static final Instant NOW = Instant.parse("2026-11-18T02:00:00Z");
    private static final OffsetDateTime PAID_AT = OffsetDateTime.parse("2026-11-17T09:30:00+08:00");

    @Mock
    private ExpenseRequestRepository expenseRequestRepository;

    @Mock
    private FundEventAppender fundEventAppender;

    @Mock
    private CallbackDiffRecorder callbackDiffRecorder;

    @Mock
    private AuditRecorder auditRecorder;

    private FakePaymentRecordRepository paymentRepository;

    private FakeFundEventRepository fundEventRepository;

    private PaymentCallbackAppService service;

    private ExpenseRequestEntity expense;

    private final List<FundEventCommand> appended = new ArrayList<>();

    @BeforeEach
    void setUp() {
        paymentRepository = new FakePaymentRecordRepository();
        fundEventRepository = new FakeFundEventRepository();
        PaymentPostingService postingService = new PaymentPostingService(paymentRepository,
                fundEventRepository, fundEventAppender, expenseRequestRepository);
        service = new PaymentCallbackAppService(new FinanceSignatureVerifier(SECRET),
                paymentRepository, expenseRequestRepository, postingService,
                callbackDiffRecorder, auditRecorder, Clock.fixed(NOW, ZoneId.of("Asia/Shanghai")));

        expense = new ExpenseRequestEntity();
        expense.setId(EXPENSE_ID);
        expense.setRequestNo(EXPENSE_NO);
        expense.setProjectId(PROJECT_ID);
        expense.setBudgetVersionId(VERSION_ID);
        expense.setBudgetLineId(LINE_ID);
        expense.setAmount(new BigDecimal("80000.00"));
        expense.setStatus(ExpenseRequestEntity.STATUS_APPROVED_UNPAID);
        expense.setVersion(1);

        lenient().when(expenseRequestRepository.findByRequestNo(anyString()))
                .thenReturn(Optional.of(expense));
        lenient().when(expenseRequestRepository.findById(EXPENSE_ID))
                .thenReturn(Optional.of(expense));
        lenient().when(expenseRequestRepository.update(any())).thenReturn(1);
        lenient().when(callbackDiffRecorder.record(anyString(), any(), any(), anyString(),
                any(), any())).thenReturn("diff-0001");
        lenient().when(fundEventAppender.append(any())).thenAnswer(invocation -> {
            FundEventCommand command = invocation.getArgument(0);
            appended.add(command);
            FundEventEntity event = new FundEventEntity();
            event.setId("evt-" + appended.size());
            event.setEventType(command.eventType());
            event.setAmount(command.amount());
            event.setIdempotencyKey(command.idempotencyKey());
            event.setProjectId(command.projectId());
            event.setReversalOfEventId(command.reversalOfEventId());
            fundEventRepository.store(event);
            return event;
        });
    }

    @Test
    void tcFund040FullPaymentMarksPaid() {
        PaymentCallbackResult result = service.handle(cmd("PAY", "PAY-001", "80000.00"));

        assertThat(result.accepted()).isTrue();
        assertThat(result.acceptedWithDiff()).isFalse();
        assertThat(result.expenseStatus()).isEqualTo(ExpenseRequestEntity.STATUS_PAID);
        assertThat(result.paidTotal()).isEqualByComparingTo("80000.00");
        assertThat(result.remainingUnpaid()).isEqualByComparingTo("0.00");
        assertThat(appended).singleElement()
                .satisfies(event -> {
                    assertThat(event.eventType()).isEqualTo(FundEventEntity.TYPE_PAY);
                    assertThat(event.amount()).isEqualByComparingTo("80000.00");
                    assertThat(event.idempotencyKey())
                            .isEqualTo("expense:" + EXPENSE_ID + ":PAY:PAY-001");
                });
        assertThat(paymentRepository.findByExpenseRequestId(EXPENSE_ID)).singleElement()
                .satisfies(record -> assertThat(record.getStatus())
                        .isEqualTo(PaymentRecordEntity.STATUS_APPLIED));
    }

    @Test
    void tcFund041PartialPaymentMarksPartiallyPaid() {
        PaymentCallbackResult result = service.handle(cmd("PARTIAL_PAY", "PAY-002", "30000.00"));

        assertThat(result.expenseStatus()).isEqualTo(ExpenseRequestEntity.STATUS_PARTIALLY_PAID);
        assertThat(result.paidTotal()).isEqualByComparingTo("30000.00");
        assertThat(result.remainingUnpaid()).isEqualByComparingTo("50000.00");
        assertThat(appended).singleElement()
                .satisfies(event -> assertThat(event.eventType())
                        .isEqualTo(FundEventEntity.TYPE_PARTIAL_PAY));
    }

    @Test
    void tcFund042RemainingPaymentCompletesExpense() {
        service.handle(cmd("PARTIAL_PAY", "PAY-003", "30000.00"));
        PaymentCallbackResult result = service.handle(cmd("PAY", "PAY-004", "50000.00"));

        assertThat(result.expenseStatus()).isEqualTo(ExpenseRequestEntity.STATUS_PAID);
        assertThat(result.paidTotal()).isEqualByComparingTo("80000.00");
        assertThat(appended).extracting(FundEventCommand::eventType)
                .containsExactly(FundEventEntity.TYPE_PARTIAL_PAY, FundEventEntity.TYPE_PAY);
    }

    @Test
    void tcFund043RepeatedSameEventThreeTimesIsIdempotent() {
        PaymentCallbackCmd cmd = cmd("PAY", "PAY-005", "30000.00");
        service.handle(cmd);
        service.handle(cmd);
        PaymentCallbackResult third = service.handle(cmd);

        assertThat(third.idempotent()).isTrue();
        assertThat(paymentRepository.findByExpenseRequestId(EXPENSE_ID)).hasSize(1);
        assertThat(appended).hasSize(1);
        assertThat(paymentRepository.findByExpenseRequestId(EXPENSE_ID).get(0).getAmount())
                .isEqualByComparingTo("30000.00");
    }

    @Test
    void tcFund044SamePaymentNoDifferentEventIdReturnsFirstResult() {
        service.handle(cmd("PAY", "PAY-006", "30000.00"));
        PaymentCallbackCmd replay = new PaymentCallbackCmd("raw-2", sign("raw-2"),
                "FIN-EVT-OTHER", "PAY", "PAY-006", EXPENSE_NO, "KY-001",
                new BigDecimal("30000.00"), "CNY", PAID_AT, "PZ-001");

        PaymentCallbackResult result = service.handle(replay);

        assertThat(result.idempotent()).isTrue();
        assertThat(result.paymentRecordId()).isNotBlank();
        assertThat(paymentRepository.findByExpenseRequestId(EXPENSE_ID)).hasSize(1);
        assertThat(appended).hasSize(1);
    }

    @Test
    void tcFund045OverpaymentCreatesAmountMismatchAndKeepsStatus() {
        PaymentCallbackResult result = service.handle(cmd("PAY", "PAY-007", "90000.00"));

        assertThat(result.acceptedWithDiff()).isTrue();
        assertThat(result.diffId()).isEqualTo("diff-0001");
        assertThat(result.expenseStatus()).isEqualTo(ExpenseRequestEntity.STATUS_APPROVED_UNPAID);
        assertThat(paymentRepository.findByExpenseRequestId(EXPENSE_ID)).isEmpty();
        assertThat(appended).isEmpty();
        verify(callbackDiffRecorder).record(eq(ReconciliationDiffEntity.TYPE_AMOUNT_MISMATCH),
                eq(PROJECT_ID), isNull(), eq("PAY-007"), eq(new BigDecimal("80000.00")),
                eq(new BigDecimal("90000.00")));
    }

    @Test
    void tcFund046FullReverseKeepsOriginalRecordAndMarksReversed() {
        service.handle(cmd("PAY", "PAY-008", "80000.00"));
        PaymentCallbackResult result = service.handle(cmd("REVERSE", "PAY-008", "80000.00"));

        assertThat(result.expenseStatus()).isEqualTo(ExpenseRequestEntity.STATUS_REVERSED);
        assertThat(result.paidTotal()).isEqualByComparingTo("0.00");
        assertThat(paymentRepository.findByExpenseRequestId(EXPENSE_ID)).singleElement()
                .satisfies(record -> assertThat(record.getStatus())
                        .isEqualTo(PaymentRecordEntity.STATUS_REVERSED));
        FundEventCommand reversal = appended.get(1);
        assertThat(reversal.eventType()).isEqualTo(FundEventEntity.TYPE_REVERSE);
        assertThat(reversal.reversalOfEventId()).isEqualTo("evt-1");
        assertThat(reversal.amount()).isEqualByComparingTo("80000.00");
    }

    @Test
    void tcFund047PartialThenReverseRestoresUnpaidAndMarksReversed() {
        service.handle(cmd("PARTIAL_PAY", "PAY-009", "30000.00"));
        PaymentCallbackResult result = service.handle(cmd("REVERSE", "PAY-009", "30000.00"));

        assertThat(result.expenseStatus()).isEqualTo(ExpenseRequestEntity.STATUS_REVERSED);
        assertThat(result.paidTotal()).isEqualByComparingTo("0.00");
        assertThat(appended).extracting(FundEventCommand::eventType)
                .containsExactly(FundEventEntity.TYPE_PARTIAL_PAY, FundEventEntity.TYPE_REVERSE);
    }

    @Test
    void tcFund048LatePaymentAfterCancelCreatesStatusLag() {
        expense.setStatus(ExpenseRequestEntity.STATUS_CANCELLED);

        PaymentCallbackResult result = service.handle(cmd("PAY", "PAY-010", "80000.00"));

        assertThat(result.acceptedWithDiff()).isTrue();
        assertThat(result.expenseStatus()).isEqualTo(ExpenseRequestEntity.STATUS_CANCELLED);
        assertThat(paymentRepository.findByExpenseRequestId(EXPENSE_ID)).isEmpty();
        assertThat(appended).isEmpty();
        verify(callbackDiffRecorder).record(eq(ReconciliationDiffEntity.TYPE_STATUS_LAG),
                eq(PROJECT_ID), isNull(), eq("PAY-010"), isNull(),
                eq(new BigDecimal("80000.00")));
    }

    @Test
    void tcFund049BadSignatureCreatesNothing() {
        PaymentCallbackCmd tampered = new PaymentCallbackCmd("raw", "deadbeef", "EVT-1",
                "PAY", "PAY-011", EXPENSE_NO, "KY-001", new BigDecimal("80000.00"),
                "CNY", PAID_AT, "PZ-001");

        assertThatThrownBy(() -> service.handle(tampered))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INT_002);
        assertThat(paymentRepository.findByExpenseRequestId(EXPENSE_ID)).isEmpty();
        assertThat(appended).isEmpty();
    }

    @Test
    void tcFund050UsdCurrencyThrowsFund009WithoutRecord() {
        PaymentCallbackCmd usd = new PaymentCallbackCmd("raw", sign("raw"), "EVT-USD",
                "PAY", "PAY-012", EXPENSE_NO, "KY-001", new BigDecimal("80000.00"),
                "USD", PAID_AT, "PZ-001");

        assertThatThrownBy(() -> service.handle(usd))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FUND_009);
        assertThat(paymentRepository.findByExpenseRequestId(EXPENSE_ID)).isEmpty();
        assertThat(appended).isEmpty();
    }

    @Test
    void tcFund051UnknownExpenseAcceptedWithDiff() {
        when(expenseRequestRepository.findByRequestNo(EXPENSE_NO)).thenReturn(Optional.empty());

        PaymentCallbackResult result = service.handle(cmd("PAY", "PAY-013", "80000.00"));

        assertThat(result.accepted()).isTrue();
        assertThat(result.acceptedWithDiff()).isTrue();
        assertThat(result.diffId()).isEqualTo("diff-0001");
        assertThat(appended).isEmpty();
        verify(callbackDiffRecorder).record(eq(ReconciliationDiffEntity.TYPE_STATUS_LAG),
                isNull(), isNull(), eq("PAY-013"), isNull(), eq(new BigDecimal("80000.00")));
    }

    @Test
    void tcFund052SamePaymentNoDifferentAmountKeepsOriginalAndDiff() {
        service.handle(cmd("PARTIAL_PAY", "PAY-014", "30000.00"));

        assertThatThrownBy(() -> service.handle(cmd("PAY", "PAY-014", "40000.00")))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FUND_009);
        assertThat(paymentRepository.findByExternalPaymentNo("PAY-014"))
                .get().extracting(PaymentRecordEntity::getAmount)
                .isEqualTo(new BigDecimal("30000.00"));
        verify(callbackDiffRecorder).record(eq(ReconciliationDiffEntity.TYPE_AMOUNT_MISMATCH),
                eq(PROJECT_ID), any(), eq("PAY-014"), eq(new BigDecimal("30000.00")),
                eq(new BigDecimal("40000.00")));
    }

    @Test
    void tcFund113RefundWithoutVoucherGoesToDiff() {
        PaymentCallbackCmd refund = new PaymentCallbackCmd("raw", sign("raw"), "EVT-REFUND",
                "REFUND", "PAY-015", EXPENSE_NO, "KY-001", new BigDecimal("10000.00"),
                "CNY", PAID_AT, null);

        PaymentCallbackResult result = service.handle(refund);

        assertThat(result.acceptedWithDiff()).isTrue();
        assertThat(appended).isEmpty();
        verify(callbackDiffRecorder).record(eq(ReconciliationDiffEntity.TYPE_STATUS_LAG),
                isNull(), isNull(), eq("PAY-015"), isNull(), eq(new BigDecimal("10000.00")));
    }

    @Test
    void tcFund115DuplicateEventAfterConcurrentLossReturnsFirstResult() {
        PaymentCallbackCmd cmd = cmd("PAY", "PAY-016", "80000.00");
        service.handle(cmd);
        PaymentCallbackResult replay = service.handle(cmd);

        assertThat(replay.idempotent()).isTrue();
        assertThat(paymentRepository.findByExpenseRequestId(EXPENSE_ID)).hasSize(1);
        assertThat(appended).hasSize(1);
    }

    @Test
    void tcFund117SameEventDifferentPayloadThrowsInt004() {
        PaymentCallbackCmd first = cmd("PAY", "PAY-017", "30000.00");
        service.handle(first);
        PaymentCallbackCmd conflicting = new PaymentCallbackCmd("raw-other", sign("raw-other"),
                first.externalEventId(), "PAY", "PAY-017", EXPENSE_NO, "KY-001",
                new BigDecimal("40000.00"), "CNY", PAID_AT, "PZ-001");

        assertThatThrownBy(() -> service.handle(conflicting))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INT_004);
        assertThat(paymentRepository.findByExternalPaymentNo("PAY-017"))
                .get().extracting(PaymentRecordEntity::getAmount)
                .isEqualTo(new BigDecimal("30000.00"));
    }

    @Test
    void invalidEventTypeThrowsInt003WithoutRecord() {
        PaymentCallbackCmd invalid = new PaymentCallbackCmd("raw", sign("raw"), "EVT-X",
                "CHARGEBACK", "PAY-018", EXPENSE_NO, "KY-001", new BigDecimal("1.00"),
                "CNY", PAID_AT, "PZ-001");

        assertThatThrownBy(() -> service.handle(invalid))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INT_003);
        verify(fundEventAppender, never()).append(any());
    }

    private static PaymentCallbackCmd cmd(String type, String paymentNo, String amount) {
        String raw = paymentNo + ":" + type + ":" + amount;
        return new PaymentCallbackCmd(raw, sign(raw), "EVT-" + paymentNo + "-" + type,
                type, paymentNo, EXPENSE_NO, "KY-001", new BigDecimal(amount),
                "CNY", PAID_AT, "PZ-001");
    }

    private static String sign(String raw) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** 内存支付记录仓储（保留真实幂等/累计金额语义）。 */
    private static final class FakePaymentRecordRepository implements PaymentRecordRepository {

        private final Map<String, PaymentRecordEntity> records = new LinkedHashMap<>();

        @Override
        public void insert(PaymentRecordEntity payment) {
            records.put(payment.getId(), payment);
        }

        @Override
        public Optional<PaymentRecordEntity> findById(String id) {
            return Optional.ofNullable(records.get(id));
        }

        @Override
        public Optional<PaymentRecordEntity> findByExternalPaymentNo(String externalPaymentNo) {
            return records.values().stream()
                    .filter(record -> externalPaymentNo.equals(record.getExternalPaymentNo()))
                    .findFirst();
        }

        @Override
        public Optional<PaymentRecordEntity> findByExternalEventId(String externalEventId) {
            return records.values().stream()
                    .filter(record -> externalEventId != null
                            && externalEventId.equals(record.getExternalEventId()))
                    .findFirst();
        }

        @Override
        public List<PaymentRecordEntity> findByPaidAtBetween(java.time.OffsetDateTime from,
                                                             java.time.OffsetDateTime to) {
            return records.values().stream()
                    .filter(record -> !record.getPaidAt().isBefore(from)
                            && record.getPaidAt().isBefore(to))
                    .toList();
        }

        @Override
        public List<PaymentRecordEntity> findByExpenseRequestId(String expenseRequestId) {
            return records.values().stream()
                    .filter(record -> expenseRequestId.equals(record.getExpenseRequestId()))
                    .toList();
        }

        @Override
        public int update(PaymentRecordEntity payment) {
            if (!records.containsKey(payment.getId())) {
                return 0;
            }
            records.put(payment.getId(), payment);
            return 1;
        }
    }

    /** 内存资金事件仓储。 */
    private static final class FakeFundEventRepository implements FundEventRepository {

        private final Map<String, FundEventEntity> events = new LinkedHashMap<>();

        void store(FundEventEntity event) {
            events.put(event.getIdempotencyKey(), event);
        }

        @Override
        public void insert(FundEventEntity event) {
            store(event);
        }

        @Override
        public Optional<FundEventEntity> findById(String id) {
            return events.values().stream()
                    .filter(event -> id.equals(event.getId())).findFirst();
        }

        @Override
        public Optional<FundEventEntity> findByIdempotencyKey(String idempotencyKey) {
            return Optional.ofNullable(events.get(idempotencyKey));
        }

        @Override
        public List<FundEventEntity> findByProjectIdAndEventType(String projectId,
                                                                 String eventType) {
            return events.values().stream()
                    .filter(event -> projectId.equals(event.getProjectId())
                            && eventType.equals(event.getEventType()))
                    .toList();
        }

        @Override
        public List<FundEventEntity> findByProjectId(String projectId) {
            return events.values().stream()
                    .filter(event -> projectId.equals(event.getProjectId())).toList();
        }
    }
}
