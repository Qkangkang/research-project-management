package cn.edu.rpm.execution.application.service;

import cn.edu.rpm.audit.domain.service.AuditRecorder;
import cn.edu.rpm.common.domain.model.OutboxEvent;
import cn.edu.rpm.common.domain.port.OutboxAppendPort;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.execution.application.dto.ProgressReportReviewed;
import cn.edu.rpm.execution.application.dto.ReviewProgressReportCmd;
import cn.edu.rpm.execution.infrastructure.entity.ProgressReportEntity;
import cn.edu.rpm.execution.infrastructure.entity.ProgressReportVersionEntity;
import cn.edu.rpm.execution.infrastructure.repository.ProgressReportRepository;
import cn.edu.rpm.execution.infrastructure.repository.ProgressReportVersionRepository;
import cn.edu.rpm.iam.application.dto.AuthContext;
import cn.edu.rpm.iam.application.dto.AuthDecision;
import cn.edu.rpm.iam.domain.service.AuthorizationService;
import cn.edu.rpm.project.infrastructure.entity.ProjectEntity;
import cn.edu.rpm.project.infrastructure.entity.ProjectMemberEntity;
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
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
 * {@link ProgressReportReviewService} 纯单元测试（T-EXEC-007）。
 *
 * <p>覆盖 TC-EXEC-010（退回置 {@code RETURNED}、旧版本保留、生成补正待办事件）、
 * TC-EXEC-012（通过置 {@code APPROVED}、当前版本固定、不新增版本）。</p>
 */
@ExtendWith(MockitoExtension.class)
class ProgressReportReviewServiceTest {

    private static final String PROJECT_ID = "8d7c6b5a4e3f4a2b9c1d0e2f3a4b5c6d";
    private static final String REPORT_ID = "11111111111111111111111111111111";
    private static final String VERSION_ID = "22222222222222222222222222222222";
    private static final String APPLICANT_ID = "dddddddddddddddddddddddddddddddd";
    private static final UUID REVIEWER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID REQUEST_ID = UUID.fromString("abcdabcd-abcd-abcd-abcd-abcdabcdabcd");
    private static final Instant NOW = Instant.parse("2026-11-18T02:00:00Z");

    @Mock
    private ProgressReportRepository progressReportRepository;

    @Mock
    private ProgressReportVersionRepository versionRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectMemberRepository projectMemberRepository;

    @Mock
    private OutboxAppendPort outboxAppendPort;

    @Mock
    private AuthorizationService authorizationService;

    @Mock
    private AuditRecorder auditRecorder;

    private ProgressReportReviewService service;

    private ProgressReportEntity report;

    private ProgressReportVersionEntity version;

    private AuthContext auth;

    @BeforeEach
    void setUp() {
        service = new ProgressReportReviewService(progressReportRepository, versionRepository,
                projectRepository, projectMemberRepository, outboxAppendPort,
                authorizationService, auditRecorder,
                Clock.fixed(NOW, ZoneId.of("Asia/Shanghai")));
        report = new ProgressReportEntity();
        report.setId(REPORT_ID);
        report.setProjectId(PROJECT_ID);
        report.setStatus(ProgressReportEntity.STATUS_SUBMITTED);
        report.setCurrentVersionId(VERSION_ID);
        report.setDueDate(LocalDate.of(2026, 11, 30));
        report.setVersion(1);
        version = new ProgressReportVersionEntity();
        version.setId(VERSION_ID);
        version.setReportId(REPORT_ID);
        version.setVersionNo(1);
        version.setSubmittedBy(APPLICANT_ID);
        version.setDecision(ProgressReportVersionEntity.DECISION_PENDING);
        ProjectEntity project = new ProjectEntity();
        project.setId(PROJECT_ID);
        project.setPiId("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
        project.setDeptId("cccccccccccccccccccccccccccccccc");
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(progressReportRepository.selectForUpdate(REPORT_ID)).thenReturn(Optional.of(report));
        when(authorizationService.check(any(), any(), any())).thenReturn(AuthDecision.allow());
        ProjectMemberEntity member = new ProjectMemberEntity();
        member.setUserId(APPLICANT_ID);
        member.setStatus(ProjectMemberEntity.STATUS_ACTIVE);
        lenient().when(projectMemberRepository.findActiveByProjectId(any(), any()))
                .thenReturn(List.of(member));
        lenient().when(progressReportRepository.update(any())).thenReturn(1);
        lenient().when(versionRepository.update(any())).thenReturn(1);
        auth = new AuthContext(REVIEWER_ID, null, "E2001",
                Set.of("execution:report:review"), Set.of(), NOW);
    }

    @Test
    void tcExec010ReturnMarksReturnedKeepsVersionAndEmitsTodoEvent() {
        when(versionRepository.findById(VERSION_ID)).thenReturn(Optional.of(version));
        ReviewProgressReportCmd cmd = new ReviewProgressReportCmd(
                "偏差说明不足，请补充供应链延迟的影响评估", REQUEST_ID);

        ProgressReportReviewed result = service.returnReport(REPORT_ID, cmd, auth);

        assertThat(result.status()).isEqualTo(ProgressReportEntity.STATUS_RETURNED);
        assertThat(result.decision()).isEqualTo(ProgressReportVersionEntity.DECISION_RETURN);
        assertThat(report.getStatus()).isEqualTo(ProgressReportEntity.STATUS_RETURNED);
        assertThat(report.getCurrentVersionId()).isEqualTo(VERSION_ID);
        assertThat(version.getDecision()).isEqualTo(ProgressReportVersionEntity.DECISION_RETURN);
        assertThat(version.getReviewComment())
                .isEqualTo("偏差说明不足，请补充供应链延迟的影响评估");
        verify(versionRepository, never()).insert(any());

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxAppendPort).append(outboxCaptor.capture());
        OutboxEvent event = outboxCaptor.getValue();
        assertThat(event.eventType()).isEqualTo("REPORT_RETURNED");
        assertThat(event.aggregateId()).isEqualTo(UUID.fromString(
                "11111111-1111-1111-1111-111111111111"));
        assertThat(event.payload()).containsEntry("report_id", REPORT_ID);
        assertThat(event.payload()).containsEntry("assignee_id", APPLICANT_ID);
        assertThat(event.idempotencyKey()).isEqualTo("REPORT_RETURNED:" + VERSION_ID);
    }

    @Test
    void tcExec012ApproveMarksApprovedAndPinsCurrentVersion() {
        when(versionRepository.findById(VERSION_ID)).thenReturn(Optional.of(version));
        ReviewProgressReportCmd cmd = new ReviewProgressReportCmd("同意", REQUEST_ID);

        ProgressReportReviewed result = service.approve(REPORT_ID, cmd, auth);

        assertThat(result.status()).isEqualTo(ProgressReportEntity.STATUS_APPROVED);
        assertThat(result.versionId()).isEqualTo(VERSION_ID);
        assertThat(report.getStatus()).isEqualTo(ProgressReportEntity.STATUS_APPROVED);
        assertThat(report.getCurrentVersionId()).isEqualTo(VERSION_ID);
        assertThat(version.getDecision()).isEqualTo(ProgressReportVersionEntity.DECISION_PASS);
        verify(versionRepository, never()).insert(any());
        verifyNoInteractions(outboxAppendPort);
    }

    @Test
    void returnWithoutCommentReturnsCommon400() {
        assertThatThrownBy(() -> service.returnReport(REPORT_ID,
                new ReviewProgressReportCmd("  ", REQUEST_ID), auth))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.COMMON_400));

        verify(versionRepository, never()).update(any());
        verify(progressReportRepository, never()).update(any());
    }

    @Test
    void reviewOnDraftReportReturnsCommon422() {
        report.setStatus(ProgressReportEntity.STATUS_DRAFT);

        assertThatThrownBy(() -> service.approve(REPORT_ID,
                new ReviewProgressReportCmd("同意", REQUEST_ID), auth))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.COMMON_422));

        verify(versionRepository, never()).update(any());
    }
}
