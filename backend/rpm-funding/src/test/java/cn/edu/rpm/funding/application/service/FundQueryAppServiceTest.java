package cn.edu.rpm.funding.application.service;

import cn.edu.rpm.common.util.UuidUtils;
import cn.edu.rpm.funding.application.dto.FundSummary;
import cn.edu.rpm.funding.domain.model.FundingPermissions;
import cn.edu.rpm.funding.infrastructure.entity.BudgetBalanceEntity;
import cn.edu.rpm.funding.infrastructure.entity.BudgetLineEntity;
import cn.edu.rpm.funding.infrastructure.entity.BudgetVersionEntity;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * {@link FundQueryAppService} 纯单元测试（T-FUND-023；TC-FUND-037、TC-FUND-038）。
 */
@ExtendWith(MockitoExtension.class)
class FundQueryAppServiceTest {

    private static final String PROJECT_ID = "11111111111111111111111111111111";
    private static final String VERSION_ID = "22222222222222222222222222222222";
    private static final String LINE_ID = "33333333333333333333333333333333";
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

    private FundQueryAppService service;

    private AuthContext auth;

    private BudgetVersionEntity effectiveVersion;

    private BudgetLineEntity line;

    @BeforeEach
    void setUp() {
        service = new FundQueryAppService(budgetVersionRepository, budgetLineRepository,
                budgetBalanceRepository, projectRepository, projectMemberRepository,
                authorizationService, Clock.fixed(NOW, ZoneId.of("Asia/Shanghai")));
        ProjectEntity project = new ProjectEntity();
        project.setId(PROJECT_ID);
        project.setPiId(UuidUtils.formatCompact(PI_ID));
        project.setDeptId(UuidUtils.formatCompact(DEPT_ID));
        project.setStatus(ProjectEntity.STATUS_EXECUTING);
        effectiveVersion = new BudgetVersionEntity();
        effectiveVersion.setId(VERSION_ID);
        effectiveVersion.setProjectId(PROJECT_ID);
        effectiveVersion.setStatus(BudgetVersionEntity.STATUS_EFFECTIVE);
        line = new BudgetLineEntity();
        line.setId(LINE_ID);
        line.setBudgetVersionId(VERSION_ID);
        line.setSubjectCode("EQUIPMENT");
        line.setSubjectName("设备费");
        auth = new AuthContext(ACTOR_ID, null, "EMP-001", Set.of(FundingPermissions.VIEW),
                Set.of(), NOW);
        lenient().when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        lenient().when(projectMemberRepository.findActiveByProjectId(eq(PROJECT_ID), any()))
                .thenReturn(List.of());
        lenient().when(authorizationService.check(any(AuthContext.class), any(), any()))
                .thenReturn(AuthDecision.allow());
    }

    @Test
    void tcFund037SummaryComputesAvailableSevenHundredThousand() {
        BudgetBalanceEntity balance = balance("1200000.00", "80000.00", "120000.00", "300000.00");
        when(budgetVersionRepository.findEffectiveByProjectId(PROJECT_ID))
                .thenReturn(Optional.of(effectiveVersion));
        when(budgetLineRepository.findByBudgetVersionId(VERSION_ID)).thenReturn(List.of(line));
        when(budgetBalanceRepository.findByBudgetLineIds(List.of(LINE_ID)))
                .thenReturn(List.of(balance));

        FundSummary summary = service.summary(PROJECT_ID, auth);

        assertThat(summary.lines()).hasSize(1);
        assertThat(summary.lines().get(0).availableAmount()).isEqualByComparingTo("700000.00");
        assertThat(summary.totalApprovedAmount()).isEqualByComparingTo("1200000.00");
        assertThat(summary.totalReservedAmount()).isEqualByComparingTo("80000.00");
        assertThat(summary.totalApprovedUnpaidAmount()).isEqualByComparingTo("120000.00");
        assertThat(summary.totalPaidAmount()).isEqualByComparingTo("300000.00");
        assertThat(summary.totalAvailableAmount()).isEqualByComparingTo("700000.00");
        assertThat(summary.dataAsOf()).isNotNull();
    }

    @Test
    void tcFund038NoBudgetReturnsZerosAndEmptyLines() {
        when(budgetVersionRepository.findEffectiveByProjectId(PROJECT_ID)).thenReturn(Optional.empty());

        FundSummary summary = service.summary(PROJECT_ID, auth);

        assertThat(summary.lines()).isEmpty();
        assertThat(summary.totalApprovedAmount()).isEqualByComparingTo("0.00");
        assertThat(summary.totalReservedAmount()).isEqualByComparingTo("0.00");
        assertThat(summary.totalApprovedUnpaidAmount()).isEqualByComparingTo("0.00");
        assertThat(summary.totalPaidAmount()).isEqualByComparingTo("0.00");
        assertThat(summary.totalAvailableAmount()).isEqualByComparingTo("0.00");
        assertThat(summary.dataAsOf()).isNotNull();
    }

    @Test
    void summarySumsMultipleLinesAndTreatsMissingBalanceAsZero() {
        BudgetLineEntity second = new BudgetLineEntity();
        second.setId("44444444444444444444444444444444");
        second.setBudgetVersionId(VERSION_ID);
        second.setSubjectCode("TRAVEL");
        second.setSubjectName("差旅费");
        BudgetBalanceEntity firstBalance = balance("100000.00", "10000.00", "20000.00", "30000.00");
        when(budgetVersionRepository.findEffectiveByProjectId(PROJECT_ID))
                .thenReturn(Optional.of(effectiveVersion));
        when(budgetLineRepository.findByBudgetVersionId(VERSION_ID)).thenReturn(List.of(line, second));
        when(budgetBalanceRepository.findByBudgetLineIds(List.of(LINE_ID,
                "44444444444444444444444444444444"))).thenReturn(List.of(firstBalance));

        FundSummary summary = service.summary(PROJECT_ID, auth);

        assertThat(summary.totalAvailableAmount()).isEqualByComparingTo("40000.00");
        assertThat(summary.lines()).hasSize(2);
        assertThat(summary.lines().get(1).approvedAmount()).isEqualByComparingTo("0.00");
    }

    private static BudgetBalanceEntity balance(String approved, String reserved, String unpaid,
                                               String paid) {
        BudgetBalanceEntity balance = new BudgetBalanceEntity();
        balance.setApprovedAmount(new BigDecimal(approved));
        balance.setReservedAmount(new BigDecimal(reserved));
        balance.setApprovedUnpaidAmount(new BigDecimal(unpaid));
        balance.setPaidAmount(new BigDecimal(paid));
        balance.setBudgetLineId(LINE_ID);
        return balance;
    }
}
