package cn.edu.rpm.project.domain.service;

import cn.edu.rpm.application.domain.model.ProjectCreationCommand;
import cn.edu.rpm.audit.domain.service.AuditRecorder;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.iam.application.dto.AuthContext;
import cn.edu.rpm.iam.application.dto.AuthDecision;
import cn.edu.rpm.iam.domain.service.AuthorizationService;
import cn.edu.rpm.project.domain.port.MilestoneEvidencePort;
import cn.edu.rpm.project.infrastructure.entity.MilestoneEntity;
import cn.edu.rpm.project.infrastructure.entity.ProjectBaselineEntity;
import cn.edu.rpm.project.infrastructure.entity.ProjectEntity;
import cn.edu.rpm.project.infrastructure.repository.MilestoneRepository;
import cn.edu.rpm.project.infrastructure.repository.ProjectBaselineRepository;
import cn.edu.rpm.project.infrastructure.repository.ProjectMemberRepository;
import cn.edu.rpm.project.infrastructure.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link MilestoneService} 纯单元测试（T-PRJ-014）。
 *
 * <p>覆盖 TC-EXEC-001～003：6 个里程碑绑定同一 baseline；缺完成判定
 * {@code RPM-EXEC-003}；有判定 + 证据才可完成并写 {@code actual_date}。</p>
 */
@ExtendWith(MockitoExtension.class)
class MilestoneServiceTest {

    private static final String PROJECT_ID = "8d7c6b5a4e3f4a2b9c1d0e2f3a4b5c6d";
    private static final String BASELINE_ID = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final String MILESTONE_ID = "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee";
    private static final UUID ACTOR_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final Instant NOW = Instant.parse("2026-11-18T02:00:00Z");

    @Mock
    private MilestoneRepository milestoneRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectBaselineRepository projectBaselineRepository;

    @Mock
    private ProjectMemberRepository projectMemberRepository;

    @Mock
    private MilestoneEvidencePort milestoneEvidencePort;

    @Mock
    private AuthorizationService authorizationService;

    @Mock
    private AuditRecorder auditRecorder;

    private MilestoneService service;

    private ProjectEntity project;

    @BeforeEach
    void setUp() {
        service = new MilestoneService(milestoneRepository, projectRepository,
                projectBaselineRepository, projectMemberRepository, milestoneEvidencePort,
                authorizationService, auditRecorder, Clock.fixed(NOW, ZoneId.of("Asia/Shanghai")));
        project = new ProjectEntity();
        project.setId(PROJECT_ID);
        project.setPiId("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        project.setDeptId("dddddddddddddddddddddddddddddddd");
        project.setStatus(ProjectEntity.STATUS_EXECUTING);
    }

    private void allowComplete(MilestoneEntity entity) {
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findActiveByProjectId(anyString(), any())).thenReturn(List.of());
        when(authorizationService.check(any(), any(), any())).thenReturn(AuthDecision.allow());
        when(milestoneRepository.selectForUpdate(MILESTONE_ID)).thenReturn(Optional.of(entity));
    }

    private AuthContext auth() {
        return new AuthContext(ACTOR_ID, null, "E001",
                Set.of("project:milestone:manage"), Set.of(), NOW);
    }

    private List<ProjectCreationCommand.MilestonePlan> sixPlans() {
        List<ProjectCreationCommand.MilestonePlan> plans = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            plans.add(new ProjectCreationCommand.MilestonePlan("M" + i, "里程碑-" + i,
                    LocalDate.of(2027, i, 28), "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "提交阶段报告"));
        }
        return plans;
    }

    private MilestoneEntity milestone(String criteria) {
        MilestoneEntity entity = new MilestoneEntity();
        entity.setId(MILESTONE_ID);
        entity.setProjectId(PROJECT_ID);
        entity.setBaselineId(BASELINE_ID);
        entity.setStatus(MilestoneEntity.STATUS_PLANNED);
        entity.setCompletionCriteria(criteria);
        entity.setPlanDate(LocalDate.of(2027, 3, 31));
        entity.setCurrentPlanDate(LocalDate.of(2027, 3, 31));
        return entity;
    }

    @Test
    void tcExec001CreateFromBaselineBindsSixMilestonesToSameBaseline() {
        ProjectBaselineEntity baseline = new ProjectBaselineEntity();
        baseline.setId(BASELINE_ID);
        baseline.setProjectId(PROJECT_ID);
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(projectBaselineRepository.findById(BASELINE_ID)).thenReturn(Optional.of(baseline));
        ArgumentCaptor<List<MilestoneEntity>> captor = ArgumentCaptor.forClass(List.class);

        List<MilestoneEntity> created = service.createFromBaseline(PROJECT_ID, BASELINE_ID, sixPlans());

        verify(milestoneRepository).insertBatch(captor.capture());
        List<MilestoneEntity> inserted = captor.getValue();
        assertThat(inserted).hasSize(6);
        assertThat(inserted).allMatch(item -> BASELINE_ID.equals(item.getBaselineId()));
        assertThat(inserted).allMatch(item -> MilestoneEntity.STATUS_PLANNED.equals(item.getStatus()));
        assertThat(inserted).allMatch(item -> item.getPlanDate().equals(item.getCurrentPlanDate()));
        assertThat(created).hasSize(6);
    }

    @Test
    void createFromBaselineAcceptsEquivalentUuidTextForms() {
        ProjectBaselineEntity baseline = new ProjectBaselineEntity();
        baseline.setId("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        baseline.setProjectId("8d7c6b5a-4e3f-4a2b-9c1d-0e2f3a4b5c6d");
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(projectBaselineRepository.findById(BASELINE_ID)).thenReturn(Optional.of(baseline));

        List<MilestoneEntity> created = service.createFromBaseline(PROJECT_ID, BASELINE_ID,
                sixPlans());

        assertThat(created).hasSize(6);
        verify(milestoneRepository).insertBatch(any());
    }

    @Test
    void createFromBaselineRejectsDuplicateCode() {
        ProjectBaselineEntity baseline = new ProjectBaselineEntity();
        baseline.setId(BASELINE_ID);
        baseline.setProjectId(PROJECT_ID);
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(projectBaselineRepository.findById(BASELINE_ID)).thenReturn(Optional.of(baseline));
        List<ProjectCreationCommand.MilestonePlan> plans = List.of(
                new ProjectCreationCommand.MilestonePlan("M1", "阶段一",
                        LocalDate.of(2027, 3, 31), null, null),
                new ProjectCreationCommand.MilestonePlan("M1", "阶段一重复",
                        LocalDate.of(2027, 6, 30), null, null));

        assertThatThrownBy(() -> service.createFromBaseline(PROJECT_ID, BASELINE_ID, plans))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.COMMON_400));
        verify(milestoneRepository, never()).insertBatch(any());
    }

    @Test
    void createFromBaselineRequiresOwnedBaseline() {
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(projectBaselineRepository.findById(BASELINE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createFromBaseline(PROJECT_ID, BASELINE_ID, sixPlans()))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.PROJ_004));
    }

    @Test
    void tcExec002CompleteWithoutCriteriaReturnsExec003() {
        allowComplete(milestone(" "));

        assertThatThrownBy(() -> service.complete(MILESTONE_ID, null, auth()))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.EXEC_003));
        verify(milestoneRepository, never()).update(any());
    }

    @Test
    void completeWithoutEvidenceReturnsExec004() {
        allowComplete(milestone("提交验收报告并通过评审"));
        when(milestoneEvidencePort.hasEvidence(PROJECT_ID, MILESTONE_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.complete(MILESTONE_ID, null, auth()))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.EXEC_004));
    }

    @Test
    void tcExec003CompleteWithCriteriaAndEvidenceWritesActualDate() {
        MilestoneEntity entity = milestone("提交验收报告并通过评审");
        allowComplete(entity);
        when(milestoneEvidencePort.hasEvidence(PROJECT_ID, MILESTONE_ID)).thenReturn(true);
        when(milestoneRepository.update(entity)).thenReturn(1);
        LocalDate actual = LocalDate.of(2027, 3, 30);

        MilestoneEntity completed = service.complete(MILESTONE_ID, actual, auth());

        assertThat(completed.getStatus()).isEqualTo(MilestoneEntity.STATUS_COMPLETED);
        assertThat(completed.getActualDate()).isEqualTo(actual);
        verify(auditRecorder).record(any());
    }

    @Test
    void unauthorizedCompleteReturnsCommon403() {
        allowComplete(milestone("提交验收报告并通过评审"));
        when(authorizationService.check(any(), any(), any()))
                .thenReturn(AuthDecision.deny(ErrorCode.COMMON_403));

        assertThatThrownBy(() -> service.complete(MILESTONE_ID, null, auth()))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.COMMON_403));
    }
}
