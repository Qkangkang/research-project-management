package cn.edu.rpm.application.application.service;

import cn.edu.rpm.application.application.dto.DecisionCmd;
import cn.edu.rpm.application.application.dto.DecisionResult;
import cn.edu.rpm.application.domain.model.ProjectCreationCommand;
import cn.edu.rpm.application.domain.model.ProjectCreationResult;
import cn.edu.rpm.application.domain.port.ProjectCreationPort;
import cn.edu.rpm.application.infrastructure.entity.ApplicationEntity;
import cn.edu.rpm.application.infrastructure.entity.ApplicationReviewEntity;
import cn.edu.rpm.application.infrastructure.entity.ApplicationVersionEntity;
import cn.edu.rpm.application.infrastructure.repository.ApplicationRepository;
import cn.edu.rpm.application.infrastructure.repository.ApplicationReviewRepository;
import cn.edu.rpm.application.infrastructure.repository.ApplicationVersionRepository;
import cn.edu.rpm.audit.domain.service.AuditRecorder;
import cn.edu.rpm.common.domain.port.OutboxAppendPort;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.iam.application.dto.AuthContext;
import cn.edu.rpm.iam.application.dto.AuthDecision;
import cn.edu.rpm.iam.domain.service.AuthorizationService;
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
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link ApplicationDecisionAppService} 纯单元测试（T-APP-019）。
 *
 * <p>覆盖 TC-APP-028（批准生成项目/基线）、TC-APP-029（重复批准返回首次 project_id）、
 * TC-APP-030（拒绝不生成项目）、TC-APP-031（版本不一致 WF-005）。</p>
 */
@ExtendWith(MockitoExtension.class)
class ApplicationDecisionAppServiceTest {

    private static final String APP_ID = "aaaabbbbccccddddeeeeffff00001111";
    private static final String VERSION_ID = "11111111111111111111111111111111";
    private static final String PROJECT_ID = "22222222222222222222222222222222";
    private static final String BASELINE_ID = "33333333333333333333333333333333";
    private static final UUID DECIDER_ID = UUID.fromString("44444444-5555-6666-7777-888888888888");
    private static final UUID APPLICANT_ID = UUID.fromString("99999999-8888-7777-6666-555555555555");
    private static final UUID REQUEST_ID = UUID.fromString("abcdabcd-abcd-abcd-abcd-abcdabcdabcd");
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-11-18T02:00:00Z"), ZoneId.of("Asia/Shanghai"));

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private ApplicationVersionRepository applicationVersionRepository;

    @Mock
    private ApplicationReviewRepository applicationReviewRepository;

    @Mock
    private ProjectCreationPort projectCreationPort;

    @Mock
    private OutboxAppendPort outboxAppendPort;

    @Mock
    private AuthorizationService authorizationService;

    @Mock
    private AuditRecorder auditRecorder;

    private ApplicationDecisionAppService service;

    private AuthContext auth;

    @BeforeEach
    void setUp() {
        service = new ApplicationDecisionAppService(applicationRepository, applicationVersionRepository,
                applicationReviewRepository, projectCreationPort, outboxAppendPort,
                authorizationService, auditRecorder, new ObjectMapper(), CLOCK);
        auth = new AuthContext(DECIDER_ID, null, "E2001", Set.of("application:decide"),
                Set.of(), Instant.parse("2026-11-18T02:00:00Z"));
    }

    private ApplicationEntity inReviewApplication() {
        ApplicationEntity application = new ApplicationEntity();
        application.setId(APP_ID);
        application.setApplicationNo("APP-2026-000001");
        application.setTitle("智能感知科研平台");
        application.setProjectType("YEAR_PLAN");
        application.setApplicantId(APPLICANT_ID.toString().replace("-", ""));
        application.setDeptId("dddddddddddddddddddddddddddddddd");
        application.setCurrentVersionId(VERSION_ID);
        application.setStatus(ApplicationEntity.STATUS_IN_REVIEW);
        return application;
    }

    private ApplicationVersionEntity version() {
        ApplicationVersionEntity version = new ApplicationVersionEntity();
        version.setId(VERSION_ID);
        version.setApplicationId(APP_ID);
        version.setVersionNo(1);
        version.setFormData("""
                {"title":"智能感知科研平台","milestones":[
                  {"code":"M1","title":"中期检查","plan_date":"2027-12-31",
                   "owner_id":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","completion_criteria":"提交中期报告"}]}
                """);
        version.setStatus(ApplicationVersionEntity.STATUS_SUBMITTED);
        return version;
    }

    private DecisionCmd approveCmd() {
        return new DecisionCmd(VERSION_ID, "APPROVE", "同意立项",
                LocalDate.of(2026, 12, 1), LocalDate.of(2028, 11, 30), REQUEST_ID);
    }

    private void stubApprove(ApplicationEntity application, ApplicationVersionEntity version) {
        when(applicationRepository.selectForUpdate(APP_ID)).thenReturn(Optional.of(application));
        when(authorizationService.check(any(), any(), any())).thenReturn(AuthDecision.allow());
        when(applicationVersionRepository.findById(VERSION_ID)).thenReturn(Optional.of(version));
        org.mockito.Mockito.lenient().when(applicationVersionRepository.update(any())).thenReturn(1);
        org.mockito.Mockito.lenient().when(applicationRepository.update(any())).thenReturn(1);
        org.mockito.Mockito.lenient().when(projectCreationPort.createFromVersion(any())).thenReturn(
                new ProjectCreationResult(PROJECT_ID, "KY-2026-000001", BASELINE_ID));
    }

    @Test
    void tcApp028ApproveCreatesProjectAndMarksApplicationApproved() {
        ApplicationEntity application = inReviewApplication();
        ApplicationVersionEntity version = version();
        stubApprove(application, version);
        ArgumentCaptor<ProjectCreationCommand> commandCaptor =
                ArgumentCaptor.forClass(ProjectCreationCommand.class);

        DecisionResult result = service.decide(APP_ID, approveCmd(), auth);

        assertThat(result.projectId()).isEqualTo(PROJECT_ID);
        assertThat(result.projectNo()).isEqualTo("KY-2026-000001");
        assertThat(result.baselineId()).isEqualTo(BASELINE_ID);
        assertThat(result.status()).isEqualTo(ApplicationEntity.STATUS_APPROVED);
        assertThat(application.getStatus()).isEqualTo(ApplicationEntity.STATUS_APPROVED);
        assertThat(version.getStatus()).isEqualTo(ApplicationVersionEntity.STATUS_APPROVED);

        verify(projectCreationPort).createFromVersion(commandCaptor.capture());
        ProjectCreationCommand command = commandCaptor.getValue();
        assertThat(command.sourceApplicationVersionId()).isEqualTo(VERSION_ID);
        assertThat(command.approvedStartDate()).isEqualTo(LocalDate.of(2026, 12, 1));
        assertThat(command.milestones()).hasSize(1);
        assertThat(command.milestones().get(0).code()).isEqualTo("M1");

        ArgumentCaptor<ApplicationReviewEntity> reviewCaptor =
                ArgumentCaptor.forClass(ApplicationReviewEntity.class);
        verify(applicationReviewRepository).insert(reviewCaptor.capture());
        assertThat(reviewCaptor.getValue().getReviewStage())
                .isEqualTo(ApplicationReviewEntity.STAGE_DECISION);
        assertThat(reviewCaptor.getValue().getDecision())
                .isEqualTo(ApplicationReviewEntity.DECISION_APPROVE);
    }

    @Test
    void tcApp029RepeatedApproveReturnsFirstProjectWithoutDuplicatingWrites() {
        ApplicationEntity application = inReviewApplication();
        application.setStatus(ApplicationEntity.STATUS_APPROVED);
        ApplicationVersionEntity version = version();
        version.setStatus(ApplicationVersionEntity.STATUS_APPROVED);
        stubApprove(application, version);

        DecisionResult result = service.decide(APP_ID, approveCmd(), auth);

        assertThat(result.projectId()).isEqualTo(PROJECT_ID);
        assertThat(result.baselineId()).isEqualTo(BASELINE_ID);
        verify(projectCreationPort).createFromVersion(any());
        verify(applicationReviewRepository, never()).insert(any());
        verify(applicationRepository, never()).update(any());
        verifyNoInteractions(outboxAppendPort, auditRecorder);
    }

    @Test
    void tcApp030RejectMarksApplicationRejectedWithoutCreatingProject() {
        ApplicationEntity application = inReviewApplication();
        ApplicationVersionEntity version = version();
        when(applicationRepository.selectForUpdate(APP_ID)).thenReturn(Optional.of(application));
        when(authorizationService.check(any(), any(), any())).thenReturn(AuthDecision.allow());
        when(applicationVersionRepository.findById(VERSION_ID)).thenReturn(Optional.of(version));
        when(applicationVersionRepository.update(any())).thenReturn(1);
        when(applicationRepository.update(any())).thenReturn(1);
        DecisionCmd cmd = new DecisionCmd(VERSION_ID, "REJECT", "不符合本年度指南",
                null, null, REQUEST_ID);

        DecisionResult result = service.decide(APP_ID, cmd, auth);

        assertThat(result.status()).isEqualTo(ApplicationEntity.STATUS_REJECTED);
        assertThat(result.projectId()).isNull();
        assertThat(application.getStatus()).isEqualTo(ApplicationEntity.STATUS_REJECTED);
        assertThat(version.getStatus()).isEqualTo(ApplicationVersionEntity.STATUS_REJECTED);
        verifyNoInteractions(projectCreationPort);
    }

    @Test
    void tcApp031VersionMismatchReturnsWf005WithoutProject() {
        ApplicationEntity application = inReviewApplication();
        when(applicationRepository.selectForUpdate(APP_ID)).thenReturn(Optional.of(application));
        when(authorizationService.check(any(), any(), any())).thenReturn(AuthDecision.allow());
        DecisionCmd cmd = new DecisionCmd("ffffffffffffffffffffffffffffffff", "APPROVE", "同意",
                LocalDate.of(2026, 12, 1), LocalDate.of(2028, 11, 30), REQUEST_ID);

        assertThatThrownBy(() -> service.decide(APP_ID, cmd, auth))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.WF_005));

        verifyNoInteractions(projectCreationPort, applicationReviewRepository, outboxAppendPort);
        verify(applicationRepository, never()).update(any());
    }
}
