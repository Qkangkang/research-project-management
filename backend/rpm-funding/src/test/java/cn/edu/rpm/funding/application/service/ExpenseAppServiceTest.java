package cn.edu.rpm.funding.application.service;

import cn.edu.rpm.audit.domain.model.AuditEvent;
import cn.edu.rpm.audit.domain.service.AuditRecorder;
import cn.edu.rpm.common.domain.service.NumberGenerator;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.common.util.UuidUtils;
import cn.edu.rpm.funding.application.dto.CreateExpenseCmd;
import cn.edu.rpm.funding.application.dto.ExpenseView;
import cn.edu.rpm.funding.domain.model.FundingPermissions;
import cn.edu.rpm.funding.infrastructure.entity.BudgetLineEntity;
import cn.edu.rpm.funding.infrastructure.entity.BudgetVersionEntity;
import cn.edu.rpm.funding.infrastructure.entity.ExpenseRequestEntity;
import cn.edu.rpm.funding.infrastructure.repository.BudgetLineRepository;
import cn.edu.rpm.funding.infrastructure.repository.BudgetVersionRepository;
import cn.edu.rpm.funding.infrastructure.repository.ExpenseRequestRepository;
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
import static org.mockito.Mockito.when;

/**
 * {@link ExpenseAppService} 纯单元测试（T-FUND-019；TC-FUND-039、TC-FUND-069）。
 */
@ExtendWith(MockitoExtension.class)
class ExpenseAppServiceTest {

    private static final String PROJECT_ID = "11111111111111111111111111111111";
    private static final String VERSION_ID = "22222222222222222222222222222222";
    private static final String OTHER_VERSION_ID = "23232323232323232323232323232323";
    private static final String LINE_ID = "33333333333333333333333333333333";
    private static final UUID ACTOR_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID PI_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID DEPT_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final Instant NOW = Instant.parse("2026-11-18T02:00:00Z");

    @Mock
    private ExpenseRequestRepository expenseRequestRepository;

    @Mock
    private BudgetVersionRepository budgetVersionRepository;

    @Mock
    private BudgetLineRepository budgetLineRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectMemberRepository projectMemberRepository;

    @Mock
    private AuthorizationService authorizationService;

    @Mock
    private AuditRecorder auditRecorder;

    @Mock
    private NumberGenerator numberGenerator;

    private ExpenseAppService service;

    private AuthContext auth;

    private ProjectEntity project;

    private BudgetVersionEntity budgetVersion;

    private BudgetLineEntity budgetLine;

    @BeforeEach
    void setUp() {
        service = new ExpenseAppService(expenseRequestRepository, budgetVersionRepository,
                budgetLineRepository, projectRepository, projectMemberRepository,
                authorizationService, auditRecorder, numberGenerator,
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
        budgetLine = new BudgetLineEntity();
        budgetLine.setId(LINE_ID);
        budgetLine.setBudgetVersionId(VERSION_ID);
        auth = new AuthContext(ACTOR_ID, null, "EMP-001",
                Set.of(FundingPermissions.EXPENSE_CREATE, FundingPermissions.VIEW),
                Set.of(), NOW);
        lenient().when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        lenient().when(projectMemberRepository.findActiveByProjectId(eq(PROJECT_ID), any()))
                .thenReturn(List.of());
        lenient().when(authorizationService.check(any(AuthContext.class), any(), any()))
                .thenReturn(AuthDecision.allow());
        lenient().when(budgetVersionRepository.findById(VERSION_ID))
                .thenReturn(Optional.of(budgetVersion));
        lenient().when(budgetLineRepository.findById(LINE_ID)).thenReturn(Optional.of(budgetLine));
        lenient().when(numberGenerator.next(ExpenseAppService.RULE_EXPENSE_REQUEST_NO))
                .thenReturn("EXP-2026-0001");
    }

    @Test
    void createDraftSavesMaskedAccountAndActualOperator() {
        ExpenseView view = service.createDraft(PROJECT_ID, cmd("80000.00", "****1234"), auth);

        assertThat(view.status()).isEqualTo(ExpenseRequestEntity.STATUS_DRAFT);
        assertThat(view.accountMasked()).isEqualTo("****1234");
        assertThat(view.amount()).isEqualByComparingTo("80000.00");
        ArgumentCaptor<ExpenseRequestEntity> captor = ArgumentCaptor.forClass(ExpenseRequestEntity.class);
        verify(expenseRequestRepository).insert(captor.capture());
        ExpenseRequestEntity saved = captor.getValue();
        assertThat(saved.getCreatedBy()).isEqualTo(UuidUtils.formatCompact(ACTOR_ID));
        assertThat(saved.getPayeeBankAccountEncrypted()).isEqualTo("cipher-text");
        assertThat(saved.getStatus()).isEqualTo(ExpenseRequestEntity.STATUS_DRAFT);
        verify(auditRecorder).record(any(AuditEvent.class));
    }

    @Test
    void amountWithThreeDecimalsThrowsCommon400WithoutInsert() {
        assertThatThrownBy(() -> service.createDraft(PROJECT_ID, cmd("80000.001", "****1234"), auth))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.COMMON_400);
        verify(expenseRequestRepository, never()).insert(any(ExpenseRequestEntity.class));
    }

    @Test
    void detailMissingExpenseThrowsCommon404WithoutLeakingProject() {
        when(expenseRequestRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.detail("missing", auth))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.COMMON_404);
        verify(projectRepository, never()).findById(any());
    }

    @Test
    void detailReturnsMaskedAccountOnly() {
        ExpenseRequestEntity expense = draftExpense("****1234");
        when(expenseRequestRepository.findById(expense.getId())).thenReturn(Optional.of(expense));

        ExpenseView view = service.detail(expense.getId(), auth);

        assertThat(view.accountMasked()).isEqualTo("****1234");
        assertThat(view.payeeName()).isEqualTo("某科技有限公司");
    }

    @Test
    void budgetLineFromAnotherVersionThrowsFund004() {
        budgetLine.setBudgetVersionId(OTHER_VERSION_ID);

        assertThatThrownBy(() -> service.createDraft(PROJECT_ID, cmd("80000.00", "****1234"), auth))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FUND_004);
        verify(expenseRequestRepository, never()).insert(any(ExpenseRequestEntity.class));
    }

    @Test
    void createIdempotencyKeyReturnsExistingWithoutInsert() {
        ExpenseRequestEntity existing = draftExpense("****1234");
        existing.setIdempotencyKey("create-key-1");
        when(expenseRequestRepository.findByIdempotencyKey("create-key-1"))
                .thenReturn(Optional.of(existing));

        CreateExpenseCmd idempotentCmd = new CreateExpenseCmd(VERSION_ID, LINE_ID,
                new BigDecimal("80000.00"), "采购高性能计算节点", "某科技有限公司",
                "****1234", "cipher-text", "某银行某支行", "create-key-1");
        ExpenseView view = service.createDraft(PROJECT_ID, idempotentCmd, auth);

        assertThat(view.expenseId()).isEqualTo(existing.getId());
        verify(expenseRequestRepository, never()).insert(any(ExpenseRequestEntity.class));
    }

    private static CreateExpenseCmd cmd(String amount, String masked) {
        return new CreateExpenseCmd(VERSION_ID, LINE_ID, new BigDecimal(amount),
                "采购高性能计算节点", "某科技有限公司", masked, "cipher-text",
                "某银行某支行", null);
    }

    private static ExpenseRequestEntity draftExpense(String masked) {
        ExpenseRequestEntity expense = new ExpenseRequestEntity();
        expense.setId("44444444444444444444444444444444");
        expense.setRequestNo("EXP-2026-0001");
        expense.setProjectId(PROJECT_ID);
        expense.setBudgetVersionId(VERSION_ID);
        expense.setBudgetLineId(LINE_ID);
        expense.setAmount(new BigDecimal("80000.00"));
        expense.setPurpose("采购高性能计算节点");
        expense.setPayeeName("某科技有限公司");
        expense.setPayeeBankAccountMasked(masked);
        expense.setPayeeBankAccountEncrypted("cipher-text");
        expense.setStatus(ExpenseRequestEntity.STATUS_DRAFT);
        return expense;
    }
}
