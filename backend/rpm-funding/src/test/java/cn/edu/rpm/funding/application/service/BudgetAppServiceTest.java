package cn.edu.rpm.funding.application.service;

import cn.edu.rpm.audit.domain.model.AuditEvent;
import cn.edu.rpm.audit.domain.service.AuditRecorder;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.common.util.UuidUtils;
import cn.edu.rpm.funding.application.dto.BudgetLineCmd;
import cn.edu.rpm.funding.application.dto.BudgetOverview;
import cn.edu.rpm.funding.application.dto.BudgetVersionDetail;
import cn.edu.rpm.funding.application.dto.CreateBudgetDraftCmd;
import cn.edu.rpm.funding.domain.model.FundEventCommand;
import cn.edu.rpm.funding.domain.model.FundingPermissions;
import cn.edu.rpm.funding.domain.service.FundEventAppender;
import cn.edu.rpm.funding.domain.service.FundEventProjection;
import cn.edu.rpm.funding.infrastructure.entity.BudgetBalanceEntity;
import cn.edu.rpm.funding.infrastructure.entity.BudgetLineEntity;
import cn.edu.rpm.funding.infrastructure.entity.BudgetVersionEntity;
import cn.edu.rpm.funding.infrastructure.entity.FundEventEntity;
import cn.edu.rpm.funding.infrastructure.repository.BudgetBalanceRepository;
import cn.edu.rpm.funding.infrastructure.repository.BudgetLineRepository;
import cn.edu.rpm.funding.infrastructure.repository.BudgetVersionRepository;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link BudgetAppService} 纯单元测试（T-FUND-017；TC-FUND-001～TC-FUND-011）。
 *
 * <p>覆盖草稿校验、项目状态与授权、首次激活、重复激活拒绝、预算总览查询、
 * 预算变更激活（替代旧版本）与未关联变更单拒绝。资金事件投影的正确性由
 * {@link cn.edu.rpm.funding.domain.service.FundEventAppenderTest} 覆盖，本测试以
 * 假追加器按同一矩阵更新内存余额，以断言批准额变化。</p>
 */
@ExtendWith(MockitoExtension.class)
class BudgetAppServiceTest {

    private static final String PROJECT_ID = "8d7c6b5a4e3f4a2b9c1d0e2f3a4b5c6d";
    private static final String VERSION_ID = "33333333333333333333333333333333";
    private static final String OLD_VERSION_ID = "34343434343434343434343434343434";
    private static final String LINE_EQUIPMENT = "44444444444444444444444444444444";
    private static final String LINE_TRAVEL = "55555555555555555555555555555555";
    private static final String CHANGE_REQUEST_ID = "66666666666666666666666666666666";
    private static final String CHANGE_VERSION_ID = "67676767676767676767676767676767";
    private static final UUID ACTOR_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID PI_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID DEPT_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final Instant NOW = Instant.parse("2026-11-18T02:00:00Z");

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
    private AuditRecorder auditRecorder;

    @Mock
    private FundEventAppender fundEventAppender;

    private BudgetAppService service;

    private AuthContext auth;

    private ProjectEntity project;

    @BeforeEach
    void setUp() {
        service = new BudgetAppService(budgetVersionRepository, budgetLineRepository,
                budgetBalanceRepository, projectRepository, projectMemberRepository,
                authorizationService, auditRecorder, fundEventAppender,
                Clock.fixed(NOW, ZoneId.of("Asia/Shanghai")));
        project = new ProjectEntity();
        project.setId(PROJECT_ID);
        project.setPiId(UuidUtils.formatCompact(PI_ID));
        project.setDeptId(UuidUtils.formatCompact(DEPT_ID));
        project.setStatus(ProjectEntity.STATUS_EXECUTING);
        auth = new AuthContext(ACTOR_ID, null, "EMP-001",
                Set.of(FundingPermissions.BUDGET_EDIT, FundingPermissions.BUDGET_SUBMIT,
                        FundingPermissions.VIEW),
                Set.of(), NOW);
        lenient().when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        lenient().when(projectMemberRepository.findActiveByProjectId(eq(PROJECT_ID), any()))
                .thenReturn(List.of());
        lenient().when(authorizationService.check(any(AuthContext.class), any(), any()))
                .thenReturn(AuthDecision.allow());
    }

    @Test
    void tcFund001CreateDraftCreatesVersionLinesAndZeroBalances() {
        when(budgetVersionRepository.findByProjectId(PROJECT_ID)).thenReturn(List.of());

        BudgetVersionDetail detail = service.createDraft(PROJECT_ID, fourLineBudget("1200000.00"), auth);

        assertThat(detail.status()).isEqualTo(BudgetVersionEntity.STATUS_DRAFT);
        assertThat(detail.versionNo()).isEqualTo(1);
        assertThat(detail.totalAmount()).isEqualByComparingTo("1200000.00");
        assertThat(detail.lines()).hasSize(4);
        assertThat(detail.lines()).allSatisfy(line -> {
            assertThat(line.approvedAmount()).isEqualByComparingTo("0.00");
            assertThat(line.reservedAmount()).isEqualByComparingTo("0.00");
            assertThat(line.availableAmount()).isEqualByComparingTo("0.00");
        });
        verify(budgetVersionRepository).insert(any(BudgetVersionEntity.class));
        verify(budgetLineRepository, times(4)).insert(any(BudgetLineEntity.class));
        verify(budgetBalanceRepository, times(4)).insert(any(BudgetBalanceEntity.class));
        verify(auditRecorder).record(any(AuditEvent.class));
    }

    @Test
    void tcFund002TotalMismatchThrowsFund004WithoutInsert() {
        assertThatThrownBy(() -> service.createDraft(PROJECT_ID, fourLineBudget("1199999.00"), auth))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FUND_004);
        verify(budgetVersionRepository, never()).insert(any(BudgetVersionEntity.class));
    }

    @Test
    void tcFund003DuplicateSubjectThrowsFund004() {
        CreateBudgetDraftCmd cmd = new CreateBudgetDraftCmd("GRANT", "BLOCK",
                LocalDate.of(2026, 1, 1), LocalDate.of(2028, 12, 31),
                List.of(new BudgetLineCmd("EQUIPMENT", "设备费", new BigDecimal("300000.00")),
                        new BudgetLineCmd("EQUIPMENT", "设备费重复", new BigDecimal("200000.00"))),
                new BigDecimal("500000.00"));

        assertThatThrownBy(() -> service.createDraft(PROJECT_ID, cmd, auth))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FUND_004);
        verify(budgetLineRepository, never()).insert(any(BudgetLineEntity.class));
    }

    @Test
    void tcFund004NonPositiveAmountThrowsFund004() {
        CreateBudgetDraftCmd cmd = new CreateBudgetDraftCmd("GRANT", "BLOCK",
                LocalDate.of(2026, 1, 1), LocalDate.of(2028, 12, 31),
                List.of(new BudgetLineCmd("EQUIPMENT", "设备费", new BigDecimal("0.00"))),
                new BigDecimal("0.00"));

        assertThatThrownBy(() -> service.createDraft(PROJECT_ID, cmd, auth))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FUND_004);
    }

    @Test
    void tcFund005CompletedProjectThrowsProj002() {
        project.setStatus(ProjectEntity.STATUS_COMPLETED);

        assertThatThrownBy(() -> service.createDraft(PROJECT_ID, fourLineBudget("1200000.00"), auth))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PROJ_002);
        verify(budgetVersionRepository, never()).insert(any(BudgetVersionEntity.class));
    }

    @Test
    void createDraftDeniedByAuthorizationReturns403() {
        when(authorizationService.check(any(AuthContext.class), any(), any()))
                .thenReturn(AuthDecision.deny(ErrorCode.COMMON_403));

        assertThatThrownBy(() -> service.createDraft(PROJECT_ID, fourLineBudget("1200000.00"), auth))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.COMMON_403);
    }

    @Test
    void tcFund007ActivateSetsEffectiveAndApprovedAmounts() {
        BudgetVersionEntity version = draftVersion(VERSION_ID);
        BudgetLineEntity equipment = line(LINE_EQUIPMENT, "EQUIPMENT", "300000.00");
        BudgetLineEntity travel = line(LINE_TRAVEL, "TRAVEL", "150000.00");
        Map<String, BudgetBalanceEntity> balances = new HashMap<>();
        balances.put(LINE_EQUIPMENT, zeroBalance(LINE_EQUIPMENT));
        balances.put(LINE_TRAVEL, zeroBalance(LINE_TRAVEL));
        when(budgetVersionRepository.findById(VERSION_ID)).thenReturn(Optional.of(version));
        when(budgetVersionRepository.findEffectiveByProjectId(PROJECT_ID)).thenReturn(Optional.empty());
        when(budgetLineRepository.findByBudgetVersionId(VERSION_ID))
                .thenReturn(List.of(equipment, travel));
        when(budgetBalanceRepository.findByBudgetLineIds(List.of(LINE_EQUIPMENT, LINE_TRAVEL)))
                .thenReturn(List.of(balances.get(LINE_EQUIPMENT), balances.get(LINE_TRAVEL)));
        when(budgetVersionRepository.update(version)).thenReturn(1);
        stubProjectionApplyingAppender(balances);

        BudgetVersionDetail detail = service.activate(VERSION_ID, auth);

        assertThat(detail.status()).isEqualTo(BudgetVersionEntity.STATUS_EFFECTIVE);
        assertThat(detail.lines()).extracting(line -> line.approvedAmount().toPlainString())
                .containsExactly("300000.00", "150000.00");
        assertThat(version.getApprovedBy()).isEqualTo(UuidUtils.formatCompact(ACTOR_ID));
        verify(fundEventAppender, times(2)).append(any(FundEventCommand.class));
        ArgumentCaptor<FundEventCommand> commandCaptor =
                ArgumentCaptor.forClass(FundEventCommand.class);
        verify(fundEventAppender, times(2)).append(commandCaptor.capture());
        assertThat(commandCaptor.getAllValues())
                .allSatisfy(command -> {
                    assertThat(command.eventType()).isEqualTo(FundEventEntity.TYPE_ADJUST);
                    assertThat(command.idempotencyKey()).contains(VERSION_ID);
                });
        verify(auditRecorder).record(any(AuditEvent.class));
    }

    @Test
    void tcFund008SecondActivationRejectedAndOldVersionStaysEffective() {
        BudgetVersionEntity version = draftVersion(VERSION_ID);
        BudgetVersionEntity old = draftVersion(OLD_VERSION_ID);
        old.setStatus(BudgetVersionEntity.STATUS_EFFECTIVE);
        when(budgetVersionRepository.findById(VERSION_ID)).thenReturn(Optional.of(version));
        when(budgetVersionRepository.findEffectiveByProjectId(PROJECT_ID)).thenReturn(Optional.of(old));

        assertThatThrownBy(() -> service.activate(VERSION_ID, auth))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FUND_007);
        assertThat(old.getStatus()).isEqualTo(BudgetVersionEntity.STATUS_EFFECTIVE);
        assertThat(version.getStatus()).isEqualTo(BudgetVersionEntity.STATUS_DRAFT);
        verify(budgetVersionRepository, never()).update(any(BudgetVersionEntity.class));
        verify(fundEventAppender, never()).append(any(FundEventCommand.class));
    }

    @Test
    void tcFund009ListReturnsEffectiveBudgetWithFourAmounts() {
        BudgetVersionEntity effective = draftVersion(VERSION_ID);
        effective.setStatus(BudgetVersionEntity.STATUS_EFFECTIVE);
        effective.setTotalAmount(new BigDecimal("1200000.00"));
        BudgetLineEntity line = line(LINE_EQUIPMENT, "EQUIPMENT", "1200000.00");
        BudgetBalanceEntity balance = zeroBalance(LINE_EQUIPMENT);
        balance.setApprovedAmount(new BigDecimal("1200000.00"));
        balance.setReservedAmount(new BigDecimal("80000.00"));
        balance.setApprovedUnpaidAmount(new BigDecimal("120000.00"));
        balance.setPaidAmount(new BigDecimal("300000.00"));
        when(budgetVersionRepository.findByProjectId(PROJECT_ID)).thenReturn(List.of(effective));
        when(budgetVersionRepository.findEffectiveByProjectId(PROJECT_ID)).thenReturn(Optional.of(effective));
        when(budgetLineRepository.findByBudgetVersionId(VERSION_ID)).thenReturn(List.of(line));
        when(budgetBalanceRepository.findByBudgetLineIds(List.of(LINE_EQUIPMENT)))
                .thenReturn(List.of(balance));

        BudgetOverview overview = service.list(PROJECT_ID, auth);

        assertThat(overview.versions()).hasSize(1);
        assertThat(overview.effectiveBudget()).isNotNull();
        assertThat(overview.effectiveBudget().budgetVersionId()).isEqualTo(VERSION_ID);
        assertThat(overview.effectiveBudget().lines()).hasSize(1);
        assertThat(overview.effectiveBudget().lines().get(0).availableAmount())
                .isEqualByComparingTo("700000.00");
        assertThat(overview.effectiveBudget().lines().get(0).approvedUnpaidAmount())
                .isEqualByComparingTo("120000.00");
    }

    @Test
    void tcFund010ListReturnsEmptyWhenNoBudget() {
        when(budgetVersionRepository.findByProjectId(PROJECT_ID)).thenReturn(List.of());
        when(budgetVersionRepository.findEffectiveByProjectId(PROJECT_ID)).thenReturn(Optional.empty());

        BudgetOverview overview = service.list(PROJECT_ID, auth);

        assertThat(overview.versions()).isEmpty();
        assertThat(overview.effectiveBudget()).isNull();
    }

    @Test
    void tcFund006ApplyChangeWithoutChangeLinkThrows422() {
        assertThatThrownBy(() -> service.applyApprovedChange(PROJECT_ID, VERSION_ID, null, null, ACTOR_ID))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.COMMON_422);
        verify(budgetVersionRepository, never()).findById(any());
    }

    @Test
    void applyApprovedChangeSupersedesOldVersionAndActivatesDraft() {
        BudgetVersionEntity version = draftVersion(VERSION_ID);
        BudgetVersionEntity old = draftVersion(OLD_VERSION_ID);
        old.setStatus(BudgetVersionEntity.STATUS_EFFECTIVE);
        BudgetLineEntity line = line(LINE_EQUIPMENT, "EQUIPMENT", "300000.00");
        BudgetBalanceEntity balance = zeroBalance(LINE_EQUIPMENT);
        Map<String, BudgetBalanceEntity> balances = Map.of(LINE_EQUIPMENT, balance);
        when(budgetVersionRepository.findById(VERSION_ID)).thenReturn(Optional.of(version));
        when(budgetVersionRepository.findEffectiveByProjectId(PROJECT_ID)).thenReturn(Optional.of(old));
        when(budgetLineRepository.findByBudgetVersionId(VERSION_ID)).thenReturn(List.of(line));
        when(budgetBalanceRepository.findByBudgetLineIds(List.of(LINE_EQUIPMENT)))
                .thenReturn(List.of(balance));
        when(budgetVersionRepository.update(any(BudgetVersionEntity.class))).thenReturn(1);
        stubProjectionApplyingAppender(balances);

        BudgetVersionDetail detail = service.applyApprovedChange(PROJECT_ID, VERSION_ID,
                CHANGE_REQUEST_ID, CHANGE_VERSION_ID, ACTOR_ID);

        assertThat(detail.status()).isEqualTo(BudgetVersionEntity.STATUS_EFFECTIVE);
        assertThat(version.getChangeRequestId()).isEqualTo(CHANGE_REQUEST_ID);
        assertThat(old.getStatus()).isEqualTo(BudgetVersionEntity.STATUS_SUPERSEDED);
        assertThat(balance.getApprovedAmount()).isEqualByComparingTo("300000.00");
        verify(fundEventAppender).append(any(FundEventCommand.class));
    }

    private void stubProjectionApplyingAppender(Map<String, BudgetBalanceEntity> balances) {
        lenient().when(fundEventAppender.append(any(FundEventCommand.class))).thenAnswer(invocation -> {
            FundEventCommand command = invocation.getArgument(0);
            FundEventProjection.apply(balances.get(command.budgetLineId()), command.eventType(),
                    command.amount(), null);
            return null;
        });
    }

    private static BudgetVersionEntity draftVersion(String id) {
        BudgetVersionEntity version = new BudgetVersionEntity();
        version.setId(id);
        version.setProjectId(PROJECT_ID);
        version.setVersionNo(VERSION_ID.equals(id) ? 2 : 1);
        version.setTotalAmount(new BigDecimal("1200000.00"));
        version.setSourceType(BudgetVersionEntity.SOURCE_GRANT);
        version.setControlLevel(BudgetVersionEntity.CONTROL_BLOCK);
        version.setStatus(BudgetVersionEntity.STATUS_DRAFT);
        version.setEffectiveFrom(LocalDate.of(2026, 1, 1));
        version.setEffectiveTo(LocalDate.of(2028, 12, 31));
        return version;
    }

    private static BudgetLineEntity line(String id, String code, String amount) {
        BudgetLineEntity line = new BudgetLineEntity();
        line.setId(id);
        line.setBudgetVersionId(VERSION_ID);
        line.setSubjectCode(code);
        line.setSubjectName(code);
        line.setAmount(new BigDecimal(amount));
        line.setControlLevel(BudgetLineEntity.CONTROL_BLOCK);
        return line;
    }

    private static BudgetBalanceEntity zeroBalance(String lineId) {
        BudgetBalanceEntity balance = new BudgetBalanceEntity();
        balance.setBudgetLineId(lineId);
        balance.setApprovedAmount(new BigDecimal("0.00"));
        balance.setReservedAmount(new BigDecimal("0.00"));
        balance.setApprovedUnpaidAmount(new BigDecimal("0.00"));
        balance.setPaidAmount(new BigDecimal("0.00"));
        return balance;
    }

    private static CreateBudgetDraftCmd fourLineBudget(String total) {
        return new CreateBudgetDraftCmd("GRANT", "BLOCK",
                LocalDate.of(2026, 1, 1), LocalDate.of(2028, 12, 31),
                List.of(new BudgetLineCmd("EQUIPMENT", "设备费", new BigDecimal("300000.00")),
                        new BudgetLineCmd("MATERIAL", "材料费", new BigDecimal("250000.00")),
                        new BudgetLineCmd("TRAVEL", "差旅费", new BigDecimal("150000.00")),
                        new BudgetLineCmd("LABOR", "劳务费", new BigDecimal("500000.00"))),
                new BigDecimal(total));
    }
}
