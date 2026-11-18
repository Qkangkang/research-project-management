package cn.edu.rpm.funding.application.service;

import cn.edu.rpm.audit.domain.service.AuditRecorder;
import cn.edu.rpm.common.domain.port.OutboxAppendPort;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.common.util.UuidUtils;
import cn.edu.rpm.funding.application.dto.ExpenseActionCmd;
import cn.edu.rpm.funding.application.dto.ExpenseStatusResult;
import cn.edu.rpm.funding.domain.model.FundEventCommand;
import cn.edu.rpm.funding.domain.model.FundingPermissions;
import cn.edu.rpm.funding.domain.port.ExpenseWorkflowLauncher;
import cn.edu.rpm.funding.domain.service.FundEventAppender;
import cn.edu.rpm.funding.infrastructure.entity.ExpenseRequestEntity;
import cn.edu.rpm.funding.infrastructure.entity.FundEventEntity;
import cn.edu.rpm.funding.infrastructure.entity.PaymentRecordEntity;
import cn.edu.rpm.funding.infrastructure.repository.ExpenseRequestRepository;
import cn.edu.rpm.funding.infrastructure.repository.PaymentRecordRepository;
import cn.edu.rpm.iam.application.dto.AuthContext;
import cn.edu.rpm.iam.application.dto.AuthDecision;
import cn.edu.rpm.iam.domain.service.AuthorizationService;
import cn.edu.rpm.project.infrastructure.entity.ProjectEntity;
import cn.edu.rpm.project.infrastructure.repository.ProjectMemberRepository;
import cn.edu.rpm.project.infrastructure.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * {@link ExpenseWithdrawCancelAppService} 纯单元测试
 * （T-FUND-021；TC-FUND-030～TC-FUND-032、TC-FUND-068）。
 */
@ExtendWith(MockitoExtension.class)
class ExpenseWithdrawCancelAppServiceTest {

    private static final String PROJECT_ID = "11111111111111111111111111111111";
    private static final String EXPENSE_ID = "22222222222222222222222222222222";
    private static final String VERSION_ID = "33333333333333333333333333333333";
    private static final String LINE_ID = "44444444444444444444444444444444";
    private static final UUID ACTOR_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID PI_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID DEPT_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID REQUEST_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private static final Instant NOW = Instant.parse("2026-11-18T02:00:00Z");

    @Mock
    private ExpenseRequestRepository expenseRequestRepository;

    @Mock
    private PaymentRecordRepository paymentRecordRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectMemberRepository projectMemberRepository;

    @Mock
    private AuthorizationService authorizationService;

    @Mock
    private FundEventAppender fundEventAppender;

    @Mock
    private ExpenseWorkflowLauncher workflowLauncher;

    @Mock
    private AuditRecorder auditRecorder;

    @Mock
    private OutboxAppendPort outboxAppendPort;

    private ExpenseWithdrawCancelAppService service;

    private AuthContext auth;

    private ExpenseRequestEntity expense;

    @BeforeEach
    void setUp() {
        service = new ExpenseWithdrawCancelAppService(expenseRequestRepository, paymentRecordRepository,
                projectRepository, projectMemberRepository, authorizationService, fundEventAppender,
                workflowLauncher, auditRecorder, outboxAppendPort,
                Clock.fixed(NOW, ZoneId.of("Asia/Shanghai")));
        ProjectEntity project = new ProjectEntity();
        project.setId(PROJECT_ID);
        project.setPiId(UuidUtils.formatCompact(PI_ID));
        project.setDeptId(UuidUtils.formatCompact(DEPT_ID));
        project.setStatus(ProjectEntity.STATUS_EXECUTING);
        expense = new ExpenseRequestEntity();
        expense.setId(EXPENSE_ID);
        expense.setRequestNo("EXP-2026-0001");
        expense.setProjectId(PROJECT_ID);
        expense.setBudgetVersionId(VERSION_ID);
        expense.setBudgetLineId(LINE_ID);
        expense.setAmount(new BigDecimal("80000.00"));
        expense.setStatus(ExpenseRequestEntity.STATUS_IN_REVIEW);
        expense.setCurrentVersionId("55555555555555555555555555555555");
        expense.setVersion(3);
        auth = new AuthContext(ACTOR_ID, null, "EMP-001",
                Set.of(FundingPermissions.EXPENSE_WITHDRAW, FundingPermissions.EXPENSE_CANCEL),
                Set.of(), NOW);
        lenient().when(expenseRequestRepository.selectForUpdate(EXPENSE_ID)).thenReturn(Optional.of(expense));
        lenient().when(expenseRequestRepository.update(any())).thenReturn(1);
        lenient().when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        lenient().when(projectMemberRepository.findActiveByProjectId(eq(PROJECT_ID), any()))
                .thenReturn(List.of());
        lenient().when(authorizationService.check(any(AuthContext.class), any(), any()))
                .thenReturn(AuthDecision.allow());
        lenient().when(fundEventAppender.append(any())).thenAnswer(invocation -> new FundEventEntity());
    }

    @Test
    void tcFund030WithdrawInReviewReleasesReservationAndCancels() {
        ExpenseStatusResult result = service.withdraw(EXPENSE_ID, action(), auth);

        assertThat(result.status()).isEqualTo(ExpenseRequestEntity.STATUS_CANCELLED);
        assertThat(result.releasedAmount()).isEqualByComparingTo("80000.00");
        ArgumentCaptor<FundEventCommand> captor = ArgumentCaptor.forClass(FundEventCommand.class);
        verify(fundEventAppender).append(captor.capture());
        assertThat(captor.getValue().eventType()).isEqualTo(FundEventEntity.TYPE_RELEASE);
        assertThat(captor.getValue().amount()).isEqualByComparingTo("80000.00");
        assertThat(captor.getValue().idempotencyKey())
                .isEqualTo("expense:" + EXPENSE_ID + ":RELEASE");
        verify(workflowLauncher).cancel(eq("EXPENSE"), any(), any(), eq(ACTOR_ID), any(), eq(REQUEST_ID));
    }

    @Test
    void tcFund031CancelSupplementReleasesReservation() {
        expense.setStatus(ExpenseRequestEntity.STATUS_SUPPLEMENT);

        ExpenseStatusResult result = service.cancel(EXPENSE_ID, action(), auth);

        assertThat(result.status()).isEqualTo(ExpenseRequestEntity.STATUS_CANCELLED);
        ArgumentCaptor<FundEventCommand> captor = ArgumentCaptor.forClass(FundEventCommand.class);
        verify(fundEventAppender).append(captor.capture());
        assertThat(captor.getValue().eventType()).isEqualTo(FundEventEntity.TYPE_RELEASE);
        assertThat(captor.getValue().amount()).isEqualByComparingTo("80000.00");
    }

    @Test
    void tcFund032CancelApprovedUnpaidCreatesCancelEvent() {
        expense.setStatus(ExpenseRequestEntity.STATUS_APPROVED_UNPAID);

        ExpenseStatusResult result = service.cancel(EXPENSE_ID, action(), auth);

        assertThat(result.status()).isEqualTo(ExpenseRequestEntity.STATUS_CANCELLED);
        assertThat(result.releasedAmount()).isEqualByComparingTo("80000.00");
        ArgumentCaptor<FundEventCommand> captor = ArgumentCaptor.forClass(FundEventCommand.class);
        verify(fundEventAppender).append(captor.capture());
        assertThat(captor.getValue().eventType()).isEqualTo(FundEventEntity.TYPE_CANCEL);
    }

    @Test
    void tcFund068CancelPartiallyPaidCancelsRemainingAndKeepsPaid() {
        expense.setStatus(ExpenseRequestEntity.STATUS_PARTIALLY_PAID);
        PaymentRecordEntity paid = new PaymentRecordEntity();
        paid.setAmount(new BigDecimal("30000.00"));
        paid.setStatus(PaymentRecordEntity.STATUS_APPLIED);
        lenient().when(paymentRecordRepository.findByExpenseRequestId(EXPENSE_ID))
                .thenReturn(List.of(paid));

        ExpenseStatusResult result = service.cancel(EXPENSE_ID, action(), auth);

        assertThat(result.status()).isEqualTo(ExpenseRequestEntity.STATUS_CANCELLED);
        assertThat(result.releasedAmount()).isEqualByComparingTo("50000.00");
        ArgumentCaptor<FundEventCommand> captor = ArgumentCaptor.forClass(FundEventCommand.class);
        verify(fundEventAppender).append(captor.capture());
        assertThat(captor.getValue().eventType()).isEqualTo(FundEventEntity.TYPE_CANCEL);
        assertThat(captor.getValue().amount()).isEqualByComparingTo("50000.00");
        verify(paymentRecordRepository, never()).update(any());
    }

    @Test
    void cancelPaidExpenseThrowsFund005() {
        expense.setStatus(ExpenseRequestEntity.STATUS_PAID);

        assertThatThrownBy(() -> service.cancel(EXPENSE_ID, action(), auth))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FUND_005);
        verify(fundEventAppender, never()).append(any());
    }

    @Test
    void withdrawApprovedUnpaidThrowsFund005() {
        expense.setStatus(ExpenseRequestEntity.STATUS_APPROVED_UNPAID);

        assertThatThrownBy(() -> service.withdraw(EXPENSE_ID, action(), auth))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FUND_005);
    }

    private static ExpenseActionCmd action() {
        return new ExpenseActionCmd(3, "申请人撤回", REQUEST_ID);
    }
}
