package cn.edu.rpm.project.application.service;

import cn.edu.rpm.audit.domain.service.AuditRecorder;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.common.util.CanonicalJsonHasher;
import cn.edu.rpm.common.util.UuidUtils;
import cn.edu.rpm.iam.application.dto.AuthContext;
import cn.edu.rpm.iam.application.dto.AuthDecision;
import cn.edu.rpm.iam.domain.service.AuthorizationService;
import cn.edu.rpm.iam.infrastructure.entity.IamUserEntity;
import cn.edu.rpm.iam.infrastructure.repository.IamUserRepository;
import cn.edu.rpm.project.application.dto.ChangeRequestCreated;
import cn.edu.rpm.project.application.dto.CreateChangeRequestCmd;
import cn.edu.rpm.project.domain.port.ProjectApprovalWorkflowLauncher;
import cn.edu.rpm.project.domain.port.ProjectApprovalWorkflowStart;
import cn.edu.rpm.project.infrastructure.entity.ChangeRequestEntity;
import cn.edu.rpm.project.infrastructure.entity.ChangeRequestVersionEntity;
import cn.edu.rpm.project.infrastructure.entity.ProjectEntity;
import cn.edu.rpm.project.infrastructure.entity.ProjectMemberEntity;
import cn.edu.rpm.project.infrastructure.repository.ChangeRequestRepository;
import cn.edu.rpm.project.infrastructure.repository.ChangeRequestVersionRepository;
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
import java.util.Map;
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
 * {@link ChangeRequestAppService} 纯单元测试（T-PRJ-019）。
 *
 * <p>覆盖 TC-PRJ-020（周期变更创建 {@code IN_REVIEW} 且沿用旧基线）、
 * TC-CHG-101（六类变更必填字段缺失返回 400 并定位到 payload 字段）、
 * TC-CHG-102（MEMBER 与现有成员重叠返回 422）、
 * TC-CHG-106（非 PI/科研管理发起返回 403）。</p>
 */
@ExtendWith(MockitoExtension.class)
class ChangeRequestAppServiceTest {

    private static final String PROJECT_ID = "8d7c6b5a4e3f4a2b9c1d0e2f3a4b5c6d";
    private static final String BASELINE_ID = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final String EXISTING_USER_ID = "cccccccccccccccccccccccccccccccc";
    private static final String NEW_PI_ID = "dddddddddddddddddddddddddddddddd";
    private static final String INSTANCE_ID = "inst-change-0001";
    private static final UUID PI_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID REQUEST_ID = UUID.fromString("abcdabcd-abcd-abcd-abcd-abcdabcdabcd");
    private static final Instant NOW = Instant.parse("2026-11-18T02:00:00Z");

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectMemberRepository projectMemberRepository;

    @Mock
    private ChangeRequestRepository changeRequestRepository;

    @Mock
    private ChangeRequestVersionRepository changeRequestVersionRepository;

    @Mock
    private IamUserRepository iamUserRepository;

    @Mock
    private ProjectApprovalWorkflowLauncher workflowLauncher;

    @Mock
    private AuthorizationService authorizationService;

    @Mock
    private AuditRecorder auditRecorder;

    private ChangeRequestAppService service;

    private ProjectEntity project;

    private AuthContext auth;

    @BeforeEach
    void setUp() {
        service = new ChangeRequestAppService(projectRepository, projectMemberRepository,
                changeRequestRepository, changeRequestVersionRepository, iamUserRepository,
                workflowLauncher, authorizationService, auditRecorder,
                new CanonicalJsonHasher(), Clock.fixed(NOW, ZoneId.of("Asia/Shanghai")));
        project = new ProjectEntity();
        project.setId(PROJECT_ID);
        project.setPiId("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        project.setDeptId("eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee");
        project.setProjectType("YEAR_PLAN");
        project.setStatus(ProjectEntity.STATUS_EXECUTING);
        project.setCurrentBaselineId(BASELINE_ID);
        project.setStartDate(LocalDate.of(2026, 12, 1));
        project.setEndDate(LocalDate.of(2028, 11, 30));
        auth = new AuthContext(PI_ID, null, "E1001",
                Set.of("project:change:create"), Set.of(), NOW);
    }

    private void stubProjectAndAuth() {
        when(projectRepository.selectForUpdate(PROJECT_ID)).thenReturn(Optional.of(project));
        when(authorizationService.check(any(), any(), any())).thenReturn(AuthDecision.allow());
        lenient().when(projectMemberRepository.findActiveByProjectId(any(), any()))
                .thenReturn(List.of());
        lenient().when(changeRequestRepository.update(any())).thenReturn(1);
        lenient().when(workflowLauncher.start(any())).thenReturn(INSTANCE_ID);
    }

    private CreateChangeRequestCmd cmd(String type, Map<String, Object> payload) {
        return new CreateChangeRequestCmd(type, LocalDate.of(2027, 1, 1), payload,
                "因研究计划调整需要变更", List.of(), REQUEST_ID);
    }

    @Test
    void tcPrj020PeriodChangeCreatesInReviewAndKeepsCurrentBaseline() {
        stubProjectAndAuth();
        CreateChangeRequestCmd cmd = cmd("PERIOD", Map.of(
                "new_start_date", "2026-12-01",
                "new_end_date", "2030-05-31"));

        ChangeRequestCreated result = service.create(PROJECT_ID, cmd, auth);

        assertThat(result.status()).isEqualTo(ChangeRequestEntity.STATUS_IN_REVIEW);
        assertThat(result.templateCode()).isEqualTo("CHANGE_PERIOD");
        assertThat(project.getCurrentBaselineId()).isEqualTo(BASELINE_ID);

        ArgumentCaptor<ChangeRequestEntity> changeCaptor =
                ArgumentCaptor.forClass(ChangeRequestEntity.class);
        verify(changeRequestRepository).insert(changeCaptor.capture());
        ChangeRequestEntity change = changeCaptor.getValue();
        assertThat(change.getChangeType()).isEqualTo(ChangeRequestEntity.TYPE_PERIOD);
        assertThat(change.getStatus()).isEqualTo(ChangeRequestEntity.STATUS_IN_REVIEW);
        assertThat(change.getCurrentVersionId()).isEqualTo(result.versionId());

        ArgumentCaptor<ChangeRequestVersionEntity> versionCaptor =
                ArgumentCaptor.forClass(ChangeRequestVersionEntity.class);
        verify(changeRequestVersionRepository).insert(versionCaptor.capture());
        ChangeRequestVersionEntity version = versionCaptor.getValue();
        assertThat(version.getVersionNo()).isEqualTo(1);
        assertThat(version.getOldBaselineId()).isEqualTo(BASELINE_ID);
        assertThat(version.getPayload()).contains("\"new_end_date\":\"2030-05-31\"");
        assertThat(version.getReason()).isEqualTo("因研究计划调整需要变更");

        ArgumentCaptor<ProjectApprovalWorkflowStart> startCaptor =
                ArgumentCaptor.forClass(ProjectApprovalWorkflowStart.class);
        verify(workflowLauncher).start(startCaptor.capture());
        assertThat(startCaptor.getValue().bizType()).isEqualTo("CHANGE");
        assertThat(startCaptor.getValue().templateCode()).isEqualTo("CHANGE_PERIOD");
        assertThat(startCaptor.getValue().bizId())
                .isEqualTo(UuidUtils.parse(result.changeRequestId()));
        assertThat(result.versionId()).matches("[0-9a-f]{32}");
    }

    @Test
    void tcChg101MissingRequiredPayloadFieldsReturn400WithPayloadField() {
        stubProjectAndAuth();

        assertPayloadError("PERIOD", Map.of("new_start_date", "2026-12-01"),
                "payload.newEndDate");
        assertPayloadError("PI", Map.of("new_pi_user_id", NEW_PI_ID),
                "payload.handoverNote");
        assertPayloadError("MEMBER", Map.of("changes", List.of()),
                "payload.changes");
        assertPayloadError("OBJECTIVE", Map.of("objectives", List.of()),
                "payload.objectives");
        assertPayloadError("BUDGET", Map.of(),
                "payload.budgetVersionDraftId");
        assertPayloadError("PARTNER", Map.of("partners", List.of()),
                "payload.partners");

        verify(changeRequestRepository, never()).insert(any());
        verifyNoInteractions(workflowLauncher);
    }

    private void assertPayloadError(String type, Map<String, Object> payload, String field) {
        assertThatThrownBy(() -> service.create(PROJECT_ID, cmd(type, payload), auth))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> {
                    BizException biz = (BizException) ex;
                    assertThat(biz.getErrorCode()).isEqualTo(ErrorCode.COMMON_400);
                    assertThat(biz.getMessage()).contains(field);
                });
    }

    @Test
    void tcChg102MemberOverlapWithExistingMemberReturnsProj005() {
        stubProjectAndAuth();
        ProjectMemberEntity existing = new ProjectMemberEntity();
        existing.setProjectId(PROJECT_ID);
        existing.setUserId(EXISTING_USER_ID);
        existing.setProjectRole("MEMBER");
        existing.setStatus(ProjectMemberEntity.STATUS_ACTIVE);
        when(projectMemberRepository.findByProjectId(PROJECT_ID)).thenReturn(List.of(existing));
        CreateChangeRequestCmd cmd = cmd("MEMBER", Map.of("changes", List.of(Map.of(
                "user_id", EXISTING_USER_ID,
                "project_role", "MEMBER",
                "action", "ADD"))));

        assertThatThrownBy(() -> service.create(PROJECT_ID, cmd, auth))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.PROJ_005));

        verify(changeRequestRepository, never()).insert(any());
        verifyNoInteractions(workflowLauncher);
    }

    @Test
    void tcChg106NonPiNonResearchAdminReturns403() {
        when(projectRepository.selectForUpdate(PROJECT_ID)).thenReturn(Optional.of(project));
        when(authorizationService.check(any(), any(), any()))
                .thenReturn(AuthDecision.deny(ErrorCode.COMMON_403));
        lenient().when(projectMemberRepository.findActiveByProjectId(any(), any()))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.create(PROJECT_ID, cmd("PERIOD", Map.of(
                "new_start_date", "2026-12-01", "new_end_date", "2030-05-31")), auth))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.COMMON_403));

        verify(changeRequestRepository, never()).insert(any());
        verifyNoInteractions(workflowLauncher);
    }

    @Test
    void piChangeRequiresActiveHandoverTarget() {
        stubProjectAndAuth();
        IamUserEntity leaved = new IamUserEntity();
        leaved.setId(NEW_PI_ID);
        leaved.setStatus("LEAVED");
        when(iamUserRepository.findById(NEW_PI_ID)).thenReturn(Optional.of(leaved));

        assertThatThrownBy(() -> service.create(PROJECT_ID, cmd("PI", Map.of(
                "new_pi_user_id", NEW_PI_ID,
                "handover_note", "原负责人调岗，由新负责人接续")), auth))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.PROJ_006));

        verify(changeRequestRepository, never()).insert(any());
        verifyNoInteractions(workflowLauncher);
    }
}
