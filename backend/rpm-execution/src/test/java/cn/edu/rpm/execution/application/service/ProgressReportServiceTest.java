package cn.edu.rpm.execution.application.service;

import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.execution.application.dto.ProgressReportCreated;
import cn.edu.rpm.execution.infrastructure.entity.ProgressReportEntity;
import cn.edu.rpm.execution.infrastructure.entity.ReportPeriodRuleEntity;
import cn.edu.rpm.execution.infrastructure.repository.ProgressReportRepository;
import cn.edu.rpm.execution.infrastructure.repository.ReportPeriodRuleRepository;
import cn.edu.rpm.project.infrastructure.entity.ProjectEntity;
import cn.edu.rpm.project.infrastructure.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ProgressReportService} 纯单元测试（T-EXEC-005）。
 *
 * <p>覆盖 TC-EXEC-006 / TC-EXEC-007：月报/季报到期日按周期结束日 + 偏移天数；
 * 无规则默认 5 天；同项目同周期唯一（{@code RPM-EXEC-001}）；参数非法 400。</p>
 */
@ExtendWith(MockitoExtension.class)
class ProgressReportServiceTest {

    private static final String PROJECT_ID = "8d7c6b5a4e3f4a2b9c1d0e2f3a4b5c6d";
    private static final String REPORT_ID = "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee";

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProgressReportRepository progressReportRepository;

    @Mock
    private ReportPeriodRuleRepository reportPeriodRuleRepository;

    private ProgressReportService service;

    private ProjectEntity project;

    @BeforeEach
    void setUp() {
        service = new ProgressReportService(projectRepository, progressReportRepository,
                reportPeriodRuleRepository);
        project = new ProjectEntity();
        project.setId(PROJECT_ID);
        project.setProjectType("YEAR_PLAN");
        project.setStatus(ProjectEntity.STATUS_EXECUTING);
    }

    private void stubInsertion() {
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(progressReportRepository.findByProjectIdAndPeriod(anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        doAnswer(invocation -> {
            ((ProgressReportEntity) invocation.getArgument(0)).setId(REPORT_ID);
            return null;
        }).when(progressReportRepository).insert(any());
    }

    private ReportPeriodRuleEntity rule(int offsetDays) {
        ReportPeriodRuleEntity rule = new ReportPeriodRuleEntity();
        rule.setProjectType("YEAR_PLAN");
        rule.setPeriodType(ProgressReportEntity.PERIOD_MONTH);
        rule.setDueOffsetDays(offsetDays);
        rule.setStatus(ReportPeriodRuleEntity.STATUS_ACTIVE);
        return rule;
    }

    @Test
    void tcExec006MonthlyReportDueFiveDaysAfterPeriodEndByDefault() {
        stubInsertion();
        when(reportPeriodRuleRepository.findActive("YEAR_PLAN", ProgressReportEntity.PERIOD_MONTH))
                .thenReturn(Optional.empty());

        ProgressReportCreated created = service.create(PROJECT_ID,
                ProgressReportEntity.PERIOD_MONTH, "2026-11");

        assertThat(created.reportId()).isEqualTo(REPORT_ID);
        assertThat(created.status()).isEqualTo(ProgressReportEntity.STATUS_DRAFT);
        assertThat(created.dueDate()).isEqualTo(LocalDate.of(2026, 12, 5));
        verify(progressReportRepository).insert(any());
    }

    @Test
    void tcExec007QuarterReportUsesRuleOffset() {
        stubInsertion();
        when(reportPeriodRuleRepository.findActive("YEAR_PLAN", ProgressReportEntity.PERIOD_QUARTER))
                .thenReturn(Optional.of(rule(10)));

        ProgressReportCreated created = service.create(PROJECT_ID,
                ProgressReportEntity.PERIOD_QUARTER, "2026-Q4");

        assertThat(created.periodKey()).isEqualTo("2026-Q4");
        assertThat(created.dueDate()).isEqualTo(LocalDate.of(2027, 1, 10));
    }

    @Test
    void negativeRuleOffsetFallsBackToDefault() {
        stubInsertion();
        ReportPeriodRuleEntity rule = rule(-1);
        when(reportPeriodRuleRepository.findActive("YEAR_PLAN", ProgressReportEntity.PERIOD_MONTH))
                .thenReturn(Optional.of(rule));

        assertThat(service.create(PROJECT_ID, ProgressReportEntity.PERIOD_MONTH, "2026-11")
                .dueDate()).isEqualTo(LocalDate.of(2026, 12, 5));
    }

    @Test
    void duplicateSamePeriodReturnsExec001() {
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(progressReportRepository.findByProjectIdAndPeriod(PROJECT_ID,
                ProgressReportEntity.PERIOD_MONTH, "2026-11"))
                .thenReturn(Optional.of(new ProgressReportEntity()));

        assertThatThrownBy(() -> service.create(PROJECT_ID,
                ProgressReportEntity.PERIOD_MONTH, "2026-11"))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.EXEC_001));
        verify(progressReportRepository, never()).insert(any());
    }

    @Test
    void duplicateKeyRaceReturnsExec001() {
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(progressReportRepository.findByProjectIdAndPeriod(anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(reportPeriodRuleRepository.findActive(anyString(), anyString()))
                .thenReturn(Optional.empty());
        doThrow(new DuplicateKeyException("uk_progress_report_period"))
                .when(progressReportRepository).insert(any());

        assertThatThrownBy(() -> service.create(PROJECT_ID,
                ProgressReportEntity.PERIOD_MONTH, "2026-11"))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.EXEC_001));
    }

    @Test
    void invalidPeriodTypeReturnsCommon400() {
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));

        assertThatThrownBy(() -> service.create(PROJECT_ID, "WEEK", "2026-W01"))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.COMMON_400));
    }

    @Test
    void invalidMonthKeyReturnsCommon400() {
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));

        assertThatThrownBy(() -> service.create(PROJECT_ID,
                ProgressReportEntity.PERIOD_MONTH, "2026-13"))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.COMMON_400));
    }

    @Test
    void invalidQuarterKeyReturnsCommon400() {
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));

        assertThatThrownBy(() -> service.create(PROJECT_ID,
                ProgressReportEntity.PERIOD_QUARTER, "2026-Q5"))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.COMMON_400));
    }

    @Test
    void missingProjectReturnsProj001() {
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(PROJECT_ID,
                ProgressReportEntity.PERIOD_MONTH, "2026-11"))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.PROJ_001));
    }
}
