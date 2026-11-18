package cn.edu.rpm.project.domain.service;

import cn.edu.rpm.audit.domain.model.AuditEvent;
import cn.edu.rpm.audit.domain.service.AuditRecorder;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.iam.application.dto.AuthContext;
import cn.edu.rpm.iam.application.dto.AuthDecision;
import cn.edu.rpm.iam.domain.service.AuthorizationService;
import cn.edu.rpm.project.application.dto.ProjectStartConditions;
import cn.edu.rpm.project.application.dto.ProjectStateChanged;
import cn.edu.rpm.project.domain.model.ProjectTransitionAction;
import cn.edu.rpm.project.infrastructure.entity.ProjectEntity;
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
import java.time.ZoneId;
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
 * {@link ProjectLifecycleService} 纯单元测试（T-PRJ-012）。
 *
 * <p>覆盖 TC-PRJ-005～TC-PRJ-013、TC-PRJ-030～TC-PRJ-033 的状态迁移与非法分支：
 * 显式方法迁移、{@code RPM-PROJ-002}、审计 {@code PROJECT_STATE_CHANGED}。</p>
 */
@ExtendWith(MockitoExtension.class)
class ProjectLifecycleServiceTest {

    private static final String PROJECT_ID = "8d7c6b5a4e3f4a2b9c1d0e2f3a4b5c6d";
    private static final UUID ACTOR_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID PI_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID DEPT_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private static final Instant NOW = Instant.parse("2026-11-18T02:00:00Z");

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectMemberRepository projectMemberRepository;

    @Mock
    private AuthorizationService authorizationService;

    @Mock
    private AuditRecorder auditRecorder;

    private ProjectLifecycleService service;

    private ProjectEntity project;

    private AuthContext auth;

    @BeforeEach
    void setUp() {
        service = new ProjectLifecycleService(projectRepository, projectMemberRepository,
                authorizationService, auditRecorder,
                Clock.fixed(NOW, ZoneId.of("Asia/Shanghai")));
        project = new ProjectEntity();
        project.setId(PROJECT_ID);
        project.setPiId(PI_ID.toString().replace("-", ""));
        project.setDeptId(DEPT_ID.toString().replace("-", ""));
        project.setStatus(ProjectEntity.STATUS_PENDING_START);
        auth = new AuthContext(ACTOR_ID, null, "E001",
                Set.of("project:state:change"), Set.of(), NOW);
    }

    private void allowChange() {
        when(projectRepository.selectForUpdate(PROJECT_ID)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findActiveByProjectId(anyString(), any())).thenReturn(List.of());
        when(authorizationService.check(any(), any(), any())).thenReturn(AuthDecision.allow());
    }

    private void allowUpdate() {
        when(projectRepository.update(project)).thenReturn(1);
    }

    @Test
    void tcPrj005StartPendingProjectTransitionsToExecutingWithAudit() {
        allowChange();
        allowUpdate();

        ProjectStateChanged result = service.start(PROJECT_ID,
                new ProjectStartConditions(true, true), auth);

        assertThat(result.status()).isEqualTo(ProjectEntity.STATUS_EXECUTING);
        assertThat(project.getStatus()).isEqualTo(ProjectEntity.STATUS_EXECUTING);
        ArgumentCaptor<AuditEvent> audit = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditRecorder).record(audit.capture());
        assertThat(audit.getValue().action()).isEqualTo(ProjectLifecycleService.AUDIT_STATE_CHANGED);
        assertThat(audit.getValue().objectId()).isEqualTo(UUID.fromString("8d7c6b5a-4e3f-4a2b-9c1d-0e2f3a4b5c6d"));
    }

    @Test
    void tcPrj006StartWithoutConditionsKeepsPendingAndReturnsProj002() {
        assertThatThrownBy(() -> service.start(PROJECT_ID,
                new ProjectStartConditions(false, true), auth))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.PROJ_002));
        assertThat(project.getStatus()).isEqualTo(ProjectEntity.STATUS_PENDING_START);
        verify(projectRepository, never()).update(any());
    }

    @Test
    void tcPrj007PauseExecutingProjectTransitionsToSuspended() {
        project.setStatus(ProjectEntity.STATUS_EXECUTING);
        allowChange();
        allowUpdate();

        assertThat(service.pause(PROJECT_ID, "设备到货延迟", auth).status())
                .isEqualTo(ProjectEntity.STATUS_SUSPENDED);
    }

    @Test
    void tcPrj008ResumeSuspendedProjectTransitionsToExecuting() {
        project.setStatus(ProjectEntity.STATUS_SUSPENDED);
        allowChange();
        allowUpdate();

        assertThat(service.resume(PROJECT_ID, auth).status())
                .isEqualTo(ProjectEntity.STATUS_EXECUTING);
    }

    @Test
    void tcPrj009To011TerminateFromAnyNonTerminalState() {
        allowChange();
        for (String status : List.of(ProjectEntity.STATUS_PENDING_START,
                ProjectEntity.STATUS_EXECUTING, ProjectEntity.STATUS_SUSPENDED)) {
            project.setStatus(status);
            when(projectRepository.update(project)).thenReturn(1);

            assertThat(service.terminate(PROJECT_ID, "制度调整", auth).status())
                    .isEqualTo(ProjectEntity.STATUS_TERMINATED);
        }
    }

    @Test
    void tcPrj012CompletedProjectCannotResume() {
        allowChange();
        project.setStatus(ProjectEntity.STATUS_COMPLETED);

        assertThatThrownBy(() -> service.transition(PROJECT_ID,
                ProjectTransitionAction.RESUME, null, auth))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.PROJ_002));
        assertThat(project.getStatus()).isEqualTo(ProjectEntity.STATUS_COMPLETED);
    }

    @Test
    void tcPrj013SuspendedProjectCannotSubmitAcceptance() {
        allowChange();
        project.setStatus(ProjectEntity.STATUS_SUSPENDED);

        assertThatThrownBy(() -> service.transition(PROJECT_ID,
                ProjectTransitionAction.SUBMIT_ACCEPTANCE, null, auth))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.PROJ_002));
    }

    @Test
    void tcPrj030ExecutingProjectCannotCompleteWithoutAcceptance() {
        allowChange();
        project.setStatus(ProjectEntity.STATUS_EXECUTING);

        assertThatThrownBy(() -> service.transition(PROJECT_ID,
                ProjectTransitionAction.COMPLETE, "验收通过", auth))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.PROJ_002));
    }

    @Test
    void tcPrj031AcceptingProjectReturnsToExecuting() {
        allowChange();
        project.setStatus(ProjectEntity.STATUS_ACCEPTING);
        allowUpdate();

        assertThat(service.transition(PROJECT_ID, ProjectTransitionAction.RETURN_TO_EXECUTING,
                null, auth).status()).isEqualTo(ProjectEntity.STATUS_EXECUTING);
    }

    @Test
    void tcPrj032CompleteAcceptingProjectWritesFrozenInfo() {
        allowChange();
        project.setStatus(ProjectEntity.STATUS_ACCEPTING);
        allowUpdate();

        ProjectStateChanged result = service.transition(PROJECT_ID,
                ProjectTransitionAction.COMPLETE, "验收结论 PASS", auth);

        assertThat(result.status()).isEqualTo(ProjectEntity.STATUS_COMPLETED);
        assertThat(project.getFrozenAt()).isNotNull();
        assertThat(project.getFrozenReason()).isEqualTo("验收结论 PASS");
    }

    @Test
    void tcPrj033AcceptingProjectCanBeTerminated() {
        allowChange();
        project.setStatus(ProjectEntity.STATUS_ACCEPTING);
        allowUpdate();

        assertThat(service.terminate(PROJECT_ID, "验收不通过", auth).status())
                .isEqualTo(ProjectEntity.STATUS_TERMINATED);
    }

    @Test
    void missingProjectReturnsProj001() {
        when(projectRepository.selectForUpdate(PROJECT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resume(PROJECT_ID, auth))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.PROJ_001));
    }

    @Test
    void unauthorizedStateChangeReturnsCommon403() {
        allowChange();
        when(authorizationService.check(any(), any(), any()))
                .thenReturn(AuthDecision.deny(ErrorCode.COMMON_403));

        assertThatThrownBy(() -> service.resume(PROJECT_ID, auth))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.COMMON_403));
        verify(projectRepository, never()).update(any());
    }

    @Test
    void pauseAndTerminateRequireReason() {
        assertThatThrownBy(() -> service.pause(PROJECT_ID, " ", auth))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.COMMON_400));
        assertThatThrownBy(() -> service.terminate(PROJECT_ID, null, auth))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.COMMON_400));
    }
}
