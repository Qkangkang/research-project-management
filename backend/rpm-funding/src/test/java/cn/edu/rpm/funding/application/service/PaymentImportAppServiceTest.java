package cn.edu.rpm.funding.application.service;

import cn.edu.rpm.audit.domain.service.AuditRecorder;
import cn.edu.rpm.common.domain.service.NumberGenerator;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.common.util.UuidUtils;
import cn.edu.rpm.funding.application.dto.PaymentImportBatchView;
import cn.edu.rpm.funding.domain.model.FundingPermissions;
import cn.edu.rpm.funding.domain.service.PaymentImportFileParser;
import cn.edu.rpm.funding.infrastructure.entity.ExpenseRequestEntity;
import cn.edu.rpm.funding.infrastructure.entity.PaymentImportBatchEntity;
import cn.edu.rpm.funding.infrastructure.entity.PaymentImportRowEntity;
import cn.edu.rpm.funding.infrastructure.entity.PaymentRecordEntity;
import cn.edu.rpm.funding.infrastructure.repository.ExpenseRequestRepository;
import cn.edu.rpm.funding.infrastructure.repository.PaymentImportBatchRepository;
import cn.edu.rpm.funding.infrastructure.repository.PaymentImportRowRepository;
import cn.edu.rpm.funding.infrastructure.repository.PaymentRecordRepository;
import cn.edu.rpm.iam.application.dto.AuthContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PaymentImportAppService} 纯单元测试（T-FUND-028；TC-FUND-053～058）。
 */
@ExtendWith(MockitoExtension.class)
class PaymentImportAppServiceTest {

    private static final String HEADER = "external_payment_no,external_expense_no,project_no,"
            + "amount,currency,paid_at,voucher_no";
    private static final UUID UPLOADER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID REVIEWER_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final Instant NOW = Instant.parse("2026-11-18T02:00:00Z");
    private static final String BATCH_ID = "99999999999999999999999999999999";

    @Mock
    private PaymentImportBatchRepository batchRepository;

    @Mock
    private PaymentImportRowRepository rowRepository;

    @Mock
    private PaymentRecordRepository paymentRecordRepository;

    @Mock
    private ExpenseRequestRepository expenseRequestRepository;

    @Mock
    private PaymentPostingService paymentPostingService;

    @Mock
    private NumberGenerator numberGenerator;

    @Mock
    private AuditRecorder auditRecorder;

    private PaymentImportAppService service;

    private AuthContext reviewer;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        service = new PaymentImportAppService(batchRepository, rowRepository,
                new PaymentImportFileParser(objectMapper), paymentRecordRepository,
                expenseRequestRepository, paymentPostingService, numberGenerator,
                auditRecorder, objectMapper, Clock.fixed(NOW, ZoneId.of("Asia/Shanghai")));
        reviewer = new AuthContext(REVIEWER_ID, null, "EMP-002",
                Set.of(FundingPermissions.PAYMENT_IMPORT, FundingPermissions.PAYMENT_APPLY),
                Set.of(), NOW);
        lenient().when(numberGenerator.next(PaymentImportAppService.RULE_IMPORT_BATCH_NO))
                .thenReturn("IMP-2026-0001");
        lenient().when(batchRepository.update(any())).thenReturn(1);
        lenient().when(rowRepository.update(any())).thenReturn(1);
    }

    @Test
    void tcFund053UploadCreatesUploadedBatchAndRows() {
        PaymentImportBatchView view = service.upload("file-1", "CSV", hundredRows(), uploader());

        assertThat(view.status()).isEqualTo(PaymentImportBatchEntity.STATUS_UPLOADED);
        assertThat(view.totalRows()).isEqualTo(100);
        assertThat(view.batchNo()).isEqualTo("IMP-2026-0001");
        ArgumentCaptor<PaymentImportBatchEntity> batchCaptor =
                ArgumentCaptor.forClass(PaymentImportBatchEntity.class);
        verify(batchRepository).insert(batchCaptor.capture());
        assertThat(batchCaptor.getValue().getFileId()).isEqualTo("file-1");
        assertThat(batchCaptor.getValue().getUploadedBy())
                .isEqualTo(UuidUtils.formatCompact(UPLOADER_ID));
        verify(rowRepository, org.mockito.Mockito.times(100)).insert(any());
        verify(auditRecorder).record(any());
    }

    @Test
    void tcFund054ValidateAllValidMarksReady() {
        PaymentImportBatchEntity batch = batch(PaymentImportBatchEntity.STATUS_UPLOADED);
        List<PaymentImportRowEntity> rows = rows(100, null);
        when(batchRepository.findById(BATCH_ID)).thenReturn(Optional.of(batch));
        when(rowRepository.findByBatchId(BATCH_ID)).thenReturn(rows);

        PaymentImportBatchView view = service.validate(BATCH_ID, reviewer);

        assertThat(view.status()).isEqualTo(PaymentImportBatchEntity.STATUS_READY);
        assertThat(view.successRows()).isEqualTo(100);
        assertThat(view.errorRows()).isZero();
        assertThat(rows).allMatch(row -> PaymentImportRowEntity.STATUS_VALID.equals(row.getStatus()));
        assertThat(batch.getVerifiedBy()).isEqualTo(UuidUtils.formatCompact(REVIEWER_ID));
    }

    @Test
    void tcFund055ValidateErrorsKeepsUploadedAndWritesRowCodes() {
        PaymentImportBatchEntity batch = batch(PaymentImportBatchEntity.STATUS_UPLOADED);
        List<PaymentImportRowEntity> rows = rows(100, null);
        rows.get(16).setErrorCode(PaymentImportFileParser.ERROR_AMOUNT_INVALID);
        rows.get(32).setErrorCode(PaymentImportFileParser.ERROR_DUPLICATE_PAYMENT_NO);
        when(batchRepository.findById(BATCH_ID)).thenReturn(Optional.of(batch));
        when(rowRepository.findByBatchId(BATCH_ID)).thenReturn(rows);

        PaymentImportBatchView view = service.validate(BATCH_ID, reviewer);

        assertThat(view.status()).isEqualTo(PaymentImportBatchEntity.STATUS_UPLOADED);
        assertThat(view.successRows()).isEqualTo(98);
        assertThat(view.errorRows()).isEqualTo(2);
        assertThat(rows.get(16).getStatus()).isEqualTo(PaymentImportRowEntity.STATUS_INVALID);
        assertThat(rows.get(16).getErrorCode())
                .isEqualTo(PaymentImportFileParser.ERROR_AMOUNT_INVALID);
        assertThat(rows.get(32).getErrorCode())
                .isEqualTo(PaymentImportFileParser.ERROR_DUPLICATE_PAYMENT_NO);
    }

    @Test
    void uploaderCannotValidateOwnBatch() {
        PaymentImportBatchEntity batch = batch(PaymentImportBatchEntity.STATUS_UPLOADED);
        when(batchRepository.findById(BATCH_ID)).thenReturn(Optional.of(batch));

        assertThatThrownBy(() -> service.validate(BATCH_ID, uploader()))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.COMMON_403);
        verify(batchRepository, never()).update(any());
    }

    @Test
    void tcFund058ApplyWithoutPermissionThrows403AndKeepsReady() {
        PaymentImportBatchEntity batch = batch(PaymentImportBatchEntity.STATUS_READY);
        AuthContext noPermission = new AuthContext(REVIEWER_ID, null, "EMP-003",
                Set.of(FundingPermissions.PAYMENT_IMPORT), Set.of(), NOW);

        assertThatThrownBy(() -> service.apply(BATCH_ID, noPermission))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.COMMON_403);
        assertThat(batch.getStatus()).isEqualTo(PaymentImportBatchEntity.STATUS_READY);
        verify(paymentPostingService, never()).post(any(), any(), anyString(), anyString(),
                any(), anyString(), anyString());
    }

    @Test
    void tcFund056ApplyWritesPaymentAndMarksApplied() {
        PaymentImportBatchEntity batch = batch(PaymentImportBatchEntity.STATUS_READY);
        List<PaymentImportRowEntity> rows = rows(1, PaymentImportRowEntity.STATUS_VALID);
        ExpenseRequestEntity expense = expense();
        when(batchRepository.findById(BATCH_ID)).thenReturn(Optional.of(batch));
        when(rowRepository.findByBatchId(BATCH_ID)).thenReturn(rows);
        when(paymentRecordRepository.findByExternalPaymentNo("PAY-001"))
                .thenReturn(Optional.empty());
        when(expenseRequestRepository.findByRequestNo("EXP-2026-001"))
                .thenReturn(Optional.of(expense));
        when(paymentPostingService.post(any(), any(), anyString(), anyString(), any(), anyString(),
                anyString())).thenReturn(new PaymentPostingService.Posting("pay-1",
                ExpenseRequestEntity.STATUS_PAID, "evt-1", new BigDecimal("1000.00"),
                BigDecimal.ZERO));

        PaymentImportBatchView view = service.apply(BATCH_ID, reviewer);

        assertThat(view.status()).isEqualTo(PaymentImportBatchEntity.STATUS_APPLIED);
        assertThat(rows.get(0).getStatus()).isEqualTo(PaymentImportRowEntity.STATUS_APPLIED);
        assertThat(rows.get(0).getMatchedExpenseId()).isEqualTo(expense.getId());
        assertThat(batch.getAppliedBy()).isEqualTo(UuidUtils.formatCompact(REVIEWER_ID));
        verify(paymentPostingService).post(any(), eqAmount("1000.00"), anyString(), anyString(),
                any(), anyString(), anyString());
    }

    @Test
    void tcFund057RepeatedApplyReturnsFirstResultWithoutPosting() {
        PaymentImportBatchEntity batch = batch(PaymentImportBatchEntity.STATUS_APPLIED);
        List<PaymentImportRowEntity> rows = rows(1, PaymentImportRowEntity.STATUS_APPLIED);
        when(batchRepository.findById(BATCH_ID)).thenReturn(Optional.of(batch));
        when(rowRepository.findByBatchId(BATCH_ID)).thenReturn(rows);

        PaymentImportBatchView view = service.apply(BATCH_ID, reviewer);

        assertThat(view.idempotent()).isTrue();
        assertThat(view.status()).isEqualTo(PaymentImportBatchEntity.STATUS_APPLIED);
        verify(paymentPostingService, never()).post(any(), any(), anyString(), anyString(),
                any(), anyString(), anyString());
        verify(batchRepository, never()).update(any());
    }

    @Test
    void existingExternalPaymentNoIsSkippedOnApply() {
        PaymentImportBatchEntity batch = batch(PaymentImportBatchEntity.STATUS_READY);
        List<PaymentImportRowEntity> rows = rows(1, PaymentImportRowEntity.STATUS_VALID);
        when(batchRepository.findById(BATCH_ID)).thenReturn(Optional.of(batch));
        when(rowRepository.findByBatchId(BATCH_ID)).thenReturn(rows);
        when(paymentRecordRepository.findByExternalPaymentNo("PAY-001"))
                .thenReturn(Optional.of(new PaymentRecordEntity()));

        service.apply(BATCH_ID, reviewer);

        assertThat(rows.get(0).getStatus()).isEqualTo(PaymentImportRowEntity.STATUS_SKIPPED);
        assertThat(rows.get(0).getErrorCode())
                .isEqualTo(PaymentImportAppService.ERROR_ALREADY_EXISTS);
        verify(paymentPostingService, never()).post(any(), any(), anyString(), anyString(),
                any(), anyString(), anyString());
    }

    private AuthContext uploader() {
        return new AuthContext(UPLOADER_ID, null, "EMP-001",
                Set.of(FundingPermissions.PAYMENT_IMPORT, FundingPermissions.PAYMENT_APPLY),
                Set.of(), NOW);
    }

    private static byte[] hundredRows() {
        StringBuilder csv = new StringBuilder(HEADER).append('\n');
        for (int i = 1; i <= 100; i++) {
            csv.append(String.format("PAY-%03d,EXP-2026-%03d,KY-001,1000.00,CNY,"
                    + "2026-11-17T09:30:00+08:00,PZ-001%n", i, i));
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static PaymentImportBatchEntity batch(String status) {
        PaymentImportBatchEntity batch = new PaymentImportBatchEntity();
        batch.setId(BATCH_ID);
        batch.setBatchNo("IMP-2026-0001");
        batch.setStatus(status);
        batch.setTotalRows(100);
        batch.setSuccessRows(100);
        batch.setUploadedBy(UuidUtils.formatCompact(UPLOADER_ID));
        batch.setVersion(1);
        return batch;
    }

    private static List<PaymentImportRowEntity> rows(int count, String status) {
        List<PaymentImportRowEntity> rows = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            PaymentImportRowEntity row = new PaymentImportRowEntity();
            row.setId(String.format("%032d", i));
            row.setBatchId(BATCH_ID);
            row.setRowNo(i);
            row.setExternalPaymentNo(String.format("PAY-%03d", i));
            row.setAmount(new BigDecimal("1000.00"));
            row.setStatus(status == null ? PaymentImportRowEntity.STATUS_PENDING : status);
            row.setRawData("{\"external_payment_no\":\"PAY-" + String.format("%03d", i)
                    + "\",\"external_expense_no\":\"EXP-2026-" + String.format("%03d", i)
                    + "\",\"project_no\":\"KY-001\",\"amount\":\"1000.00\",\"currency\":\"CNY\","
                    + "\"paid_at\":\"2026-11-17T09:30:00+08:00\",\"voucher_no\":\"PZ-001\"}");
            rows.add(row);
        }
        return rows;
    }

    private static ExpenseRequestEntity expense() {
        ExpenseRequestEntity expense = new ExpenseRequestEntity();
        expense.setId("22222222222222222222222222222222");
        expense.setRequestNo("EXP-2026-001");
        expense.setProjectId("11111111111111111111111111111111");
        expense.setBudgetVersionId("33333333333333333333333333333333");
        expense.setBudgetLineId("44444444444444444444444444444444");
        expense.setAmount(new BigDecimal("80000.00"));
        expense.setStatus(ExpenseRequestEntity.STATUS_APPROVED_UNPAID);
        return expense;
    }

    private static BigDecimal eqAmount(String value) {
        return org.mockito.ArgumentMatchers.eq(new BigDecimal(value));
    }
}
