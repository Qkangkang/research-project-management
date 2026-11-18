package cn.edu.rpm.funding.application.service;

import cn.edu.rpm.audit.domain.service.AuditRecorder;
import cn.edu.rpm.common.domain.port.OutboxAppendPort;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.common.util.CanonicalJsonHasher;
import cn.edu.rpm.common.util.UuidUtils;
import cn.edu.rpm.funding.application.dto.ExpenseSubmitted;
import cn.edu.rpm.funding.application.dto.SubmitExpenseCmd;
import cn.edu.rpm.funding.domain.model.FundEventCommand;
import cn.edu.rpm.funding.domain.model.FundingPermissions;
import cn.edu.rpm.funding.domain.port.ExpenseWorkflowLauncher;
import cn.edu.rpm.funding.domain.service.ApprovalBudgetControlPolicy;
import cn.edu.rpm.funding.domain.service.BlockBudgetControlPolicy;
import cn.edu.rpm.funding.domain.service.BudgetControlPolicy;
import cn.edu.rpm.funding.domain.service.BudgetControlPolicyRegistry;
import cn.edu.rpm.funding.domain.service.FundEventAppender;
import cn.edu.rpm.funding.domain.service.WarnBudgetControlPolicy;
import cn.edu.rpm.funding.infrastructure.entity.BudgetBalanceEntity;
import cn.edu.rpm.funding.infrastructure.entity.BudgetLineEntity;
import cn.edu.rpm.funding.infrastructure.entity.BudgetVersionEntity;
import cn.edu.rpm.funding.infrastructure.entity.ExpenseRequestEntity;
import cn.edu.rpm.funding.infrastructure.entity.ExpenseRequestVersionEntity;
import cn.edu.rpm.funding.infrastructure.entity.FundEventEntity;
import cn.edu.rpm.funding.infrastructure.repository.BudgetBalanceRepository;
import cn.edu.rpm.funding.infrastructure.repository.BudgetLineRepository;
import cn.edu.rpm.funding.infrastructure.repository.BudgetVersionRepository;
import cn.edu.rpm.funding.infrastructure.repository.ExpenseRequestRepository;
import cn.edu.rpm.funding.infrastructure.repository.ExpenseRequestVersionRepository;
import cn.edu.rpm.funding.infrastructure.repository.FundEventRepository;
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
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ExpenseSubmitAppService} 纯单元测试（T-FUND-020；TC-FUND-012～TC-FUND-027）。
 */
@ExtendWith(MockitoExtension.class)
class ExpenseSubmitAppServiceTest {

    private static final String PROJECT_ID = "11111111111111111111111111111111";
    private static final String EXPENSE_ID = "22222222222222222222222222222222";
    private static final String VERSION_ID = "33333333333333333333333333333333";
    private static final String OTHER_VERSION_ID = "34343434343434343434343434343434";
    private static final String LINE_ID = "44444444444444444444444444444444";
    private static final String INSTANCE_ID = "55555555555555555555555555555555";
    private static final UUID ACTOR_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID PI_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID DEPT_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final Instant NOW = Instant.parse("2026-11-18T02:00:00Z");
    private static final String KEY = "submit-key-001";

    @Mock
    private ExpenseRequestRepository expenseRequestRepository;

    @Mock
    private ExpenseRequestVersionRepository versionRepository;

    @Mock
    private FundEventRepository fundEventRepository;

    @Mock
    private BudgetVersionRepository budgetVersionRepository;

    @Mock
    private BudgetLineRepository budgetLineRepository;

    @Mock
    private BudgetBalanceRepository budgetBalanceRepository;

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

    private ExpenseSubmitAppService service;

    private AuthContext auth;

    private ProjectEntity project;

    private BudgetVersionEntity budgetVersion;

    private BudgetLineEntity line;

    private BudgetBalanceEntity balance;

    private ExpenseRequestEntity expense;

    private final AtomicReference<FundEventEntity> reserveRef = new AtomicReference<>();

    private final AtomicReference<ExpenseRequestVersionEntity> versionRef = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        List<BudgetControlPolicy> policies = List.of(new BlockBudgetControlPolicy(),
                new WarnBudgetControlPolicy(), new ApprovalBudgetControlPolicy());
        service = new ExpenseSubmitAppService(expenseRequestRepository, versionRepository,
                fundEventRepository, budgetVersionRepository, budgetLineRepository,
                budgetBalanceRepository, projectRepository, projectMemberRepository,
                authorizationService, new BudgetControlPolicyRegistry(policies), fundEventAppender,
                workflowLauncher, auditRecorder, outboxAppendPort, new CanonicalJsonHasher(),
                Clock.fixed(NOW, ZoneId.of("Asia/Shanghai")));

        project = new ProjectEntity();
        project.setId(PROJECT_ID);
        project.setPiId(UuidUtils.formatCompact(PI_ID));
        project.setDeptId(UuidUtils.formatCompact(DEPT_ID));
        project.setStatus(ProjectEntity.STATUS_EXECUTING);

        budgetVersion = new BudgetVersionEntity();
        budgetVersion.setId(VERSION_ID);
        budgetVersion.setProjectId(PROJECT_ID);
        budgetVersion.setStatus(BudgetVersionEntity.STATUS_EFFECTIVE);
        budgetVersion.setControlLevel(BudgetVersionEntity.CONTROL_BLOCK);

        line = new BudgetLineEntity();
        line.setId(LINE_ID);
        line.setBudgetVersionId(VERSION_ID);
        line.setControlLevel(BudgetVersionEntity.CONTROL_BLOCK);

        balance = balance("300000.00");

        expense = draftExpense();
        auth = new AuthContext(ACTOR_ID, null, "EMP-001",
                Set.of(FundingPermissions.EXPENSE_SUBMIT), Set.of(), NOW);

        lenient().when(expenseRequestRepository.selectForUpdate(EXPENSE_ID)).thenReturn(Optional.of(expense));
        lenient().when(expenseRequestRepository.update(any())).thenReturn(1);
        lenient().when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        lenient().when(projectMemberRepository.findActiveByProjectId(eq(PROJECT_ID), any()))
                .thenReturn(List.of());
        lenient().when(authorizationService.check(any(AuthContext.class), any(), any()))
                .thenReturn(AuthDecision.allow());
        lenient().when(budgetVersionRepository.findById(VERSION_ID)).thenReturn(Optional.of(budgetVersion));
        lenient().when(budgetLineRepository.findById(LINE_ID)).thenReturn(Optional.of(line));
        lenient().when(budgetBalanceRepository.selectForUpdate(LINE_ID)).thenReturn(Optional.of(balance));
        lenient().when(budgetBalanceRepository.findByBudgetLineId(LINE_ID)).thenReturn(Optional.of(balance));
        lenient().when(versionRepository.findBySubmitIdempotencyKey(anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(versionRef.get()));
        lenient().when(versionRepository.findByExpenseRequestId(EXPENSE_ID)).thenReturn(List.of());
        lenient().doAnswer(invocation -> {
            versionRef.set(invocation.getArgument(0));
            return null;
        }).when(versionRepository).insert(any());
        lenient().when(fundEventRepository.findByIdempotencyKey(anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(reserveRef.get()));
        lenient().when(fundEventAppender.append(any())).thenAnswer(invocation -> {
            FundEventCommand command = invocation.getArgument(0);
            FundEventEntity event = new FundEventEntity();
            event.setId("evt-0001");
            event.setEventType(command.eventType());
            event.setAmount(command.amount());
            event.setBudgetLineId(command.budgetLineId());
            reserveRef.set(event);
            return event;
        });
        lenient().when(workflowLauncher.startExpense(any())).thenReturn(INSTANCE_ID);
        lenient().when(workflowLauncher.findInstanceId(anyString(), any(), any()))
                .thenReturn(Optional.of(INSTANCE_ID));
    }

    @Test
    void tcFund012SubmitCreatesReserveAndReturnsAvailable() {
        ExpenseSubmitted result = service.submit(EXPENSE_ID, cmd("80000.00"),
                KEY, auth);

        assertThat(result.fundEventId()).isEqualTo("evt-0001");
        assertThat(result.availableAmount()).isEqualByComparingTo("220000.00");
        assertThat(result.versionNo()).isEqualTo(1);
        assertThat(result.workflowInstanceId()).isEqualTo(INSTANCE_ID);
        assertThat(expense.getStatus()).isEqualTo(ExpenseRequestEntity.STATUS_IN_REVIEW);
        assertThat(expense.getSubmittedBy()).isEqualTo(UuidUtils.formatCompact(ACTOR_ID));
        assertThat(expense.getAmount()).isEqualByComparingTo("80000.00");

        ArgumentCaptor<FundEventCommand> eventCaptor = ArgumentCaptor.forClass(FundEventCommand.class);
        verify(fundEventAppender).append(eventCaptor.capture());
        assertThat(eventCaptor.getValue().eventType()).isEqualTo(FundEventEntity.TYPE_RESERVE);
        assertThat(eventCaptor.getValue().amount()).isEqualByComparingTo("80000.00");
        assertThat(eventCaptor.getValue().idempotencyKey())
                .isEqualTo("expense:" + EXPENSE_ID + ":RESERVE");

        ArgumentCaptor<ExpenseRequestVersionEntity> versionCaptor =
                ArgumentCaptor.forClass(ExpenseRequestVersionEntity.class);
        verify(versionRepository).insert(versionCaptor.capture());
        assertThat(versionCaptor.getValue().getSubmitIdempotencyKey()).isEqualTo(KEY);
        assertThat(versionCaptor.getValue().getContentHash()).hasSize(64);
        assertThat(versionCaptor.getValue().getVersionNo()).isEqualTo(1);
        verify(workflowLauncher).startExpense(any());
        verify(auditRecorder).record(any());
    }

    @Test
    void tcFund013SuspendedProjectThrowsFund005WithoutEvent() {
        project.setStatus(ProjectEntity.STATUS_SUSPENDED);

        assertThatThrownBy(() -> service.submit(EXPENSE_ID, cmd("80000.00"),
                KEY, auth))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FUND_005);
        verify(fundEventAppender, never()).append(any());
        verify(workflowLauncher, never()).startExpense(any());
    }

    @Test
    void tcFund014DraftBudgetVersionThrowsFund002() {
        budgetVersion.setStatus(BudgetVersionEntity.STATUS_DRAFT);

        assertThatThrownBy(() -> service.submit(EXPENSE_ID, cmd("80000.00"),
                KEY, auth))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FUND_002);
        verify(fundEventAppender, never()).append(any());
    }

    @Test
    void tcFund015LineFromAnotherVersionThrowsFund004() {
        line.setBudgetVersionId(OTHER_VERSION_ID);

        assertThatThrownBy(() -> service.submit(EXPENSE_ID, cmd("80000.00"),
                KEY, auth))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FUND_004);
        verify(fundEventAppender, never()).append(any());
    }

    @Test
    void tcFund016BlockPolicyInsufficientThrowsFund001() {
        budgetVersion.setControlLevel(BudgetVersionEntity.CONTROL_BLOCK);
        line.setControlLevel(BudgetVersionEntity.CONTROL_BLOCK);
        balance = balance("10000.00");
        lenient().when(budgetBalanceRepository.selectForUpdate(LINE_ID)).thenReturn(Optional.of(balance));

        assertThatThrownBy(() -> service.submit(EXPENSE_ID, cmd("80000.00"),
                KEY, auth))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FUND_001);
        verify(fundEventAppender, never()).append(any());
    }

    @Test
    void tcFund017WarnPolicyInsufficientThrowsFund001() {
        line.setControlLevel(BudgetVersionEntity.CONTROL_WARN);
        balance = balance("10000.00");
        lenient().when(budgetBalanceRepository.selectForUpdate(LINE_ID)).thenReturn(Optional.of(balance));

        assertThatThrownBy(() -> service.submit(EXPENSE_ID, cmd("80000.00"),
                KEY, auth))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FUND_001);
    }

    @Test
    void tcFund018ApprovalPolicyInsufficientThrowsFund006() {
        line.setControlLevel(BudgetVersionEntity.CONTROL_APPROVAL);
        balance = balance("10000.00");
        lenient().when(budgetBalanceRepository.selectForUpdate(LINE_ID)).thenReturn(Optional.of(balance));

        assertThatThrownBy(() -> service.submit(EXPENSE_ID, cmd("80000.00"),
                KEY, auth))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FUND_006);
    }

    @Test
    void tcFund019WarnPolicyReturnsRiskWarningWithoutNegativeBalance() {
        line.setControlLevel(BudgetVersionEntity.CONTROL_WARN);
        balance = balance("100000.00");
        lenient().when(budgetBalanceRepository.selectForUpdate(LINE_ID)).thenReturn(Optional.of(balance));

        ExpenseSubmitted result = service.submit(EXPENSE_ID, cmd("85000.00"),
                KEY, auth);

        assertThat(result.riskWarning()).contains("80%");
        assertThat(result.availableAmount()).isEqualByComparingTo("15000.00");
    }

    @Test
    void tcFund020SameIdempotencyKeyReturnsSameEventAndReservesOnce() {
        ExpenseSubmitted first = service.submit(EXPENSE_ID, cmd("80000.00"),
                KEY, auth);
        ExpenseSubmitted second = service.submit(EXPENSE_ID, cmd("80000.00"),
                KEY, auth);

        assertThat(second.fundEventId()).isEqualTo(first.fundEventId());
        verify(fundEventAppender, times(1)).append(any());
        verify(workflowLauncher, times(1)).startExpense(any());
    }

    @Test
    void tcFund022SameKeyDifferentAmountThrowsFund003() {
        service.submit(EXPENSE_ID, cmd("80000.00"), KEY, auth);

        assertThatThrownBy(() -> service.submit(EXPENSE_ID,
                cmd("90000.00"), KEY, auth))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FUND_003);
    }

    @Test
    void tcFund023ZeroAmountThrowsCommon400() {
        assertThatThrownBy(() -> service.submit(EXPENSE_ID, cmd("0.00"),
                KEY, auth))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.COMMON_400);
    }

    @Test
    void tcFund024MissingAttachmentsThrowsCommon422() {
        SubmitExpenseCmd missing = new SubmitExpenseCmd(1, VERSION_ID, LINE_ID,
                new BigDecimal("80000.00"), "采购高性能计算节点", List.of(), null);

        assertThatThrownBy(() -> service.submit(EXPENSE_ID, missing, KEY, auth))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.COMMON_422);
    }

    @Test
    void tcFund027SupplementResubmitKeepsSingleReserve() {
        expense.setStatus(ExpenseRequestEntity.STATUS_SUPPLEMENT);
        FundEventEntity existing = new FundEventEntity();
        existing.setId("evt-reserve-1");
        existing.setEventType(FundEventEntity.TYPE_RESERVE);
        existing.setAmount(new BigDecimal("80000.00"));
        existing.setBudgetLineId(LINE_ID);
        reserveRef.set(existing);
        balance.setReservedAmount(new BigDecimal("80000.00"));
        ExpenseRequestVersionEntity oldVersion = new ExpenseRequestVersionEntity();
        oldVersion.setId("66666666666666666666666666666666");
        oldVersion.setVersionNo(1);
        lenient().when(versionRepository.findByExpenseRequestId(EXPENSE_ID)).thenReturn(List.of(oldVersion));

        ExpenseSubmitted result = service.submit(EXPENSE_ID, cmd("80000.00"),
                "resubmit-key", auth);

        assertThat(result.fundEventId()).isEqualTo("evt-reserve-1");
        assertThat(result.versionNo()).isEqualTo(2);
        assertThat(result.availableAmount()).isEqualByComparingTo("220000.00");
        assertThat(expense.getStatus()).isEqualTo(ExpenseRequestEntity.STATUS_IN_REVIEW);
        verify(fundEventAppender, never()).append(any());
        verify(workflowLauncher).startExpense(any());
    }

    private static SubmitExpenseCmd cmd(String amount) {
        return new SubmitExpenseCmd(1, VERSION_ID, LINE_ID, new BigDecimal(amount),
                "采购高性能计算节点", List.of("file-5"), null);
    }

    private static BudgetBalanceEntity balance(String approved) {
        BudgetBalanceEntity balance = new BudgetBalanceEntity();
        balance.setId("77777777777777777777777777777777");
        balance.setBudgetLineId(LINE_ID);
        balance.setApprovedAmount(new BigDecimal(approved));
        balance.setReservedAmount(BigDecimal.ZERO);
        balance.setApprovedUnpaidAmount(BigDecimal.ZERO);
        balance.setPaidAmount(BigDecimal.ZERO);
        return balance;
    }

    private static ExpenseRequestEntity draftExpense() {
        ExpenseRequestEntity expense = new ExpenseRequestEntity();
        expense.setId(EXPENSE_ID);
        expense.setRequestNo("EXP-2026-0001");
        expense.setProjectId(PROJECT_ID);
        expense.setBudgetVersionId(VERSION_ID);
        expense.setBudgetLineId(LINE_ID);
        expense.setAmount(new BigDecimal("80000.00"));
        expense.setPurpose("采购高性能计算节点");
        expense.setPayeeName("某科技有限公司");
        expense.setPayeeBankAccountMasked("****1234");
        expense.setStatus(ExpenseRequestEntity.STATUS_DRAFT);
        expense.setVersion(1);
        return expense;
    }
}
