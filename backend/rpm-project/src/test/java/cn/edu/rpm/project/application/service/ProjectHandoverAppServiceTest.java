package cn.edu.rpm.project.application.service;

import cn.edu.rpm.audit.domain.service.AuditRecorder;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.common.util.CanonicalJsonHasher;
import cn.edu.rpm.iam.application.dto.AuthContext;
import cn.edu.rpm.iam.application.dto.AuthDecision;
import cn.edu.rpm.iam.domain.service.AuthorizationService;
import cn.edu.rpm.iam.infrastructure.entity.IamUserEntity;
import cn.edu.rpm.iam.infrastructure.repository.IamUserRepository;
import cn.edu.rpm.project.application.dto.InitiateHandoverCmd;
import cn.edu.rpm.project.application.dto.ProjectHandoverInitiated;
import cn.edu.rpm.project.domain.port.ProjectApprovalWorkflowLauncher;
import cn.edu.rpm.project.domain.port.ProjectApprovalWorkflowStart;
import cn.edu.rpm.project.domain.port.ProjectHandoverChecklistPort;
import cn.edu.rpm.project.infrastructure.entity.ProjectEntity;
import cn.edu.rpm.project.infrastructure.entity.ProjectHandoverEntity;
import cn.edu.rpm.project.infrastructure.repository.ProjectHandoverRepository;
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
import java.time.OffsetDateTime;
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
 * {@link ProjectHandoverAppService} 纯单元测试（T-PRJ-020）。
 *
 * <p>覆盖 TC-PRJ-025（清单遗漏未结待办/风险返回 {@code RPM-PROJ-006}）、
 * TC-PRJ-028（新负责人离岗/禁用返回 {@code RPM-PROJ-006}），并验证完整清单
 * 创建 {@code IN_REVIEW} 交接单且不提前修改 {@code pi_id}。</p>
 */
@ExtendWith(MockitoExtension.class)
class ProjectHandoverAppServiceTest {

    private static final String PROJECT_ID = "8d7c6b5a4e3f4a2b9c1d0e2f3a4b5c6d";
    private static final String TO_USER_ID = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final String RISK_ID = "cccccccccccccccccccccccccccccccc";
    private static final String TASK_ID = "dddddddddddddddddddddddddddddddd";
    private static final String INSTANCE_ID = "inst-handover-0001";
    private static final UUID PI_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID REQUEST_ID = UUID.fromString("abcdabcd-abcd-abcd-abcd-abcdabcdabcd");
    private static final Instant NOW = Instant.parse("2026-11-18T02:00:00Z");
    private static final OffsetDateTime EFFECTIVE_AT =
            OffsetDateTime.parse("2026-12-01T00:00:00+08:00");

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectMemberRepository projectMemberRepository;

    @Mock
    private ProjectHandoverRepository handoverRepository;

    @Mock
    private IamUserRepository iamUserRepository;

    @Mock
    private ProjectHandoverChecklistPort checklistPort;

    @Mock
    private ProjectApprovalWorkflowLauncher workflowLauncher;

    @Mock
    private AuthorizationService authorizationService;

    @Mock
    private AuditRecorder auditRecorder;

    private ProjectHandoverAppService service;

    private ProjectEntity project;

    private AuthContext auth;

    @BeforeEach
    void setUp() {
        service = new ProjectHandoverAppService(projectRepository, projectMemberRepository,
                handoverRepository, iamUserRepository, List.of(checklistPort), workflowLauncher,
                authorizationService, auditRecorder, new CanonicalJsonHasher(),
                Clock.fixed(NOW, ZoneId.of("Asia/Shanghai")));
        project = new ProjectEntity();
        project.setId(PROJECT_ID);
        project.setPiId("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        project.setDeptId("eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee");
        project.setProjectType("YEAR_PLAN");
        project.setStatus(ProjectEntity.STATUS_EXECUTING);
        project.setCurrentBaselineId("ffffffffffffffffffffffffffffffff");
        project.setStartDate(LocalDate.of(2026, 12, 1));
        project.setEndDate(LocalDate.of(2028, 11, 30));
        auth = new AuthContext(PI_ID, null, "E1001",
                Set.of("project:handover"), Set.of(), NOW);
    }

    private void stubBase() {
        when(projectRepository.selectForUpdate(PROJECT_ID)).thenReturn(Optional.of(project));
        when(authorizationService.check(any(), any(), any())).thenReturn(AuthDecision.allow());
        lenient().when(projectMemberRepository.findActiveByProjectId(any(), any()))
                .thenReturn(List.of());
    }

    private void stubActiveUser() {
        IamUserEntity user = new IamUserEntity();
        user.setId(TO_USER_ID);
        user.setStatus("ACTIVE");
        when(iamUserRepository.findById(TO_USER_ID)).thenReturn(Optional.of(user));
    }

    private InitiateHandoverCmd cmd(List<InitiateHandoverCmd.PendingItem> items) {
        return new InitiateHandoverCmd(TO_USER_ID, EFFECTIVE_AT, items,
                "原负责人调岗，办理交接", REQUEST_ID);
    }

    @Test
    void tcPrj025IncompleteChecklistReturnsProj006WithoutCreatingHandover() {
        stubBase();
        stubActiveUser();
        when(checklistPort.findOpenItems(PROJECT_ID)).thenReturn(List.of(
                new ProjectHandoverChecklistPort.OpenItem("RISK", RISK_ID),
                new ProjectHandoverChecklistPort.OpenItem("TASK", TASK_ID)));

        assertThatThrownBy(() -> service.initiate(PROJECT_ID, cmd(List.of(
                new InitiateHandoverCmd.PendingItem("TASK", TASK_ID, "TRANSFER"))), auth))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> {
                    BizException biz = (BizException) ex;
                    assertThat(biz.getErrorCode()).isEqualTo(ErrorCode.PROJ_006);
                    assertThat(biz.getMessage()).contains("RISK:" + RISK_ID);
                });

        verify(handoverRepository, never()).insert(any());
        verifyNoInteractions(workflowLauncher);
        assertThat(project.getPiId()).isEqualTo("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
    }

    @Test
    void tcPrj028InactiveNewPiReturnsProj006WithoutCreatingHandover() {
        stubBase();
        IamUserEntity leaved = new IamUserEntity();
        leaved.setId(TO_USER_ID);
        leaved.setStatus("LEAVED");
        when(iamUserRepository.findById(TO_USER_ID)).thenReturn(Optional.of(leaved));

        assertThatThrownBy(() -> service.initiate(PROJECT_ID, cmd(List.of()), auth))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.PROJ_006));

        verify(handoverRepository, never()).insert(any());
        verifyNoInteractions(workflowLauncher);
    }

    @Test
    void completeChecklistCreatesInReviewHandoverAndKeepsPi() {
        stubBase();
        stubActiveUser();
        when(checklistPort.findOpenItems(PROJECT_ID)).thenReturn(List.of(
                new ProjectHandoverChecklistPort.OpenItem("RISK", RISK_ID),
                new ProjectHandoverChecklistPort.OpenItem("TASK", TASK_ID)));
        when(workflowLauncher.start(any())).thenReturn(INSTANCE_ID);

        ProjectHandoverInitiated result = service.initiate(PROJECT_ID, cmd(List.of(
                new InitiateHandoverCmd.PendingItem("TASK", TASK_ID, "TRANSFER"),
                new InitiateHandoverCmd.PendingItem("RISK", RISK_ID, "TRANSFER"))), auth);

        assertThat(result.status()).isEqualTo(ProjectHandoverEntity.STATUS_IN_REVIEW);
        assertThat(result.workflowInstanceId()).isEqualTo(INSTANCE_ID);
        assertThat(project.getPiId()).isEqualTo("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");

        ArgumentCaptor<ProjectHandoverEntity> captor =
                ArgumentCaptor.forClass(ProjectHandoverEntity.class);
        verify(handoverRepository).insert(captor.capture());
        ProjectHandoverEntity handover = captor.getValue();
        assertThat(handover.getStatus()).isEqualTo(ProjectHandoverEntity.STATUS_IN_REVIEW);
        assertThat(handover.getFromUserId()).isEqualTo("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        assertThat(handover.getToUserId()).isEqualTo(TO_USER_ID);
        assertThat(handover.getPendingItems()).contains(RISK_ID).contains(TASK_ID);

        ArgumentCaptor<ProjectApprovalWorkflowStart> startCaptor =
                ArgumentCaptor.forClass(ProjectApprovalWorkflowStart.class);
        verify(workflowLauncher).start(startCaptor.capture());
        assertThat(startCaptor.getValue().bizType()).isEqualTo("HANDOVER");
        assertThat(startCaptor.getValue().templateCode()).isEqualTo("HANDOVER");
    }
}
