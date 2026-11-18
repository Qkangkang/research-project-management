package cn.edu.rpm.funding.application.service;

import cn.edu.rpm.audit.domain.model.AuditEvent;
import cn.edu.rpm.audit.domain.service.AuditRecorder;
import cn.edu.rpm.common.domain.port.OutboxAppendPort;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.common.util.CanonicalJsonHasher;
import cn.edu.rpm.common.util.UuidUtils;
import cn.edu.rpm.funding.application.dto.SpecialApprovalCmd;
import cn.edu.rpm.funding.application.dto.SpecialApprovalView;
import cn.edu.rpm.funding.domain.model.FundingPermissions;
import cn.edu.rpm.funding.domain.port.ExpenseWorkflowLauncher;
import cn.edu.rpm.funding.infrastructure.entity.ExpenseRequestEntity;
import cn.edu.rpm.funding.infrastructure.entity.ExpenseRequestVersionEntity;
import cn.edu.rpm.funding.infrastructure.entity.ExpenseSpecialApprovalEntity;
import cn.edu.rpm.funding.infrastructure.repository.ExpenseRequestRepository;
import cn.edu.rpm.funding.infrastructure.repository.ExpenseRequestVersionRepository;
import cn.edu.rpm.funding.infrastructure.repository.ExpenseSpecialApprovalRepository;
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
 * {@link SpecialApprovalAppService} 纯单元测试（T-FUND-024；TC-FUND-102、TC-FUND-107）。
 */
@ExtendWith(MockitoExtension.class)
class SpecialApprovalAppServiceTest {

    private static final String PROJECT_ID = "11111111111111111111111111111111";
    private static final String EXPENSE_ID = "22222222222222222222222222222222";
    private static final String VERSION_ID = "33333333333333333333333333333333";
    private static final String LINE_ID = "44444444444444444444444444444444";
    private static final String INSTANCE_ID = "55555555555555555555555555555555";
    private static final UUID ACTOR_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID PI_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID DEPT_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final Instant NOW = Instant.parse("2026-11-18T02:00:00Z");
    private static final String REASON = "该项目预算不足但属于院级重点任务需要专项支持并补充说明材料";

    @Mock
    private ExpenseRequestRepository expenseRequestRepository;

    @Mock
    private ExpenseRequestVersionRepository versionRepository;

    @Mock
    private ExpenseSpecialApprovalRepository specialApprovalRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectMemberRepository projectMemberRepository;

    @Mock
    private AuthorizationService authorizationService;

    @Mock
    private ExpenseWorkflowLauncher workflowLauncher;

    @Mock
    private AuditRecorder auditRecorder;

    @Mock
    private OutboxAppendPort outboxAppendPort;

    private SpecialApprovalAppService service;

    private AuthContext auth;

    private ExpenseRequestEntity expense;

    @BeforeEach
    void setUp() {
        service = new SpecialApprovalAppService(expenseRequestRepository, versionRepository,
                specialApprovalRepository, projectRepository, projectMemberRepository,
                authorizationService, workflowLauncher, auditRecorder, outboxAppendPort,
                new CanonicalJsonHasher(), Clock.fixed(NOW, ZoneId.of("Asia/Shanghai")),
                new BigDecimal("100000.00"));
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
        expense.setPurpose("采购高性能计算节点");
        expense.setStatus(ExpenseRequestEntity.STATUS_DRAFT);
        expense.setVersion(2);
        auth = new AuthContext(ACTOR_ID, null, "EMP-001", Set.of(FundingPermissions.EXPENSE_EDIT),
                Set.of(), NOW);
        lenient().when(expenseRequestRepository.selectForUpdate(EXPENSE_ID)).thenReturn(Optional.of(expense));
        lenient().when(expenseRequestRepository.findById(EXPENSE_ID)).thenReturn(Optional.of(expense));
        lenient().when(expenseRequestRepository.update(any())).thenReturn(1);
        lenient().when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        lenient().when(projectMemberRepository.findActiveByProjectId(eq(PROJECT_ID), any()))
                .thenReturn(List.of());
        lenient().when(authorizationService.check(any(AuthContext.class), any(), any()))
                .thenReturn(AuthDecision.allow());
        lenient().when(specialApprovalRepository.findByExpenseRequestIdAndStatus(
                EXPENSE_ID, ExpenseSpecialApprovalEntity.STATUS_PENDING)).thenReturn(List.of());
        lenient().when(versionRepository.findByExpenseRequestId(EXPENSE_ID)).thenReturn(List.of());
        lenient().when(workflowLauncher.startSpecialApproval(any())).thenReturn(INSTANCE_ID);
    }

    @Test
    void tcFund102MissingReasonThrowsCommon400() {
        SpecialApprovalCmd cmd = new SpecialApprovalCmd("OVER_BUDGET", new BigDecimal("20000.00"),
                "太短", List.of(), "key-1");

        assertThatThrownBy(() -> service.initiate(EXPENSE_ID, cmd, auth))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.COMMON_400);
        verify(specialApprovalRepository, never()).insert(any());
    }

    @Test
    void invalidSpecialTypeThrowsCommon400() {
        SpecialApprovalCmd cmd = new SpecialApprovalCmd("UNKNOWN", new BigDecimal("20000.00"),
                REASON, List.of(), "key-1");

        assertThatThrownBy(() -> service.initiate(EXPENSE_ID, cmd, auth))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.COMMON_400);
    }

    @Test
    void tcFund107DuplicatePendingApprovalThrowsCommon409() {
        ExpenseSpecialApprovalEntity pending = new ExpenseSpecialApprovalEntity();
        pending.setId("66666666666666666666666666666666");
        pending.setStatus(ExpenseSpecialApprovalEntity.STATUS_PENDING);
        when(specialApprovalRepository.findByExpenseRequestIdAndStatus(
                EXPENSE_ID, ExpenseSpecialApprovalEntity.STATUS_PENDING)).thenReturn(List.of(pending));

        assertThatThrownBy(() -> service.initiate(EXPENSE_ID, cmd("20000.00"), auth))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.COMMON_409);
        verify(versionRepository, never()).insert(any());
        verify(workflowLauncher, never()).startSpecialApproval(any());
    }

    @Test
    void initiateCreatesSnapshotVersionAndStartsSpecialWorkflow() {
        SpecialApprovalView view = service.initiate(EXPENSE_ID, cmd("20000.00"), auth);

        assertThat(view.status()).isEqualTo(ExpenseSpecialApprovalEntity.STATUS_PENDING);
        assertThat(view.wfInstanceId()).isEqualTo(INSTANCE_ID);
        assertThat(view.overAmount()).isEqualByComparingTo("20000.00");
        assertThat(view.specialType()).isEqualTo(ExpenseSpecialApprovalEntity.TYPE_OVER_BUDGET);
        ArgumentCaptor<ExpenseRequestVersionEntity> versionCaptor =
                ArgumentCaptor.forClass(ExpenseRequestVersionEntity.class);
        verify(versionRepository).insert(versionCaptor.capture());
        assertThat(versionCaptor.getValue().getVersionNo()).isEqualTo(1);
        assertThat(versionCaptor.getValue().getContentHash()).hasSize(64);
        assertThat(expense.getCurrentVersionId()).isEqualTo(versionCaptor.getValue().getId());

        ArgumentCaptor<ExpenseWorkflowLauncher.ExpenseWorkflowStart> startCaptor =
                ArgumentCaptor.forClass(ExpenseWorkflowLauncher.ExpenseWorkflowStart.class);
        verify(workflowLauncher).startSpecialApproval(startCaptor.capture());
        assertThat(startCaptor.getValue().bizType()).isEqualTo("SPECIAL_APPROVAL");
        assertThat(startCaptor.getValue().variables()).containsEntry("countersignRequired", false);
        verify(specialApprovalRepository).insert(any(ExpenseSpecialApprovalEntity.class));
        verify(auditRecorder).record(any(AuditEvent.class));
    }

    @Test
    void amountOverLimitPassesCountersignVariableAndAudits() {
        service.initiate(EXPENSE_ID, cmd("150000.00"), auth);

        ArgumentCaptor<ExpenseWorkflowLauncher.ExpenseWorkflowStart> startCaptor =
                ArgumentCaptor.forClass(ExpenseWorkflowLauncher.ExpenseWorkflowStart.class);
        verify(workflowLauncher).startSpecialApproval(startCaptor.capture());
        assertThat(startCaptor.getValue().variables()).containsEntry("countersignRequired", true);
        ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditRecorder, org.mockito.Mockito.times(2)).record(auditCaptor.capture());
        assertThat(auditCaptor.getAllValues().stream().map(AuditEvent::action))
                .contains(SpecialApprovalAppService.AUDIT_SPECIAL_COUNTERSIGN);
    }

    @Test
    void withoutEditPermissionThrowsCommon403() {
        when(authorizationService.check(any(AuthContext.class), any(), any()))
                .thenReturn(AuthDecision.deny(ErrorCode.COMMON_403));

        assertThatThrownBy(() -> service.initiate(EXPENSE_ID, cmd("20000.00"), auth))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.COMMON_403);
        verify(specialApprovalRepository, never()).insert(any());
    }

    private static SpecialApprovalCmd cmd(String overAmount) {
        return new SpecialApprovalCmd("OVER_BUDGET", new BigDecimal(overAmount), REASON,
                List.of("file-1"), "special-key-1");
    }
}
