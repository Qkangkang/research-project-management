package cn.edu.rpm.funding.application.handler;

import cn.edu.rpm.audit.domain.model.AuditEvent;
import cn.edu.rpm.audit.domain.service.AuditRecorder;
import cn.edu.rpm.common.domain.port.OutboxAppendPort;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.common.util.UuidUtils;
import cn.edu.rpm.funding.domain.model.FundEventCommand;
import cn.edu.rpm.funding.domain.service.FundEventAppender;
import cn.edu.rpm.funding.infrastructure.entity.ExpenseRequestEntity;
import cn.edu.rpm.funding.infrastructure.entity.FundEventEntity;
import cn.edu.rpm.funding.infrastructure.repository.ExpenseRequestRepository;
import cn.edu.rpm.funding.infrastructure.repository.PaymentRecordRepository;
import cn.edu.rpm.workflow.domain.dto.ApprovalResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * {@link ExpenseApprovalHandler} 纯单元测试（T-FUND-022；TC-FUND-026～TC-FUND-029）。
 */
@ExtendWith(MockitoExtension.class)
class ExpenseApprovalHandlerTest {

    private static final String EXPENSE_ID = "22222222222222222222222222222222";
    private static final String VERSION_ID = "33333333333333333333333333333333";
    private static final String STALE_VERSION_ID = "34343434343434343434343434343434";
    private static final String PROJECT_ID = "11111111111111111111111111111111";
    private static final String LINE_ID = "44444444444444444444444444444444";
    private static final String INSTANCE_ID = "55555555555555555555555555555555";
    private static final UUID OPERATOR_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID REQUEST_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private static final Instant NOW = Instant.parse("2026-11-18T02:00:00Z");

    @Mock
    private ExpenseRequestRepository expenseRequestRepository;

    @Mock
    private PaymentRecordRepository paymentRecordRepository;

    @Mock
    private FundEventAppender fundEventAppender;

    @Mock
    private AuditRecorder auditRecorder;

    @Mock
    private OutboxAppendPort outboxAppendPort;

    private ExpenseApprovalHandler handler;

    private ExpenseRequestEntity expense;

    @BeforeEach
    void setUp() {
        handler = new ExpenseApprovalHandler(expenseRequestRepository, paymentRecordRepository,
                fundEventAppender, auditRecorder, outboxAppendPort,
                Clock.fixed(NOW, ZoneId.of("Asia/Shanghai")));
        expense = new ExpenseRequestEntity();
        expense.setId(EXPENSE_ID);
        expense.setRequestNo("EXP-2026-0001");
        expense.setProjectId(PROJECT_ID);
        expense.setBudgetVersionId(VERSION_ID);
        expense.setBudgetLineId(LINE_ID);
        expense.setAmount(new BigDecimal("80000.00"));
        expense.setStatus(ExpenseRequestEntity.STATUS_IN_REVIEW);
        expense.setCurrentVersionId(VERSION_ID);
        expense.setVersion(2);
        lenient().when(expenseRequestRepository.selectForUpdate(EXPENSE_ID)).thenReturn(Optional.of(expense));
        lenient().when(expenseRequestRepository.update(any())).thenReturn(1);
        lenient().when(fundEventAppender.append(any())).thenAnswer(invocation -> new FundEventEntity());
    }

    @Test
    void tcFund028ApprovedCreatesApproveEventAndApprovedUnpaid() {
        handler.onApproved(result(VERSION_ID, "APPROVED"));

        assertThat(expense.getStatus()).isEqualTo(ExpenseRequestEntity.STATUS_APPROVED_UNPAID);
        assertThat(expense.getApprovedAt()).isNotNull();
        ArgumentCaptor<FundEventCommand> captor = ArgumentCaptor.forClass(FundEventCommand.class);
        verify(fundEventAppender).append(captor.capture());
        assertThat(captor.getValue().eventType()).isEqualTo(FundEventEntity.TYPE_APPROVE);
        assertThat(captor.getValue().amount()).isEqualByComparingTo("80000.00");
        verify(auditRecorder).record(any(AuditEvent.class));
    }

    @Test
    void tcFund029RejectedCreatesReleaseAndRejectedStatus() {
        handler.onRejected(result(VERSION_ID, "REJECTED"));

        assertThat(expense.getStatus()).isEqualTo(ExpenseRequestEntity.STATUS_REJECTED);
        ArgumentCaptor<FundEventCommand> captor = ArgumentCaptor.forClass(FundEventCommand.class);
        verify(fundEventAppender).append(captor.capture());
        assertThat(captor.getValue().eventType()).isEqualTo(FundEventEntity.TYPE_RELEASE);
        assertThat(captor.getValue().amount()).isEqualByComparingTo("80000.00");
    }

    @Test
    void tcFund026ReturnedKeepsReservationWithoutEvent() {
        handler.onReturned(result(VERSION_ID, "RETURNED"));

        assertThat(expense.getStatus()).isEqualTo(ExpenseRequestEntity.STATUS_SUPPLEMENT);
        verify(fundEventAppender, never()).append(any());
        verify(auditRecorder).record(any(AuditEvent.class));
    }

    @Test
    void staleApprovalVersionThrowsWf005AndAudits() {
        assertThatThrownBy(() -> handler.onApproved(result(STALE_VERSION_ID, "APPROVED")))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.WF_005);
        verify(auditRecorder).recordNonCritical(any(AuditEvent.class));
        verify(fundEventAppender, never()).append(any());
    }

    @Test
    void repeatedApprovedCallbackIsIdempotent() {
        expense.setStatus(ExpenseRequestEntity.STATUS_APPROVED_UNPAID);

        handler.onApproved(result(VERSION_ID, "APPROVED"));

        verify(fundEventAppender, never()).append(any());
        verify(expenseRequestRepository, never()).update(any());
    }

    @Test
    void withdrawnInReviewReleasesAndCancels() {
        handler.onWithdrawn(result(VERSION_ID, "CANCELLED"));

        assertThat(expense.getStatus()).isEqualTo(ExpenseRequestEntity.STATUS_CANCELLED);
        ArgumentCaptor<FundEventCommand> captor = ArgumentCaptor.forClass(FundEventCommand.class);
        verify(fundEventAppender).append(captor.capture());
        assertThat(captor.getValue().eventType()).isEqualTo(FundEventEntity.TYPE_RELEASE);
    }

    private static ApprovalResult result(String versionId, String action) {
        return new ApprovalResult(UUID.fromString("66666666-6666-6666-6666-666666666666"),
                "EXPENSE", UuidUtils.parse(EXPENSE_ID), UuidUtils.parse(versionId), action,
                OPERATOR_ID, null, "意见", OffsetDateTime.ofInstant(NOW, ZoneId.of("Asia/Shanghai")),
                REQUEST_ID, List.of());
    }
}
