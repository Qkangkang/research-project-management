package cn.edu.rpm.funding.application.service;

import cn.edu.rpm.audit.domain.service.AuditRecorder;
import cn.edu.rpm.common.domain.service.NumberGenerator;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.funding.application.dto.FinancePaymentEntry;
import cn.edu.rpm.funding.application.dto.ReconciliationBatchView;
import cn.edu.rpm.funding.application.dto.ReconciliationRunCmd;
import cn.edu.rpm.funding.domain.model.FundingPermissions;
import cn.edu.rpm.funding.infrastructure.entity.ExpenseRequestEntity;
import cn.edu.rpm.funding.infrastructure.entity.PaymentRecordEntity;
import cn.edu.rpm.funding.infrastructure.entity.ReconciliationBatchEntity;
import cn.edu.rpm.funding.infrastructure.entity.ReconciliationDiffEntity;
import cn.edu.rpm.funding.infrastructure.repository.ExpenseRequestRepository;
import cn.edu.rpm.funding.infrastructure.repository.PaymentRecordRepository;
import cn.edu.rpm.funding.infrastructure.repository.ReconciliationBatchRepository;
import cn.edu.rpm.funding.infrastructure.repository.ReconciliationDiffRepository;
import cn.edu.rpm.iam.application.dto.AuthContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ReconciliationAppService} 纯单元测试（T-FUND-029；TC-FUND-059～063、067）。
 */
@ExtendWith(MockitoExtension.class)
class ReconciliationAppServiceTest {

    private static final UUID ACTOR_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final Instant NOW = Instant.parse("2026-11-18T02:00:00Z");
    private static final LocalDate START = LocalDate.of(2026, 11, 1);
    private static final LocalDate END = LocalDate.of(2026, 11, 30);

    @Mock
    private ReconciliationBatchRepository batchRepository;

    @Mock
    private ReconciliationDiffRepository diffRepository;

    @Mock
    private PaymentRecordRepository paymentRecordRepository;

    @Mock
    private ExpenseRequestRepository expenseRequestRepository;

    @Mock
    private NumberGenerator numberGenerator;

    @Mock
    private AuditRecorder auditRecorder;

    private ReconciliationAppService service;

    private AuthContext auth;

    @BeforeEach
    void setUp() {
        service = new ReconciliationAppService(batchRepository, diffRepository,
                paymentRecordRepository, expenseRequestRepository, numberGenerator,
                auditRecorder, new ObjectMapper(), Clock.fixed(NOW, ZoneId.of("Asia/Shanghai")));
        auth = new AuthContext(ACTOR_ID, null, "EMP-001",
                Set.of(FundingPermissions.RECONCILE_RUN, FundingPermissions.RECONCILE_VIEW),
                Set.of(), NOW);
        lenient().when(numberGenerator.next(
                ReconciliationAppService.RULE_RECONCILIATION_BATCH_NO))
                .thenReturn("REC-2026-001");
        lenient().when(batchRepository.update(any())).thenReturn(1);
    }

    @Test
    void tcFund059ConsistentTwoHundredPaymentsHasNoDiff() {
        List<FinancePaymentEntry> finance = new ArrayList<>();
        List<PaymentRecordEntity> system = new ArrayList<>();
        for (int i = 1; i <= 200; i++) {
            String no = String.format("PAY-%03d", i);
            finance.add(new FinancePaymentEntry(no, "EXP-" + i, new BigDecimal("1000.00")));
            system.add(payment(no, "1000.00", "expense-paid"));
        }
        when(batchRepository.findByPeriod(START, END)).thenReturn(List.of());
        when(paymentRecordRepository.findByPaidAtBetween(any(), any())).thenReturn(system);
        when(expenseRequestRepository.findById(anyString()))
                .thenReturn(Optional.of(expense(ExpenseRequestEntity.STATUS_PAID)));
        ArgumentCaptor<ReconciliationBatchEntity> captor =
                ArgumentCaptor.forClass(ReconciliationBatchEntity.class);

        ReconciliationBatchView view = service.run(cmd(finance), auth);

        verify(batchRepository).insert(captor.capture());
        assertThat(captor.getValue().getStatus())
                .isEqualTo(ReconciliationBatchEntity.STATUS_COMPLETED);
        assertThat(view.diffCount()).isZero();
        assertThat(view.diffs()).isEmpty();
        assertThat(view.totalFinanceAmount()).isEqualByComparingTo("200000.00");
        assertThat(view.totalSystemAmount()).isEqualByComparingTo("200000.00");
        verify(diffRepository, never()).insert(any());
    }

    @Test
    void tcFund060FinanceOnlyPaymentCreatesMissingPayment() {
        when(batchRepository.findByPeriod(START, END)).thenReturn(List.of());
        when(paymentRecordRepository.findByPaidAtBetween(any(), any())).thenReturn(List.of());

        ReconciliationBatchView view = service.run(cmd(List.of(
                new FinancePaymentEntry("PAY-ONLY", "EXP-1", new BigDecimal("80000.00")))), auth);

        assertThat(view.diffCount()).isEqualTo(1);
        assertThat(view.diffs()).singleElement().satisfies(diff -> {
            assertThat(diff.diffType())
                    .isEqualTo(ReconciliationDiffEntity.TYPE_MISSING_PAYMENT);
            assertThat(diff.externalPaymentNo()).isEqualTo("PAY-ONLY");
            assertThat(diff.financeAmount()).isEqualByComparingTo("80000.00");
        });
    }

    @Test
    void tcFund061AmountMismatchRecordsBothAmounts() {
        when(batchRepository.findByPeriod(START, END)).thenReturn(List.of());
        when(paymentRecordRepository.findByPaidAtBetween(any(), any()))
                .thenReturn(List.of(payment("PAY-1", "70000.00", "expense-paid")));

        ReconciliationBatchView view = service.run(cmd(List.of(
                new FinancePaymentEntry("PAY-1", "EXP-1", new BigDecimal("80000.00")))), auth);

        assertThat(view.diffs()).singleElement().satisfies(diff -> {
            assertThat(diff.diffType())
                    .isEqualTo(ReconciliationDiffEntity.TYPE_AMOUNT_MISMATCH);
            assertThat(diff.systemAmount()).isEqualByComparingTo("70000.00");
            assertThat(diff.financeAmount()).isEqualByComparingTo("80000.00");
        });
    }

    @Test
    void tcFund062DuplicateFinancePaymentNoCreatesSingleDuplicateDiff() {
        when(batchRepository.findByPeriod(START, END)).thenReturn(List.of());
        when(paymentRecordRepository.findByPaidAtBetween(any(), any())).thenReturn(List.of());

        ReconciliationBatchView view = service.run(cmd(List.of(
                new FinancePaymentEntry("PAY-DUP", "EXP-1", new BigDecimal("1000.00")),
                new FinancePaymentEntry("PAY-DUP", "EXP-1", new BigDecimal("1000.00")))), auth);

        assertThat(view.diffs()).singleElement()
                .satisfies(diff -> assertThat(diff.diffType())
                        .isEqualTo(ReconciliationDiffEntity.TYPE_DUPLICATE));
        verify(diffRepository, times(1)).insert(any());
    }

    @Test
    void tcFund063ApprovedUnpaidSystemExpenseCreatesStatusLag() {
        when(batchRepository.findByPeriod(START, END)).thenReturn(List.of());
        when(paymentRecordRepository.findByPaidAtBetween(any(), any()))
                .thenReturn(List.of(payment("PAY-LAG", "80000.00", "expense-unpaid")));
        when(expenseRequestRepository.findById("expense-unpaid"))
                .thenReturn(Optional.of(expense(ExpenseRequestEntity.STATUS_APPROVED_UNPAID)));

        ReconciliationBatchView view = service.run(cmd(List.of(
                new FinancePaymentEntry("PAY-LAG", "EXP-1", new BigDecimal("80000.00")))), auth);

        assertThat(view.diffs()).singleElement().satisfies(diff -> assertThat(diff.diffType())
                .isEqualTo(ReconciliationDiffEntity.TYPE_STATUS_LAG));
    }

    @Test
    void tcFund067SamePeriodExistingBatchThrows409() {
        ReconciliationBatchEntity existing = new ReconciliationBatchEntity();
        existing.setSourceBatchNo("FIN-BATCH-202611");
        when(batchRepository.findByPeriod(START, END)).thenReturn(List.of(existing));

        assertThatThrownBy(() -> service.run(cmd(List.of()), auth))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.COMMON_409);
        verify(batchRepository, never()).insert(any());
    }

    @Test
    void runWithoutPermissionThrows403() {
        AuthContext noPermission = new AuthContext(ACTOR_ID, null, "EMP-002",
                Set.of(FundingPermissions.RECONCILE_VIEW), Set.of(), NOW);

        assertThatThrownBy(() -> service.run(cmd(List.of()), noPermission))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.COMMON_403);
    }

    private static ReconciliationRunCmd cmd(List<FinancePaymentEntry> rows) {
        return new ReconciliationRunCmd(START, END, "FIN-BATCH-202611", rows);
    }

    private static PaymentRecordEntity payment(String no, String amount, String expenseId) {
        PaymentRecordEntity payment = new PaymentRecordEntity();
        payment.setId("payment-" + no);
        payment.setExternalPaymentNo(no);
        payment.setAmount(new BigDecimal(amount));
        payment.setStatus(PaymentRecordEntity.STATUS_APPLIED);
        payment.setExpenseRequestId(expenseId);
        payment.setProjectId("11111111111111111111111111111111");
        payment.setPaidAt(OffsetDateTime.parse("2026-11-15T10:00:00+08:00"));
        return payment;
    }

    private static ExpenseRequestEntity expense(String status) {
        ExpenseRequestEntity expense = new ExpenseRequestEntity();
        expense.setId("expense-x");
        expense.setStatus(status);
        return expense;
    }
}
