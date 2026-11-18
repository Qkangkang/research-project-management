package cn.edu.rpm.project.application.handler;

import cn.edu.rpm.audit.domain.service.AuditRecorder;
import cn.edu.rpm.common.domain.model.OutboxEvent;
import cn.edu.rpm.common.domain.port.OutboxAppendPort;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.common.util.CanonicalJsonHasher;
import cn.edu.rpm.project.domain.port.BudgetChangePort;
import cn.edu.rpm.project.domain.port.HandoverTaskTransferPort;
import cn.edu.rpm.project.infrastructure.entity.ChangeRequestEntity;
import cn.edu.rpm.project.infrastructure.entity.ChangeRequestVersionEntity;
import cn.edu.rpm.project.infrastructure.entity.ProjectBaselineEntity;
import cn.edu.rpm.project.infrastructure.entity.ProjectEntity;
import cn.edu.rpm.project.infrastructure.entity.ProjectMemberEntity;
import cn.edu.rpm.project.infrastructure.repository.ChangeRequestRepository;
import cn.edu.rpm.project.infrastructure.repository.ChangeRequestVersionRepository;
import cn.edu.rpm.project.infrastructure.repository.ProjectBaselineRepository;
import cn.edu.rpm.project.infrastructure.repository.ProjectMemberRepository;
import cn.edu.rpm.project.infrastructure.repository.ProjectRepository;
import cn.edu.rpm.project.infrastructure.repository.ProjectRiskRepository;
import cn.edu.rpm.workflow.domain.dto.ApprovalResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link ChangeApprovalHandler} 纯单元测试（T-PRJ-021）。
 *
 * <p>覆盖 TC-PRJ-021（PERIOD 批准生成新基线并原子切换）、TC-PRJ-022（拒绝不生成基线）、
 * TC-PRJ-023 / TC-CHG-107（BUDGET 批准调用预算变更端口，由资金模块生成新版本）、
 * TC-CHG-103（PI 变更批准切换负责人并转移风险/任务）、
 * TC-CHG-104（GOAL 变更批准后旧基线历史仍可查）、
 * TC-CHG-105（并发审批后旧版本回调 `RPM-WF-005`，仅一个新基线生效）。</p>
 */
@ExtendWith(MockitoExtension.class)
class ChangeApprovalHandlerTest {

    private static final String PROJECT_ID = "8d7c6b5a4e3f4a2b9c1d0e2f3a4b5c6d";
    private static final String CHANGE_ID = "11111111111111111111111111111111";
    private static final String VERSION_ID = "22222222222222222222222222222222";
    private static final String OLD_BASELINE_ID = "33333333333333333333333333333333";
    private static final String OTHER_BASELINE_ID = "44444444444444444444444444444444";
    private static final UUID CHANGE_UUID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID VERSION_UUID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID OPERATOR_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID REQUEST_ID = UUID.fromString("abcdabcd-abcd-abcd-abcd-abcdabcdabcd");
    private static final Instant NOW = Instant.parse("2026-11-18T02:00:00Z");

    @Mock
    private ChangeRequestRepository changeRequestRepository;

    @Mock
    private ChangeRequestVersionRepository versionRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectBaselineRepository baselineRepository;

    @Mock
    private ProjectMemberRepository projectMemberRepository;

    @Mock
    private ProjectRiskRepository projectRiskRepository;

    @Mock
    private BudgetChangePort budgetChangePort;

    @Mock
    private HandoverTaskTransferPort taskTransferPort;

    @Mock
    private OutboxAppendPort outboxAppendPort;

    @Mock
    private AuditRecorder auditRecorder;

    private ChangeApprovalHandler handler;

    private ChangeRequestEntity change;

    private ChangeRequestVersionEntity version;

    private ProjectEntity project;

    private ProjectBaselineEntity oldBaseline;

    @BeforeEach
    void setUp() {
        handler = new ChangeApprovalHandler(changeRequestRepository, versionRepository,
                projectRepository, baselineRepository, projectMemberRepository,
                projectRiskRepository, List.of(budgetChangePort), List.of(taskTransferPort),
                outboxAppendPort, auditRecorder,
                new CanonicalJsonHasher(), new ObjectMapper(),
                Clock.fixed(NOW, ZoneId.of("Asia/Shanghai")));
        change = new ChangeRequestEntity();
        change.setId(CHANGE_ID);
        change.setProjectId(PROJECT_ID);
        change.setChangeType(ChangeRequestEntity.TYPE_PERIOD);
        change.setStatus(ChangeRequestEntity.STATUS_IN_REVIEW);
        change.setCurrentVersionId(VERSION_ID);
        change.setVersion(2);
        version = new ChangeRequestVersionEntity();
        version.setId(VERSION_ID);
        version.setChangeRequestId(CHANGE_ID);
        version.setVersionNo(1);
        version.setPayload("{\"new_start_date\":\"2026-12-01\",\"new_end_date\":\"2030-05-31\"}");
        version.setOldBaselineId(OLD_BASELINE_ID);
        project = new ProjectEntity();
        project.setId(PROJECT_ID);
        project.setPiId("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
        project.setDeptId("cccccccccccccccccccccccccccccccc");
        project.setStatus(ProjectEntity.STATUS_EXECUTING);
        project.setCurrentBaselineId(OLD_BASELINE_ID);
        project.setStartDate(LocalDate.of(2026, 12, 1));
        project.setEndDate(LocalDate.of(2028, 11, 30));
        project.setVersion(3);
        oldBaseline = new ProjectBaselineEntity();
        oldBaseline.setId(OLD_BASELINE_ID);
        oldBaseline.setProjectId(PROJECT_ID);
        oldBaseline.setVersionNo(1);
        oldBaseline.setStatus(ProjectBaselineEntity.STATUS_EFFECTIVE);
        oldBaseline.setSnapshot("{\"title\":\"项目A\",\"start_date\":\"2026-12-01\","
                + "\"end_date\":\"2028-11-30\"}");
        oldBaseline.setSnapshotHash("a".repeat(64));
        oldBaseline.setVersion(1);
    }

    private ApprovalResult result() {
        return new ApprovalResult(UUID.randomUUID(), "CHANGE", CHANGE_UUID, VERSION_UUID,
                "APPROVED", OPERATOR_ID, null, "同意变更",
                OffsetDateTime.now(Clock.fixed(NOW, ZoneId.of("Asia/Shanghai"))),
                REQUEST_ID, List.of());
    }

    private void stubApprove() {
        when(changeRequestRepository.selectForUpdate(CHANGE_ID)).thenReturn(Optional.of(change));
        when(versionRepository.findById(VERSION_ID)).thenReturn(Optional.of(version));
        when(projectRepository.selectForUpdate(PROJECT_ID)).thenReturn(Optional.of(project));
        when(baselineRepository.findById(OLD_BASELINE_ID)).thenReturn(Optional.of(oldBaseline));
        when(baselineRepository.findMaxVersionNo(PROJECT_ID)).thenReturn(1);
        lenient().when(baselineRepository.update(any())).thenReturn(1);
        lenient().when(projectRepository.update(any())).thenReturn(1);
        lenient().when(changeRequestRepository.update(any())).thenReturn(1);
        lenient().when(versionRepository.update(any())).thenReturn(1);
        lenient().when(projectMemberRepository.findActiveByProjectId(any(), any()))
                .thenReturn(List.of());
    }

    @Test
    void tcPrj021PeriodApprovalCreatesNewBaselineAndSwitchesAtomically() {
        stubApprove();
        ArgumentCaptor<ProjectBaselineEntity> baselineCaptor =
                ArgumentCaptor.forClass(ProjectBaselineEntity.class);

        handler.onApproved(result());

        assertThat(change.getStatus()).isEqualTo(ChangeRequestEntity.STATUS_APPROVED);
        assertThat(oldBaseline.getStatus()).isEqualTo(ProjectBaselineEntity.STATUS_SUPERSEDED);
        verify(baselineRepository).insert(baselineCaptor.capture());
        ProjectBaselineEntity created = baselineCaptor.getValue();
        assertThat(created.getVersionNo()).isEqualTo(2);
        assertThat(created.getStatus()).isEqualTo(ProjectBaselineEntity.STATUS_EFFECTIVE);
        assertThat(created.getSourceChangeId()).isEqualTo(CHANGE_ID);
        assertThat(created.getSnapshot()).contains("\"end_date\":\"2030-05-31\"");
        assertThat(created.getSnapshotHash()).matches("[0-9a-f]{64}");
        assertThat(project.getCurrentBaselineId()).isEqualTo(created.getId());
        assertThat(version.getNewBaselineId()).isEqualTo(created.getId());

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxAppendPort).append(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().eventType()).isEqualTo("BASELINE_CHANGED");
    }

    @Test
    void tcPrj022RejectedChangeDoesNotCreateBaseline() {
        when(changeRequestRepository.selectForUpdate(CHANGE_ID)).thenReturn(Optional.of(change));
        when(changeRequestRepository.update(any())).thenReturn(1);

        handler.onRejected(result());

        assertThat(change.getStatus()).isEqualTo(ChangeRequestEntity.STATUS_REJECTED);
        assertThat(project.getCurrentBaselineId()).isEqualTo(OLD_BASELINE_ID);
        verifyNoInteractions(baselineRepository, projectRepository, outboxAppendPort);
    }

    @Test
    void tcChg105StaleChangeVersionAfterConcurrentApprovalReturnsWf005() {
        project.setCurrentBaselineId(OTHER_BASELINE_ID);
        when(changeRequestRepository.selectForUpdate(CHANGE_ID)).thenReturn(Optional.of(change));
        when(versionRepository.findById(VERSION_ID)).thenReturn(Optional.of(version));
        when(projectRepository.selectForUpdate(PROJECT_ID)).thenReturn(Optional.of(project));

        assertThatThrownBy(() -> handler.onApproved(result()))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.WF_005));

        verify(baselineRepository, never()).insert(any());
        verify(changeRequestRepository, never()).update(any());
    }

    @Test
    void tcChg103PiApprovalSwitchesPiAndTransfersRiskAndTasks() {
        String oldPi = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
        String newPi = "dddddddddddddddddddddddddddddddd";
        change.setChangeType(ChangeRequestEntity.TYPE_PI);
        version.setPayload("{\"new_pi_user_id\":\"" + newPi + "\"}");
        stubApprove();
        ProjectMemberEntity oldMember = new ProjectMemberEntity();
        oldMember.setProjectId(PROJECT_ID);
        oldMember.setUserId(oldPi);
        oldMember.setProjectRole("PI");
        oldMember.setStatus(ProjectMemberEntity.STATUS_ACTIVE);
        ProjectMemberEntity newMember = new ProjectMemberEntity();
        newMember.setProjectId(PROJECT_ID);
        newMember.setUserId(newPi);
        newMember.setProjectRole("MEMBER");
        newMember.setStatus(ProjectMemberEntity.STATUS_ACTIVE);
        when(projectMemberRepository.findActiveByProjectId(any(), any()))
                .thenReturn(List.of(oldMember, newMember));
        lenient().when(projectMemberRepository.update(any())).thenReturn(1);

        handler.onApproved(result());

        assertThat(project.getPiId()).isEqualTo(newPi);
        assertThat(oldMember.getStatus()).isNotEqualTo(ProjectMemberEntity.STATUS_ACTIVE);
        assertThat(newMember.getProjectRole()).isEqualTo("PI");
        verify(projectRiskRepository).transferOwner(PROJECT_ID, oldPi, newPi);
        verify(taskTransferPort).transferTasksAndTodos(PROJECT_ID, oldPi, newPi);
        verify(baselineRepository).insert(any(ProjectBaselineEntity.class));
        assertThat(project.getCurrentBaselineId()).isNotEqualTo(OLD_BASELINE_ID);
    }

    @Test
    void tcChg104GoalApprovalKeepsOldBaselineHistoryImmutable() {
        change.setChangeType(ChangeRequestEntity.TYPE_GOAL);
        version.setPayload("{\"objectives\":[{\"code\":\"OBJ-1\",\"description\":\"新目标\"}]}");
        String oldSnapshot = oldBaseline.getSnapshot();
        stubApprove();
        ArgumentCaptor<ProjectBaselineEntity> baselineCaptor =
                ArgumentCaptor.forClass(ProjectBaselineEntity.class);

        handler.onApproved(result());

        verify(baselineRepository).insert(baselineCaptor.capture());
        assertThat(baselineCaptor.getValue().getSnapshot()).contains("\"objectives\"");
        assertThat(baselineCaptor.getValue().getSnapshot()).contains("新目标");
        assertThat(oldBaseline.getSnapshot()).isEqualTo(oldSnapshot);
        assertThat(oldBaseline.getStatus()).isEqualTo(ProjectBaselineEntity.STATUS_SUPERSEDED);
        assertThat(project.getCurrentBaselineId()).isEqualTo(baselineCaptor.getValue().getId());
    }

    @Test
    void tcChg107BudgetApprovalCallsBudgetChangePortWithoutBaseline() {
        change.setChangeType(ChangeRequestEntity.TYPE_BUDGET);
        version.setPayload("{\"budget_version_draft_id\":\"55555555555555555555555555555555\"}");
        when(changeRequestRepository.selectForUpdate(CHANGE_ID)).thenReturn(Optional.of(change));
        when(versionRepository.findById(VERSION_ID)).thenReturn(Optional.of(version));
        when(budgetChangePort.applyBudgetChange(any())).thenReturn("bv-new");
        lenient().when(changeRequestRepository.update(any())).thenReturn(1);

        handler.onApproved(result());

        assertThat(change.getStatus()).isEqualTo(ChangeRequestEntity.STATUS_APPROVED);
        ArgumentCaptor<BudgetChangePort.BudgetChangeCommand> captor =
                ArgumentCaptor.forClass(BudgetChangePort.BudgetChangeCommand.class);
        verify(budgetChangePort).applyBudgetChange(captor.capture());
        assertThat(captor.getValue().projectId()).isEqualTo(PROJECT_ID);
        assertThat(captor.getValue().changeVersionId()).isEqualTo(VERSION_ID);
        verify(baselineRepository, never()).insert(any());
        verifyNoInteractions(projectRepository);
    }
}
