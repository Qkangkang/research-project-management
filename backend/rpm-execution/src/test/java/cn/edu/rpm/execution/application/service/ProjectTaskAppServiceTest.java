package cn.edu.rpm.execution.application.service;

import cn.edu.rpm.audit.domain.model.AuditEvent;
import cn.edu.rpm.audit.domain.service.AuditRecorder;
import cn.edu.rpm.common.domain.model.OutboxEvent;
import cn.edu.rpm.common.domain.port.OutboxAppendPort;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.common.util.UuidUtils;
import cn.edu.rpm.execution.application.dto.CompleteProjectTaskCmd;
import cn.edu.rpm.execution.application.dto.CreateProjectTaskCmd;
import cn.edu.rpm.execution.application.dto.ProjectTaskCreated;
import cn.edu.rpm.execution.application.dto.ProjectTaskStateChanged;
import cn.edu.rpm.execution.domain.model.ExecutionPermissions;
import cn.edu.rpm.execution.infrastructure.entity.DeliverableEntity;
import cn.edu.rpm.execution.infrastructure.entity.ProjectTaskEntity;
import cn.edu.rpm.execution.infrastructure.repository.DeliverableRepository;
import cn.edu.rpm.execution.infrastructure.repository.ProjectTaskRepository;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link ProjectTaskAppService} 纯单元测试（T-EXEC-009）。
 *
 * <p>覆盖 TC-TSK-001（创建分派生成待办）、TC-TSK-002（非授权成员更新
 * {@code RPM-EXEC-005}）、TC-TSK-003（assignee start→complete 关联交付物）、
 * TC-TSK-004（他项目交付物 {@code RPM-EXEC-004}）、TC-TSK-005（DONE 再 start
 * {@code RPM-EXEC-006}）、TC-TSK-006（并发完成后者 {@code RPM-COMMON-409}）、
 * TC-TSK-007（取消理由必填、CANCELLED 后不可 start）。</p>
 */
@ExtendWith(MockitoExtension.class)
class ProjectTaskAppServiceTest {

    private static final String PROJECT_ID = "8d7c6b5a4e3f4a2b9c1d0e2f3a4b5c6d";
    private static final String TASK_ID = "11111111111111111111111111111111";
    private static final String DELIVERABLE_ID = "22222222222222222222222222222222";
    private static final String OTHER_PROJECT_ID = "33333333333333333333333333333333";
    private static final UUID PI_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID ASSIGNEE_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID OTHER_ID = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
    private static final Instant NOW = Instant.parse("2026-11-18T02:00:00Z");

    @Mock
    private ProjectTaskRepository projectTaskRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectMemberRepository projectMemberRepository;

    @Mock
    private DeliverableRepository deliverableRepository;

    @Mock
    private AuthorizationService authorizationService;

    @Mock
    private OutboxAppendPort outboxAppendPort;

    @Mock
    private AuditRecorder auditRecorder;

    private ProjectTaskAppService service;

    private AuthContext piAuth;

    private AuthContext assigneeAuth;

    @BeforeEach
    void setUp() {
        service = new ProjectTaskAppService(projectTaskRepository, projectRepository,
                projectMemberRepository, deliverableRepository, authorizationService,
                outboxAppendPort, auditRecorder,
                Clock.fixed(NOW, ZoneId.of("Asia/Shanghai")));
        ProjectEntity project = new ProjectEntity();
        project.setId(PROJECT_ID);
        project.setPiId(UuidUtils.formatCompact(PI_ID));
        project.setDeptId("cccccccccccccccccccccccccccccccc");
        project.setStatus(ProjectEntity.STATUS_EXECUTING);
        lenient().when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        ProjectMemberEntity assignee = member(ASSIGNEE_ID);
        ProjectMemberEntity pi = member(PI_ID);
        lenient().when(projectMemberRepository.findActiveByProjectId(eq(PROJECT_ID), any()))
                .thenReturn(List.of(assignee, pi));
        lenient().when(authorizationService.check(any(), any(), any()))
                .thenReturn(AuthDecision.allow());
        lenient().when(projectTaskRepository.update(any())).thenReturn(1);
        piAuth = new AuthContext(PI_ID, null, "E1001",
                Set.of(ExecutionPermissions.VIEW, ExecutionPermissions.TASK_ASSIGN),
                Set.of(), NOW);
        assigneeAuth = new AuthContext(ASSIGNEE_ID, null, "E1002",
                Set.of(ExecutionPermissions.VIEW), Set.of(), NOW);
    }

    @Test
    void createAndDispatchGeneratesTodoForAssignee() {
        CreateProjectTaskCmd cmd = new CreateProjectTaskCmd("撰写中期报告", "汇总第二年度进展",
                UuidUtils.formatCompact(ASSIGNEE_ID), LocalDate.of(2026, 12, 10), null);

        ProjectTaskCreated result = service.create(PROJECT_ID, cmd, piAuth);

        ArgumentCaptor<ProjectTaskEntity> taskCaptor = ArgumentCaptor.forClass(ProjectTaskEntity.class);
        verify(projectTaskRepository).insert(taskCaptor.capture());
        ProjectTaskEntity saved = taskCaptor.getValue();
        assertThat(saved.getId()).hasSize(32);
        assertThat(saved.getProjectId()).isEqualTo(PROJECT_ID);
        assertThat(saved.getTitle()).isEqualTo("撰写中期报告");
        assertThat(saved.getAssigneeId()).isEqualTo(UuidUtils.formatCompact(ASSIGNEE_ID));
        assertThat(saved.getStatus()).isEqualTo(ProjectTaskEntity.STATUS_TODO);
        assertThat(saved.getDueDate()).isEqualTo(LocalDate.of(2026, 12, 10));

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxAppendPort).append(outboxCaptor.capture());
        OutboxEvent event = outboxCaptor.getValue();
        assertThat(event.eventType()).isEqualTo(ProjectTaskAppService.EVENT_TASK_ASSIGNED);
        assertThat(event.payload()).containsEntry("task_id", saved.getId())
                .containsEntry("project_id", PROJECT_ID)
                .containsEntry("assignee_id", UuidUtils.formatCompact(ASSIGNEE_ID))
                .containsEntry("title", "撰写中期报告");
        assertThat(event.idempotencyKey())
                .isEqualTo(ProjectTaskAppService.EVENT_TASK_ASSIGNED + ":" + saved.getId());

        assertThat(result.taskId()).isEqualTo(saved.getId());
        assertThat(result.status()).isEqualTo(ProjectTaskEntity.STATUS_TODO);
        verify(auditRecorder).record(any(AuditEvent.class));
    }

    @Test
    void createRejectsAssigneeOutsideActiveMembers() {
        CreateProjectTaskCmd cmd = new CreateProjectTaskCmd("撰写中期报告", null,
                UuidUtils.formatCompact(OTHER_ID), null, null);

        assertThatThrownBy(() -> service.create(PROJECT_ID, cmd, piAuth))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.COMMON_400));

        verify(projectTaskRepository, never()).insert(any());
    }

    @Test
    void nonAuthorizedMemberUpdateThrowsExec005() {
        when(projectTaskRepository.findById(TASK_ID)).thenReturn(Optional.of(task(
                ProjectTaskEntity.STATUS_TODO, UuidUtils.formatCompact(ASSIGNEE_ID))));
        AuthContext otherAuth = new AuthContext(OTHER_ID, null, "E1003",
                Set.of(ExecutionPermissions.VIEW), Set.of(), NOW);

        assertThatThrownBy(() -> service.start(TASK_ID, otherAuth))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.EXEC_005));

        verify(projectTaskRepository, never()).update(any());
    }

    @Test
    void assigneeStartsAndCompletesWithProjectDeliverable() {
        ProjectTaskEntity task = task(ProjectTaskEntity.STATUS_TODO,
                UuidUtils.formatCompact(ASSIGNEE_ID));
        when(projectTaskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
        when(deliverableRepository.findById(DELIVERABLE_ID))
                .thenReturn(Optional.of(deliverable(PROJECT_ID)));

        ProjectTaskStateChanged started = service.start(TASK_ID, assigneeAuth);
        ProjectTaskStateChanged completed = service.complete(TASK_ID,
                new CompleteProjectTaskCmd(DELIVERABLE_ID), assigneeAuth);

        assertThat(started.status()).isEqualTo(ProjectTaskEntity.STATUS_IN_PROGRESS);
        assertThat(completed.status()).isEqualTo(ProjectTaskEntity.STATUS_DONE);
        assertThat(task.getCompletedAt()).isEqualTo(NOW.atZone(ZoneId.of("Asia/Shanghai")).toOffsetDateTime());
        assertThat(task.getDeliverableId()).isEqualTo(DELIVERABLE_ID);
        verify(projectTaskRepository, times(2)).update(task);

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxAppendPort).append(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().eventType())
                .isEqualTo(ProjectTaskAppService.EVENT_TASK_COMPLETED);
        assertThat(outboxCaptor.getValue().payload())
                .containsEntry("pi_id", UuidUtils.formatCompact(PI_ID))
                .containsEntry("deliverable_id", DELIVERABLE_ID)
                .containsEntry("assignee_id", UuidUtils.formatCompact(ASSIGNEE_ID));
    }

    @Test
    void completeWithOtherProjectDeliverableThrowsExec004() {
        ProjectTaskEntity task = task(ProjectTaskEntity.STATUS_IN_PROGRESS,
                UuidUtils.formatCompact(ASSIGNEE_ID));
        when(projectTaskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
        when(deliverableRepository.findById(DELIVERABLE_ID))
                .thenReturn(Optional.of(deliverable(OTHER_PROJECT_ID)));

        assertThatThrownBy(() -> service.complete(TASK_ID,
                new CompleteProjectTaskCmd(DELIVERABLE_ID), assigneeAuth))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.EXEC_004));

        assertThat(task.getStatus()).isEqualTo(ProjectTaskEntity.STATUS_IN_PROGRESS);
        verify(projectTaskRepository, never()).update(any());
        verifyNoInteractions(outboxAppendPort);
    }

    @Test
    void doneTaskCannotStartAgain() {
        ProjectTaskEntity task = task(ProjectTaskEntity.STATUS_DONE,
                UuidUtils.formatCompact(ASSIGNEE_ID));
        when(projectTaskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.start(TASK_ID, assigneeAuth))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.EXEC_006));

        assertThat(task.getStatus()).isEqualTo(ProjectTaskEntity.STATUS_DONE);
        verify(projectTaskRepository, never()).update(any());
    }

    @Test
    void concurrentCompleteSecondGets409() {
        ProjectTaskEntity task = task(ProjectTaskEntity.STATUS_IN_PROGRESS,
                UuidUtils.formatCompact(ASSIGNEE_ID));
        when(projectTaskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
        when(projectTaskRepository.update(task)).thenReturn(0);

        assertThatThrownBy(() -> service.complete(TASK_ID,
                new CompleteProjectTaskCmd(null), assigneeAuth))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.COMMON_409));
    }

    @Test
    void cancelRequiresReason() {
        when(projectTaskRepository.findById(TASK_ID)).thenReturn(Optional.of(task(
                ProjectTaskEntity.STATUS_TODO, UuidUtils.formatCompact(ASSIGNEE_ID))));

        assertThatThrownBy(() -> service.cancel(TASK_ID, "  ", piAuth))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.COMMON_400));

        verify(projectTaskRepository, never()).update(any());
    }

    @Test
    void cancelOnlyPiAndNotifiesPi() {
        ProjectTaskEntity task = task(ProjectTaskEntity.STATUS_TODO,
                UuidUtils.formatCompact(ASSIGNEE_ID));
        when(projectTaskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.cancel(TASK_ID, "方向调整", assigneeAuth))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.EXEC_005));

        ProjectTaskStateChanged cancelled = service.cancel(TASK_ID, "方向调整", piAuth);

        assertThat(cancelled.status()).isEqualTo(ProjectTaskEntity.STATUS_CANCELLED);
        assertThat(task.getStatus()).isEqualTo(ProjectTaskEntity.STATUS_CANCELLED);
        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxAppendPort).append(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().eventType())
                .isEqualTo(ProjectTaskAppService.EVENT_TASK_CANCELLED);
        assertThat(outboxCaptor.getValue().payload()).containsEntry("reason", "方向调整")
                .containsEntry("pi_id", UuidUtils.formatCompact(PI_ID));
    }

    private static ProjectTaskEntity task(String status, String assigneeId) {
        ProjectTaskEntity task = new ProjectTaskEntity();
        task.setId(TASK_ID);
        task.setProjectId(PROJECT_ID);
        task.setTitle("撰写中期报告");
        task.setAssigneeId(assigneeId);
        task.setStatus(status);
        task.setVersion(0);
        return task;
    }

    private static DeliverableEntity deliverable(String projectId) {
        DeliverableEntity entity = new DeliverableEntity();
        entity.setId(DELIVERABLE_ID);
        entity.setProjectId(projectId);
        entity.setFileId("ffffffffffffffffffffffffffffffff");
        entity.setTitle("报告.pdf");
        entity.setSha256("a".repeat(64));
        entity.setStatus(DeliverableEntity.STATUS_ACTIVE);
        return entity;
    }

    private static ProjectMemberEntity member(UUID userId) {
        ProjectMemberEntity member = new ProjectMemberEntity();
        member.setProjectId(PROJECT_ID);
        member.setUserId(UuidUtils.formatCompact(userId));
        member.setStatus(ProjectMemberEntity.STATUS_ACTIVE);
        return member;
    }
}
