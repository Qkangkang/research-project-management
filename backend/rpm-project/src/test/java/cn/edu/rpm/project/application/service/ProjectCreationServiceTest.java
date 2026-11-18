package cn.edu.rpm.project.application.service;

import cn.edu.rpm.application.domain.model.ProjectCreationCommand;
import cn.edu.rpm.application.domain.model.ProjectCreationResult;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.common.util.CanonicalJsonHasher;
import cn.edu.rpm.project.domain.service.MilestoneService;
import cn.edu.rpm.project.domain.service.ProjectNumberGenerator;
import cn.edu.rpm.project.infrastructure.entity.ProjectBaselineEntity;
import cn.edu.rpm.project.infrastructure.entity.ProjectEntity;
import cn.edu.rpm.project.infrastructure.repository.ProjectBaselineRepository;
import cn.edu.rpm.project.infrastructure.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ProjectCreationService} 纯单元测试（T-PRJ-018）。
 *
 * <p>覆盖 TC-APP-028 / TC-APP-029：审批通过生成项目（{@code PENDING_START}）+
 * 初始 {@code EFFECTIVE} 基线（快照哈希稳定）+ 基线里程碑；同一申报版本重复调用
 * 返回首次结果且不再产生第二个项目。</p>
 */
@ExtendWith(MockitoExtension.class)
class ProjectCreationServiceTest {

    private static final String VERSION_ID = "11111111111111111111111111111111";
    private static final String PROJECT_ID = "8d7c6b5a4e3f4a2b9c1d0e2f3a4b5c6d";
    private static final String BASELINE_ID = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final String DEPT_ID = "dddddddddddddddddddddddddddddddd";
    private static final String PI_ID = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final Instant NOW = Instant.parse("2026-11-18T02:00:00Z");

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectBaselineRepository projectBaselineRepository;

    @Mock
    private ProjectNumberGenerator projectNumberGenerator;

    @Mock
    private MilestoneService milestoneService;

    private CanonicalJsonHasher jsonHasher;

    private ProjectCreationService service;

    @BeforeEach
    void setUp() {
        jsonHasher = new CanonicalJsonHasher();
        service = new ProjectCreationService(projectRepository, projectBaselineRepository,
                projectNumberGenerator, milestoneService, jsonHasher,
                Clock.fixed(NOW, ZoneId.of("Asia/Shanghai")));
    }

    private ProjectCreationCommand command() {
        return new ProjectCreationCommand(
                "22222222222222222222222222222222", VERSION_ID, ProjectCreationCommand.SOURCE_INTERNAL,
                "智能感知科研平台", "YEAR_PLAN", DEPT_ID, PI_ID,
                LocalDate.of(2026, 12, 1), LocalDate.of(2028, 11, 30),
                null, null, null,
                Map.of("title", "智能感知科研平台", "budget", 100000),
                List.of(new ProjectCreationCommand.MilestonePlan("M1", "中期检查",
                        LocalDate.of(2027, 12, 31), PI_ID, "提交中期报告")));
    }

    private void stubCreationUntilUpdate(int updated) {
        when(projectRepository.findBySourceApplicationVersionId(VERSION_ID)).thenReturn(Optional.empty());
        when(projectNumberGenerator.next()).thenReturn("KY-2026-000001");
        doAnswer(invocation -> {
            ((ProjectEntity) invocation.getArgument(0)).setId(PROJECT_ID);
            return null;
        }).when(projectRepository).insert(any());
        doAnswer(invocation -> {
            ((ProjectBaselineEntity) invocation.getArgument(0)).setId(BASELINE_ID);
            return null;
        }).when(projectBaselineRepository).insert(any());
        when(projectRepository.update(any())).thenReturn(updated);
    }

    private void stubNewCreation() {
        stubCreationUntilUpdate(1);
        when(milestoneService.createFromBaseline(anyString(), anyString(), anyList()))
                .thenReturn(List.of());
    }

    @Test
    void tcApp028CreateProjectWithBaselineAndMilestones() {
        stubNewCreation();
        ProjectCreationCommand cmd = command();
        ArgumentCaptor<ProjectEntity> projectCaptor = ArgumentCaptor.forClass(ProjectEntity.class);
        ArgumentCaptor<ProjectBaselineEntity> baselineCaptor = ArgumentCaptor.forClass(
                ProjectBaselineEntity.class);

        ProjectCreationResult result = service.createFromVersion(cmd);

        assertThat(result.projectId()).isEqualTo(PROJECT_ID);
        assertThat(result.projectNo()).isEqualTo("KY-2026-000001");
        assertThat(result.baselineId()).isEqualTo(BASELINE_ID);

        verify(projectRepository).insert(projectCaptor.capture());
        ProjectEntity project = projectCaptor.getValue();
        assertThat(project.getStatus()).isEqualTo(ProjectEntity.STATUS_PENDING_START);
        assertThat(project.getSourceApplicationVersionId()).isEqualTo(VERSION_ID);
        assertThat(project.getApprovedAt()).isNotNull();
        assertThat(project.getCurrentBaselineId()).isEqualTo(BASELINE_ID);

        verify(projectBaselineRepository).insert(baselineCaptor.capture());
        ProjectBaselineEntity baseline = baselineCaptor.getValue();
        assertThat(baseline.getVersionNo()).isEqualTo(1);
        assertThat(baseline.getStatus()).isEqualTo(ProjectBaselineEntity.STATUS_EFFECTIVE);
        assertThat(baseline.getSnapshotHash())
                .isEqualTo(jsonHasher.sha256(jsonHasher.canonicalJson(cmd.snapshot())))
                .matches("[0-9a-f]{64}");
        assertThat(baseline.getEffectiveAt()).isNotNull();

        verify(milestoneService).createFromBaseline(eq(PROJECT_ID), eq(BASELINE_ID), anyList());
    }

    @Test
    void tcApp029ReplayReturnsExistingProjectWithoutCreatingAnother() {
        ProjectEntity existing = new ProjectEntity();
        existing.setId(PROJECT_ID);
        existing.setProjectNo("KY-2026-000001");
        existing.setCurrentBaselineId(BASELINE_ID);
        when(projectRepository.findBySourceApplicationVersionId(VERSION_ID))
                .thenReturn(Optional.of(existing));

        ProjectCreationResult result = service.createFromVersion(command());

        assertThat(result.projectId()).isEqualTo(PROJECT_ID);
        assertThat(result.baselineId()).isEqualTo(BASELINE_ID);
        verify(projectRepository, never()).insert(any());
        verify(projectNumberGenerator, never()).next();
        verify(projectBaselineRepository, never()).insert(any());
    }

    @Test
    void replayFallsBackToEffectiveBaselineWhenCurrentBaselineMissing() {
        ProjectEntity existing = new ProjectEntity();
        existing.setId(PROJECT_ID);
        existing.setProjectNo("KY-2026-000001");
        ProjectBaselineEntity baseline = new ProjectBaselineEntity();
        baseline.setId(BASELINE_ID);
        when(projectRepository.findBySourceApplicationVersionId(VERSION_ID))
                .thenReturn(Optional.of(existing));
        when(projectBaselineRepository.findEffectiveByProjectId(PROJECT_ID))
                .thenReturn(Optional.of(baseline));

        assertThat(service.createFromVersion(command()).baselineId()).isEqualTo(BASELINE_ID);
    }

    @Test
    void duplicateKeyRaceReturnsConcurrentWinner() {
        ProjectEntity winner = new ProjectEntity();
        winner.setId(PROJECT_ID);
        winner.setProjectNo("KY-2026-000001");
        winner.setCurrentBaselineId(BASELINE_ID);
        when(projectRepository.findBySourceApplicationVersionId(VERSION_ID))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));
        when(projectNumberGenerator.next()).thenReturn("KY-2026-000001");
        doThrow(new DuplicateKeyException("uk_project_source_version"))
                .when(projectRepository).insert(any());

        ProjectCreationResult result = service.createFromVersion(command());

        assertThat(result.projectId()).isEqualTo(PROJECT_ID);
        verify(projectBaselineRepository, never()).insert(any());
    }

    @Test
    void invalidDateRangeReturnsCommon400() {
        when(projectRepository.findBySourceApplicationVersionId(VERSION_ID)).thenReturn(Optional.empty());
        ProjectCreationCommand invalid = new ProjectCreationCommand(
                "22222222222222222222222222222222", VERSION_ID, ProjectCreationCommand.SOURCE_INTERNAL,
                "智能感知科研平台", "YEAR_PLAN", DEPT_ID, PI_ID,
                LocalDate.of(2028, 12, 1), LocalDate.of(2027, 11, 30),
                null, null, null, Map.of(), List.of());

        assertThatThrownBy(() -> service.createFromVersion(invalid))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.COMMON_400));
        verify(projectRepository, never()).insert(any());
    }

    @Test
    void invalidSourceTypeReturnsCommon400() {
        when(projectRepository.findBySourceApplicationVersionId(VERSION_ID)).thenReturn(Optional.empty());
        ProjectCreationCommand invalid = new ProjectCreationCommand(
                null, VERSION_ID, "UNKNOWN", "智能感知科研平台", "YEAR_PLAN", DEPT_ID, PI_ID,
                LocalDate.of(2026, 12, 1), LocalDate.of(2028, 11, 30),
                null, null, null, Map.of(), List.of());

        assertThatThrownBy(() -> service.createFromVersion(invalid))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.COMMON_400));
    }

    @Test
    void missingSourceVersionReturnsCommon400() {
        ProjectCreationCommand invalid = new ProjectCreationCommand(
                null, " ", ProjectCreationCommand.SOURCE_INTERNAL, "智能感知科研平台",
                "YEAR_PLAN", DEPT_ID, PI_ID,
                LocalDate.of(2026, 12, 1), LocalDate.of(2028, 11, 30),
                null, null, null, Map.of(), List.of());

        assertThatThrownBy(() -> service.createFromVersion(invalid))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.COMMON_400));
    }

    @Test
    void baselineSwitchConflictReturnsCommon409() {
        stubCreationUntilUpdate(0);

        assertThatThrownBy(() -> service.createFromVersion(command()))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.COMMON_409));
        verify(milestoneService, never()).createFromBaseline(anyString(), anyString(), anyList());
    }
}
