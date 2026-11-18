package cn.edu.rpm.project.application.service;

import cn.edu.rpm.audit.domain.model.AuditEvent;
import cn.edu.rpm.audit.domain.service.AuditRecorder;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.common.util.CanonicalJsonHasher;
import cn.edu.rpm.iam.application.dto.AuthContext;
import cn.edu.rpm.iam.application.dto.AuthDecision;
import cn.edu.rpm.iam.domain.service.AuthorizationService;
import cn.edu.rpm.project.application.dto.MilestoneDelayRequestCmd;
import cn.edu.rpm.project.application.dto.MilestoneDelayRequestResult;
import cn.edu.rpm.project.domain.port.MilestoneDelayWorkflowLauncher;
import cn.edu.rpm.project.domain.port.MilestoneDelayWorkflowStart;
import cn.edu.rpm.project.infrastructure.entity.MilestoneDelayRequestEntity;
import cn.edu.rpm.project.infrastructure.entity.MilestoneEntity;
import cn.edu.rpm.project.infrastructure.entity.ProjectEntity;
import cn.edu.rpm.project.infrastructure.repository.MilestoneDelayRequestRepository;
import cn.edu.rpm.project.infrastructure.repository.MilestoneRepository;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link MilestoneDelayAppService} 纯单元测试（T-PRJ-016）。
 *
 * <p>覆盖 TC-EXED-001～007：PI 提交返回 PENDING + instanceId；非 PI 403；
 * 重复 PENDING 409；已完成里程碑 422；项目非执行中 {@code RPM-PROJ-002}；
 * 新日期/理由非法 400；撤回仅申请人且仅 PENDING。</p>
 */
@ExtendWith(MockitoExtension.class)
class MilestoneDelayAppServiceTest {

    private static final String PROJECT_ID = "8d7c6b5a4e3f4a2b9c1d0e2f3a4b5c6d";
    private static final String MILESTONE_ID = "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee";
    private static final String BASELINE_ID = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final String REQUEST_ID = "ffffffffffffffffffffffffffffffff";
    private static final String INSTANCE_ID = "inst-delay-0001";
    private static final UUID PI_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID OTHER_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final Instant NOW = Instant.parse("2026-11-18T02:00:00Z");

    @Mock
    private MilestoneRepository milestoneRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectMemberRepository projectMemberRepository;

    @Mock
    private MilestoneDelayRequestRepository delayRequestRepository;

    @Mock
    private MilestoneDelayWorkflowLauncher workflowLauncher;

    @Mock
    private AuthorizationService authorizationService;

    @Mock
    private AuditRecorder auditRecorder;

    private MilestoneDelayAppService service;

    private ProjectEntity project;

    private MilestoneEntity milestone;

    @BeforeEach
    void setUp() {
        service = new MilestoneDelayAppService(milestoneRepository, projectRepository,
                projectMemberRepository, delayRequestRepository, workflowLauncher,
                authorizationService, auditRecorder, new CanonicalJsonHasher(),
                Clock.fixed(NOW, ZoneId.of("Asia/Shanghai")));
        project = new ProjectEntity();
        project.setId(PROJECT_ID);
        project.setPiId("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        project.setDeptId("dddddddddddddddddddddddddddddddd");
        project.setProjectType("YEAR_PLAN");
        project.setStatus(ProjectEntity.STATUS_EXECUTING);
        milestone = new MilestoneEntity();
        milestone.setId(MILESTONE_ID);
        milestone.setProjectId(PROJECT_ID);
        milestone.setBaselineId(BASELINE_ID);
        milestone.setPlanDate(LocalDate.of(2027, 3, 31));
        milestone.setCurrentPlanDate(LocalDate.of(2027, 3, 31));
        milestone.setStatus(MilestoneEntity.STATUS_PLANNED);
    }

    private AuthContext auth(UUID userId) {
        return new AuthContext(userId, null, "E001",
                Set.of("project:milestone:manage"), Set.of(), NOW);
    }

    private MilestoneDelayRequestCmd cmd(LocalDate date, String reason) {
        return new MilestoneDelayRequestCmd(date, reason, List.of("f1"));
    }

    private void allowSubmit() {
        when(milestoneRepository.selectForUpdate(MILESTONE_ID)).thenReturn(Optional.of(milestone));
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findActiveByProjectId(anyString(), any())).thenReturn(List.of());
        when(authorizationService.check(any(), any(), any())).thenReturn(AuthDecision.allow());
    }

    private void stubInsertAssignsId() {
        doAnswer(invocation -> {
            ((MilestoneDelayRequestEntity) invocation.getArgument(0)).setId(REQUEST_ID);
            return null;
        }).when(delayRequestRepository).insert(any());
    }

    @Test
    void tcExed001PiSubmitReturnsPendingWithInstanceId() {
        allowSubmit();
        stubInsertAssignsId();
        when(workflowLauncher.start(any())).thenReturn(INSTANCE_ID);
        when(delayRequestRepository.update(any())).thenReturn(1);
        ArgumentCaptor<MilestoneDelayWorkflowStart> start = ArgumentCaptor.forClass(
                MilestoneDelayWorkflowStart.class);

        MilestoneDelayRequestResult result = service.requestDelay(MILESTONE_ID,
                cmd(LocalDate.of(2027, 4, 30), "关键设备到货延迟导致阶段验证顺延"), auth(PI_ID));

        assertThat(result.status()).isEqualTo(MilestoneDelayRequestEntity.STATUS_PENDING);
        assertThat(result.instanceId()).isEqualTo(INSTANCE_ID);
        assertThat(result.requestId()).isEqualTo(REQUEST_ID);
        verify(workflowLauncher).start(start.capture());
        assertThat(start.getValue().requestId()).isEqualTo(UUID.fromString(
                "ffffffff-ffff-ffff-ffff-ffffffffffff"));
        assertThat(start.getValue().milestoneId()).isEqualTo(UUID.fromString(
                "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee"));
        ArgumentCaptor<MilestoneDelayRequestEntity> saved = ArgumentCaptor.forClass(
                MilestoneDelayRequestEntity.class);
        verify(delayRequestRepository).insert(saved.capture());
        assertThat(saved.getValue().getOriginalPlanDate()).isEqualTo(LocalDate.of(2027, 3, 31));
        assertThat(saved.getValue().getDelayDays()).isEqualTo(30);
        assertThat(saved.getValue().getRequestedBy()).isEqualTo("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        verify(delayRequestRepository).update(saved.getValue());
        assertThat(saved.getValue().getWfInstanceId()).isEqualTo(INSTANCE_ID);
        ArgumentCaptor<AuditEvent> audit = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditRecorder).record(audit.capture());
        assertThat(audit.getValue().action()).isEqualTo(MilestoneDelayAppService.AUDIT_DELAY_REQUESTED);
    }

    @Test
    void tcExed002NonPiReturnsCommon403() {
        allowSubmit();

        assertThatThrownBy(() -> service.requestDelay(MILESTONE_ID,
                cmd(LocalDate.of(2027, 4, 30), "关键设备到货延迟导致阶段验证顺延"), auth(OTHER_ID)))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.COMMON_403));
        verify(delayRequestRepository, never()).insert(any());
    }

    @Test
    void tcExed004DuplicatePendingReturnsCommon409() {
        allowSubmit();
        when(delayRequestRepository.findPendingByMilestoneId(MILESTONE_ID))
                .thenReturn(Optional.of(new MilestoneDelayRequestEntity()));

        assertThatThrownBy(() -> service.requestDelay(MILESTONE_ID,
                cmd(LocalDate.of(2027, 4, 30), "关键设备到货延迟导致阶段验证顺延"), auth(PI_ID)))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.COMMON_409));
        verify(workflowLauncher, never()).start(any());
    }

    @Test
    void tcExed005CompletedMilestoneReturnsCommon422() {
        allowSubmit();
        milestone.setStatus(MilestoneEntity.STATUS_COMPLETED);

        assertThatThrownBy(() -> service.requestDelay(MILESTONE_ID,
                cmd(LocalDate.of(2027, 4, 30), "关键设备到货延迟导致阶段验证顺延"), auth(PI_ID)))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.COMMON_422));
    }

    @Test
    void projectNotExecutingReturnsProj002() {
        allowSubmit();
        project.setStatus(ProjectEntity.STATUS_SUSPENDED);

        assertThatThrownBy(() -> service.requestDelay(MILESTONE_ID,
                cmd(LocalDate.of(2027, 4, 30), "关键设备到货延迟导致阶段验证顺延"), auth(PI_ID)))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.PROJ_002));
    }

    @Test
    void requestedDateNotAfterOriginalReturnsCommon400() {
        allowSubmit();

        assertThatThrownBy(() -> service.requestDelay(MILESTONE_ID,
                cmd(LocalDate.of(2027, 3, 31), "关键设备到货延迟导致阶段验证顺延"), auth(PI_ID)))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.COMMON_400));
    }

    @Test
    void reasonShorterThanTenCharsReturnsCommon400() {
        allowSubmit();

        assertThatThrownBy(() -> service.requestDelay(MILESTONE_ID,
                cmd(LocalDate.of(2027, 4, 30), "设备延迟"), auth(PI_ID)))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.COMMON_400));
    }

    @Test
    void missingMilestoneReturnsCommon404() {
        when(milestoneRepository.selectForUpdate(MILESTONE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requestDelay(MILESTONE_ID,
                cmd(LocalDate.of(2027, 4, 30), "关键设备到货延迟导致阶段验证顺延"), auth(PI_ID)))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.COMMON_404));
    }

    private MilestoneDelayRequestEntity pendingRequest() {
        MilestoneDelayRequestEntity request = new MilestoneDelayRequestEntity();
        request.setId(REQUEST_ID);
        request.setMilestoneId(MILESTONE_ID);
        request.setProjectId(PROJECT_ID);
        request.setRequestedBy("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        request.setStatus(MilestoneDelayRequestEntity.STATUS_PENDING);
        request.setWfInstanceId(INSTANCE_ID);
        return request;
    }

    @Test
    void tcExed007CancelByApplicantCancelsWorkflow() {
        MilestoneDelayRequestEntity request = pendingRequest();
        when(delayRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));
        when(delayRequestRepository.update(request)).thenReturn(1);

        service.cancel(REQUEST_ID, auth(PI_ID));

        assertThat(request.getStatus()).isEqualTo(MilestoneDelayRequestEntity.STATUS_CANCELLED);
        verify(workflowLauncher).cancel(eq(INSTANCE_ID), eq(PI_ID), anyString(),
                eq(UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff")));
        ArgumentCaptor<AuditEvent> audit = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditRecorder).record(audit.capture());
        assertThat(audit.getValue().action()).isEqualTo(MilestoneDelayAppService.AUDIT_DELAY_CANCELLED);
    }

    @Test
    void cancelByOtherUserReturnsCommon403() {
        when(delayRequestRepository.findById(REQUEST_ID))
                .thenReturn(Optional.of(pendingRequest()));

        assertThatThrownBy(() -> service.cancel(REQUEST_ID, auth(OTHER_ID)))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.COMMON_403));
        verify(workflowLauncher, never()).cancel(any(), any(), any(), any());
    }

    @Test
    void cancelNonPendingReturnsCommon422() {
        MilestoneDelayRequestEntity request = pendingRequest();
        request.setStatus(MilestoneDelayRequestEntity.STATUS_APPROVED);
        when(delayRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.cancel(REQUEST_ID, auth(PI_ID)))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.COMMON_422));
    }
}
