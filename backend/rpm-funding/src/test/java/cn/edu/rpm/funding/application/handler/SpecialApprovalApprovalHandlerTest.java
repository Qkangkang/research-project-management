package cn.edu.rpm.funding.application.handler;

import cn.edu.rpm.audit.domain.model.AuditEvent;
import cn.edu.rpm.audit.domain.service.AuditRecorder;
import cn.edu.rpm.common.domain.port.OutboxAppendPort;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
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
import cn.edu.rpm.funding.infrastructure.repository.BudgetBalanceRepository;
import cn.edu.rpm.funding.infrastructure.repository.BudgetLineRepository;
import cn.edu.rpm.funding.infrastructure.repository.BudgetVersionRepository;
import cn.edu.rpm.funding.infrastructure.repository.ExpenseRequestRepository;
import cn.edu.rpm.funding.infrastructure.repository.ExpenseSpecialApprovalRepository;
import cn.edu.rpm.funding.infrastructure.repository.PaymentRecordRepository;
import cn.edu.rpm.project.infrastructure.entity.ProjectEntity;
import cn.edu.rpm.project.infrastructure.repository.ProjectRepository;
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
import java.time.LocalDate;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SpecialApprovalApprovalHandler} 纯单元测试
 * （T-FUND-025；TC-FUND-103～TC-FUND-106）。
 */
@ExtendWith(MockitoExtension.class)
class SpecialApprovalApprovalHandlerTest {

    private static final String APPROVAL_ID = "11111111111111111111111111111111";
    private static final String PROJECT_ID = "22222222222222222222222222222222";
    private static final String EXPENSE_ID = "33333333333333333333333333333333";
    private static final String VERSION_ID = "44444444444444444444444444444444";
    private static final String STALE_VERSION_ID = "45454545454545454545454545454545";
    private static final String EFFECTIVE_VERSION_ID = "55555555555555555555555555555555";
    private static final String EFFECTIVE_LINE_ID = "66666666666666666666666666666666";
    private static final String INSTANCE_ID = "77777777777777777777777777777777";
    private static final UUID OPERATOR_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID APPLICANT_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID PI_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID REQUEST_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private static final Instant NOW = Instant.parse("2026-11-18T02:00:00Z");

    @Mock
    private ExpenseSpecialApprovalRepository specialApprovalRepository;

    @Mock
    private ExpenseRequestRepository expenseRequestRepository;

    @Mock
    private PaymentRecordRepository paymentRecordRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private BudgetVersionRepository budgetVersionRepository;

    @Mock
    private BudgetLineRepository budgetLineRepository;

    @Mock
    private BudgetBalanceRepository budgetBalanceRepository;

    @Mock
    private FundEventAppender fundEventAppender;

    @Mock
    private ExpenseWorkflowLauncher workflowLauncher;

    @Mock
    private AuditRecorder auditRecorder;

    @Mock
    private OutboxAppendPort outboxAppendPort;

    private SpecialApprovalApprovalHandler handler;

    private ExpenseSpecialApprovalEntity approval;

    private ExpenseRequestEntity expense;

    private BudgetVersionEntity effectiveVersion;

    @BeforeEach
    void setUp() {
        handler = new SpecialApprovalApprovalHandler(specialApprovalRepository, expenseRequestRepository,
                paymentRecordRepository, projectRepository, budgetVersionRepository,
                budgetLineRepository, budgetBalanceRepository, fundEventAppender, workflowLauncher,
                auditRecorder, outboxAppendPort, Clock.fixed(NOW, ZoneId.of("Asia/Shanghai")),
                30, new BigDecimal("100000.00"));

        approval = new ExpenseSpecialApprovalEntity();
        approval.setId(APPROVAL_ID);
        approval.setExpenseRequestId(EXPENSE_ID);
        approval.setExpenseVersionId(VERSION_ID);
        approval.setSpecialType(ExpenseSpecialApprovalEntity.TYPE_OVER_BUDGET);
        approval.setOverAmount(new BigDecimal("20000.00"));
        approval.setApplicantId(UuidUtils.formatCompact(APPLICANT_ID));
        approval.setStatus(ExpenseSpecialApprovalEntity.STATUS_PENDING);

        ProjectEntity project = new ProjectEntity();
        project.setId(PROJECT_ID);
        project.setPiId(UuidUtils.formatCompact(PI_ID));
        project.setDeptId(UuidUtils.formatCompact(PI_ID));
        project.setStatus(ProjectEntity.STATUS_EXECUTING);

        expense = new ExpenseRequestEntity();
        expense.setId(EXPENSE_ID);
        expense.setRequestNo("EXP-2026-0001");
        expense.setProjectId(PROJECT_ID);
        expense.setBudgetVersionId(EFFECTIVE_VERSION_ID);
        expense.setBudgetLineId(EFFECTIVE_LINE_ID);
        expense.setAmount(new BigDecimal("80000.00"));
        expense.setStatus(ExpenseRequestEntity.STATUS_DRAFT);
        expense.setCurrentVersionId(VERSION_ID);
        expense.setCreatedBy("eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee");

        effectiveVersion = new BudgetVersionEntity();
        effectiveVersion.setId(EFFECTIVE_VERSION_ID);
        effectiveVersion.setProjectId(PROJECT_ID);
        effectiveVersion.setVersionNo(1);
        effectiveVersion.setTotalAmount(new BigDecimal("1200000.00"));
        effectiveVersion.setControlLevel(BudgetVersionEntity.CONTROL_BLOCK);
        effectiveVersion.setStatus(BudgetVersionEntity.STATUS_EFFECTIVE);

        BudgetLineEntity effectiveLine = new BudgetLineEntity();
        effectiveLine.setId(EFFECTIVE_LINE_ID);
        effectiveLine.setBudgetVersionId(EFFECTIVE_VERSION_ID);
        effectiveLine.setSubjectCode("EQUIPMENT");
        effectiveLine.setSubjectName("设备费");
        effectiveLine.setAmount(new BigDecimal("300000.00"));
        effectiveLine.setControlLevel(BudgetVersionEntity.CONTROL_BLOCK);

        lenient().when(specialApprovalRepository.selectForUpdate(APPROVAL_ID))
                .thenReturn(Optional.of(approval));
        lenient().when(specialApprovalRepository.update(any())).thenReturn(1);
        lenient().when(expenseRequestRepository.selectForUpdate(EXPENSE_ID)).thenReturn(Optional.of(expense));
        lenient().when(expenseRequestRepository.update(any())).thenReturn(1);
        lenient().when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        lenient().when(budgetVersionRepository.findEffectiveByProjectId(PROJECT_ID))
                .thenReturn(Optional.of(effectiveVersion));
        lenient().when(budgetVersionRepository.findByProjectId(PROJECT_ID))
                .thenReturn(List.of(effectiveVersion));
        lenient().when(budgetLineRepository.findByBudgetVersionId(EFFECTIVE_VERSION_ID))
                .thenReturn(List.of(effectiveLine));
        lenient().when(fundEventAppender.append(any())).thenAnswer(invocation -> new FundEventEntity());
        lenient().when(workflowLauncher.startExpense(any())).thenReturn(INSTANCE_ID);
        lenient().when(paymentRecordRepository.findByExpenseRequestId(EXPENSE_ID)).thenReturn(List.of());
    }

    @Test
    void tcFund104ApproveCreatesAdjustmentVersionAdjustReserveAndRestartsExpense() {
        handler.onApproved(result(VERSION_ID, "APPROVED"));

        assertThat(approval.getStatus()).isEqualTo(ExpenseSpecialApprovalEntity.STATUS_APPROVED);
        assertThat(approval.getApproverId()).isEqualTo(UuidUtils.formatCompact(OPERATOR_ID));
        assertThat(approval.getValidUntil()).isEqualTo(LocalDate.of(2026, 12, 18));
        assertThat(approval.getAdjustmentVersionId()).isNotBlank();
        assertThat(expense.getStatus()).isEqualTo(ExpenseRequestEntity.STATUS_IN_REVIEW);

        ArgumentCaptor<FundEventCommand> events = ArgumentCaptor.forClass(FundEventCommand.class);
        verify(fundEventAppender, times(2)).append(events.capture());
        assertThat(events.getAllValues()).extracting(FundEventCommand::eventType)
                .containsExactly(FundEventEntity.TYPE_ADJUST, FundEventEntity.TYPE_RESERVE);
        assertThat(events.getAllValues().get(0).amount()).isEqualByComparingTo("20000.00");
        assertThat(events.getAllValues().get(0).idempotencyKey())
                .isEqualTo("special:" + APPROVAL_ID + ":ADJUST");
        assertThat(events.getAllValues().get(1).amount()).isEqualByComparingTo("80000.00");
        assertThat(events.getAllValues().get(1).idempotencyKey())
                .isEqualTo("expense:" + EXPENSE_ID + ":RESERVE");

        verify(budgetVersionRepository).insert(any(BudgetVersionEntity.class));
        verify(budgetLineRepository).insert(any(BudgetLineEntity.class));
        verify(budgetBalanceRepository).insert(any(BudgetBalanceEntity.class));
        verify(workflowLauncher).startExpense(any(ExpenseWorkflowLauncher.ExpenseWorkflowStart.class));
        verify(auditRecorder).record(any(AuditEvent.class));
    }

    @Test
    void tcFund103ApproverEqualsPiThrowsWf003() {
        ApprovalResult piResult = result(VERSION_ID, "APPROVED", PI_ID);

        assertThatThrownBy(() -> handler.onApproved(piResult))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.WF_003);
        verify(fundEventAppender, never()).append(any());
    }

    @Test
    void approverEqualsApplicantThrowsWf003() {
        ApprovalResult applicantResult = result(VERSION_ID, "APPROVED", APPLICANT_ID);

        assertThatThrownBy(() -> handler.onApproved(applicantResult))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.WF_003);
    }

    @Test
    void tcFund105OverLimitRecordsCountersignAudit() {
        approval.setOverAmount(new BigDecimal("150000.00"));

        handler.onApproved(result(VERSION_ID, "APPROVED"));

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditRecorder, times(2)).record(captor.capture());
        assertThat(captor.getAllValues()).extracting(AuditEvent::action)
                .contains(SpecialApprovalApprovalHandler.AUDIT_COUNTERSIGN);
    }

    @Test
    void tcFund106ExpireDueReleasesReservationAndExpiresApproval() {
        approval.setStatus(ExpenseSpecialApprovalEntity.STATUS_APPROVED);
        approval.setApproverId(UuidUtils.formatCompact(OPERATOR_ID));
        approval.setValidUntil(LocalDate.of(2026, 11, 17));
        expense.setStatus(ExpenseRequestEntity.STATUS_IN_REVIEW);
        when(specialApprovalRepository.findApprovedExpiringBefore(LocalDate.of(2026, 11, 18)))
                .thenReturn(List.of(approval));

        int expired = handler.expireDue(LocalDate.of(2026, 11, 18));

        assertThat(expired).isEqualTo(1);
        assertThat(approval.getStatus()).isEqualTo(ExpenseSpecialApprovalEntity.STATUS_EXPIRED);
        assertThat(expense.getStatus()).isEqualTo(ExpenseRequestEntity.STATUS_CANCELLED);
        ArgumentCaptor<FundEventCommand> captor = ArgumentCaptor.forClass(FundEventCommand.class);
        verify(fundEventAppender).append(captor.capture());
        assertThat(captor.getValue().eventType()).isEqualTo(FundEventEntity.TYPE_RELEASE);
        assertThat(captor.getValue().amount()).isEqualByComparingTo("80000.00");
        assertThat(captor.getValue().idempotencyKey())
                .isEqualTo("special:" + APPROVAL_ID + ":EXPIRE_RELEASE");
        verify(workflowLauncher).cancel(any(), any(), any(), any(), any(), any());
        verify(auditRecorder).record(any(AuditEvent.class));
    }

    @Test
    void rejectSetsRejectedWithReason() {
        handler.onRejected(result(VERSION_ID, "REJECTED"));

        assertThat(approval.getStatus()).isEqualTo(ExpenseSpecialApprovalEntity.STATUS_REJECTED);
        assertThat(approval.getRejectionReason()).isNotBlank();
        verify(fundEventAppender, never()).append(any());
    }

    @Test
    void staleVersionThrowsWf005() {
        assertThatThrownBy(() -> handler.onApproved(result(STALE_VERSION_ID, "APPROVED")))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.WF_005);
        verify(auditRecorder).recordNonCritical(any(AuditEvent.class));
    }

    private static ApprovalResult result(String versionId, String action) {
        return result(versionId, action, OPERATOR_ID);
    }

    private static ApprovalResult result(String versionId, String action, UUID operatorId) {
        return new ApprovalResult(UUID.fromString("88888888-8888-8888-8888-888888888888"),
                "SPECIAL_APPROVAL", UuidUtils.parse(APPROVAL_ID), UuidUtils.parse(versionId),
                action, operatorId, null, "特批意见",
                OffsetDateTime.ofInstant(NOW, ZoneId.of("Asia/Shanghai")), REQUEST_ID, List.of());
    }
}
