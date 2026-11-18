package cn.edu.rpm.project.application.handler;

import cn.edu.rpm.audit.domain.model.AuditEvent;
import cn.edu.rpm.audit.domain.service.AuditRecorder;
import cn.edu.rpm.common.domain.port.OutboxAppendPort;
import cn.edu.rpm.common.util.CanonicalJsonHasher;
import cn.edu.rpm.project.domain.port.HandoverTaskTransferPort;
import cn.edu.rpm.project.infrastructure.entity.ProjectEntity;
import cn.edu.rpm.project.infrastructure.entity.ProjectHandoverEntity;
import cn.edu.rpm.project.infrastructure.entity.ProjectMemberEntity;
import cn.edu.rpm.project.infrastructure.repository.ProjectHandoverRepository;
import cn.edu.rpm.project.infrastructure.repository.ProjectMemberRepository;
import cn.edu.rpm.project.infrastructure.repository.ProjectRepository;
import cn.edu.rpm.project.infrastructure.repository.ProjectRiskRepository;
import cn.edu.rpm.workflow.domain.dto.ApprovalResult;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link HandoverApprovalHandler} 纯单元测试（T-PRJ-022）。
 *
 * <p>覆盖 TC-PRJ-026（生效后新 PI 生效、风险与待办转移）、
 * TC-PRJ-027（生效前一秒原 PI 仍有效，到期后切换）、
 * TC-PRJ-029 语义（历史操作人不由交接改写，仅新增交接审计）。</p>
 */
@ExtendWith(MockitoExtension.class)
class HandoverApprovalHandlerTest {

    private static final String PROJECT_ID = "8d7c6b5a4e3f4a2b9c1d0e2f3a4b5c6d";
    private static final String HANDOVER_ID = "11111111111111111111111111111111";
    private static final String OLD_PI = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String NEW_PI = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final UUID HANDOVER_UUID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OPERATOR_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID REQUEST_ID = UUID.fromString("abcdabcd-abcd-abcd-abcd-abcdabcdabcd");
    private static final Instant NOW = Instant.parse("2026-11-18T02:00:00Z");
    private static final OffsetDateTime EFFECTIVE_AT =
            OffsetDateTime.parse("2026-12-01T00:00:00+08:00");

    @Mock
    private ProjectHandoverRepository handoverRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectMemberRepository projectMemberRepository;

    @Mock
    private ProjectRiskRepository projectRiskRepository;

    @Mock
    private HandoverTaskTransferPort taskTransferPort;

    @Mock
    private OutboxAppendPort outboxAppendPort;

    @Mock
    private AuditRecorder auditRecorder;

    private HandoverApprovalHandler handler;

    private ProjectHandoverEntity handover;

    private ProjectEntity project;

    @BeforeEach
    void setUp() {
        handler = new HandoverApprovalHandler(handoverRepository, projectRepository,
                projectMemberRepository, projectRiskRepository, List.of(taskTransferPort),
                outboxAppendPort, auditRecorder, new CanonicalJsonHasher(),
                Clock.fixed(NOW, ZoneId.of("Asia/Shanghai")));
        handover = new ProjectHandoverEntity();
        handover.setId(HANDOVER_ID);
        handover.setProjectId(PROJECT_ID);
        handover.setFromUserId(OLD_PI);
        handover.setToUserId(NEW_PI);
        handover.setEffectiveAt(EFFECTIVE_AT);
        handover.setStatus(ProjectHandoverEntity.STATUS_IN_REVIEW);
        handover.setPendingItems("[]");
        handover.setReason("原负责人调岗");
        handover.setVersion(1);
        project = new ProjectEntity();
        project.setId(PROJECT_ID);
        project.setPiId(OLD_PI);
        project.setDeptId("dddddddddddddddddddddddddddddddd");
        project.setStatus(ProjectEntity.STATUS_EXECUTING);
        project.setStartDate(LocalDate.of(2026, 12, 1));
        project.setEndDate(LocalDate.of(2028, 11, 30));
        project.setVersion(2);
    }

    private ApprovalResult resultAt(OffsetDateTime occurredAt) {
        return new ApprovalResult(UUID.randomUUID(), "HANDOVER", HANDOVER_UUID, HANDOVER_UUID,
                "APPROVED", OPERATOR_ID, null, "同意交接", occurredAt, REQUEST_ID, List.of());
    }

    private void stubCommon() {
        lenient().when(handoverRepository.findById(HANDOVER_ID)).thenReturn(Optional.of(handover));
        lenient().when(handoverRepository.update(any())).thenReturn(1);
        lenient().when(projectRepository.selectForUpdate(PROJECT_ID)).thenReturn(Optional.of(project));
        lenient().when(projectRepository.update(any())).thenReturn(1);
        ProjectMemberEntity oldMember = new ProjectMemberEntity();
        oldMember.setProjectId(PROJECT_ID);
        oldMember.setUserId(OLD_PI);
        oldMember.setProjectRole("PI");
        oldMember.setStatus(ProjectMemberEntity.STATUS_ACTIVE);
        lenient().when(projectMemberRepository.findActiveByProjectId(any(), any()))
                .thenReturn(List.of(oldMember));
        lenient().when(projectMemberRepository.update(any())).thenReturn(1);
    }

    @Test
    void tcPrj026AfterEffectiveApprovalSwitchesPiAndTransfersItems() {
        stubCommon();

        handler.onApproved(resultAt(EFFECTIVE_AT.plusSeconds(1)));

        assertThat(handover.getStatus()).isEqualTo(ProjectHandoverEntity.STATUS_COMPLETED);
        assertThat(project.getPiId()).isEqualTo(NEW_PI);
        verify(projectRiskRepository).transferOwner(PROJECT_ID, OLD_PI, NEW_PI);
        verify(taskTransferPort).transferTasksAndTodos(PROJECT_ID, OLD_PI, NEW_PI);
        ArgumentCaptor<ProjectMemberEntity> memberCaptor =
                ArgumentCaptor.forClass(ProjectMemberEntity.class);
        verify(projectMemberRepository).insert(memberCaptor.capture());
        assertThat(memberCaptor.getValue().getUserId()).isEqualTo(NEW_PI);
        assertThat(memberCaptor.getValue().getProjectRole()).isEqualTo("PI");
        ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditRecorder).record(auditCaptor.capture());
        assertThat(auditCaptor.getValue().actorId()).isEqualTo(OPERATOR_ID);
        assertThat(auditCaptor.getValue().action())
                .isEqualTo(HandoverApprovalHandler.AUDIT_HANDOVER_COMPLETED);
        verify(outboxAppendPort).append(any());
    }

    @Test
    void tcPrj027BeforeEffectiveKeepsOldPiThenLazyApplySwitches() {
        stubCommon();
        handover.setStatus(ProjectHandoverEntity.STATUS_APPROVED);
        when(handoverRepository.findByStatusAndEffectiveAtBefore(
                ProjectHandoverEntity.STATUS_APPROVED, EFFECTIVE_AT.minusSeconds(1)))
                .thenReturn(List.of());
        when(handoverRepository.findByStatusAndEffectiveAtBefore(
                ProjectHandoverEntity.STATUS_APPROVED, EFFECTIVE_AT.plusSeconds(1)))
                .thenReturn(List.of(handover));

        handler.applyDue(EFFECTIVE_AT.minusSeconds(1));
        assertThat(project.getPiId()).isEqualTo(OLD_PI);
        verifyNoInteractions(projectRiskRepository);

        handler.applyDue(EFFECTIVE_AT.plusSeconds(1));

        assertThat(project.getPiId()).isEqualTo(NEW_PI);
        assertThat(handover.getStatus()).isEqualTo(ProjectHandoverEntity.STATUS_COMPLETED);
        verify(projectRiskRepository).transferOwner(PROJECT_ID, OLD_PI, NEW_PI);
    }

    @Test
    void rejectedHandoverCancelsWithoutChangingPi() {
        when(handoverRepository.findById(HANDOVER_ID)).thenReturn(Optional.of(handover));
        when(handoverRepository.update(any())).thenReturn(1);

        handler.onRejected(new ApprovalResult(UUID.randomUUID(), "HANDOVER", HANDOVER_UUID,
                HANDOVER_UUID, "REJECTED", OPERATOR_ID, null, "不同意",
                OffsetDateTime.now(Clock.fixed(NOW, ZoneId.of("Asia/Shanghai"))),
                REQUEST_ID, List.of()));

        assertThat(handover.getStatus()).isEqualTo(ProjectHandoverEntity.STATUS_CANCELLED);
        assertThat(project.getPiId()).isEqualTo(OLD_PI);
        verifyNoInteractions(projectRepository, projectRiskRepository, taskTransferPort);
        verify(handoverRepository, never()).insert(any());
    }

    @Test
    void approvalBeforeEffectiveStoresApprovedWithoutChangingPi() {
        stubCommon();

        handler.onApproved(resultAt(EFFECTIVE_AT.minusSeconds(1)));

        assertThat(handover.getStatus()).isEqualTo(ProjectHandoverEntity.STATUS_APPROVED);
        assertThat(project.getPiId()).isEqualTo(OLD_PI);
        verifyNoInteractions(projectRiskRepository, taskTransferPort);
        verify(projectRepository, never()).update(any());
    }
}
