package cn.edu.rpm.application.application.service;

import cn.edu.rpm.application.application.dto.FormalReviewCmd;
import cn.edu.rpm.application.application.dto.FormalReviewResult;
import cn.edu.rpm.application.domain.model.ApplicationPermissions;
import cn.edu.rpm.application.domain.port.ApplicationApprovalWorkflowPort;
import cn.edu.rpm.application.domain.port.ApplicationWorkflowLauncher;
import cn.edu.rpm.application.domain.port.WorkflowLaunch;
import cn.edu.rpm.application.infrastructure.entity.ApplicationEntity;
import cn.edu.rpm.application.infrastructure.entity.ApplicationReviewEntity;
import cn.edu.rpm.application.infrastructure.repository.ApplicationRepository;
import cn.edu.rpm.application.infrastructure.repository.ApplicationReviewRepository;
import cn.edu.rpm.audit.domain.service.AuditRecorder;
import cn.edu.rpm.common.domain.port.OutboxAppendPort;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.common.util.CanonicalJsonHasher;
import cn.edu.rpm.iam.application.dto.AuthContext;
import cn.edu.rpm.iam.application.dto.AuthDecision;
import cn.edu.rpm.iam.domain.service.AuthorizationService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link ApplicationReviewAppService} 纯单元测试（T-APP-017）。
 *
 * <p>覆盖 TC-APP-010（RETURN 无问题项 400）、TC-APP-011（RETURN 写入 2 条问题并置
 * SUPPLEMENT）、TC-APP-013（PASS 完成任务且版本内容不变）、TC-APP-014（自审 WF-003）、
 * TC-APP-101（NOT_ACCEPT 无理由 400）、TC-APP-102（NOT_ACCEPTED 终态后不可再提交）。</p>
 */
@ExtendWith(MockitoExtension.class)
class ApplicationReviewAppServiceTest {

    private static final String APP_ID = "aaaabbbbccccddddeeeeffff00001111";
    private static final String VERSION_ID = "11111111111111111111111111111111";
    private static final String TASK_ID = "22222222222222222222222222222222";
    private static final String INSTANCE_ID = "33333333333333333333333333333333";
    private static final UUID REVIEWER_ID = UUID.fromString("44444444-5555-6666-7777-888888888888");
    private static final UUID APPLICANT_ID = UUID.fromString("99999999-8888-7777-6666-555555555555");
    private static final UUID REQUEST_ID = UUID.fromString("abcdabcd-abcd-abcd-abcd-abcdabcdabcd");

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-11-18T02:00:00Z"), ZoneId.of("Asia/Shanghai"));

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private ApplicationReviewRepository applicationReviewRepository;

    @Mock
    private ApplicationWorkflowLauncher workflowLauncher;

    @Mock
    private ApplicationApprovalWorkflowPort approvalWorkflowPort;

    @Mock
    private OutboxAppendPort outboxAppendPort;

    @Mock
    private AuthorizationService authorizationService;

    @Mock
    private AuditRecorder auditRecorder;

    private ApplicationReviewAppService service;

    private AuthContext reviewerAuth;

    @BeforeEach
    void setUp() {
        service = new ApplicationReviewAppService(applicationRepository, applicationReviewRepository,
                workflowLauncher, approvalWorkflowPort, outboxAppendPort, authorizationService,
                auditRecorder, new CanonicalJsonHasher(), CLOCK);
        reviewerAuth = new AuthContext(REVIEWER_ID, null, "E2001",
                Set.of(ApplicationPermissions.FORMAL_REVIEW), Set.of(),
                Instant.parse("2026-11-18T02:00:00Z"));
    }

    private ApplicationEntity inReviewApplication() {
        ApplicationEntity application = new ApplicationEntity();
        application.setId(APP_ID);
        application.setApplicationNo("APP-2026-000001");
        application.setApplicantId(APPLICANT_ID.toString().replace("-", ""));
        application.setDeptId("dddddddddddddddddddddddddddddddd");
        application.setCurrentVersionId(VERSION_ID);
        application.setStatus(ApplicationEntity.STATUS_IN_REVIEW);
        return application;
    }

    private void stubLockedApplication(ApplicationEntity application) {
        when(applicationRepository.selectForUpdate(eq(APP_ID))).thenReturn(Optional.of(application));
        when(authorizationService.check(any(), any(), any())).thenReturn(AuthDecision.allow());
        org.mockito.Mockito.lenient().when(applicationRepository.update(any())).thenReturn(1);
    }

    @Test
    void tcApp010ReturnWithoutIssuesReturnsCommon400() {
        FormalReviewCmd cmd = new FormalReviewCmd(VERSION_ID, null, null, "RETURN",
                List.of(), "请补正", REQUEST_ID);

        assertThatThrownBy(() -> service.formalReview(APP_ID, cmd, reviewerAuth))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.COMMON_400));

        verify(applicationReviewRepository, never()).insert(any());
        verify(applicationRepository, never()).update(any());
    }

    @Test
    void tcApp011ReturnWithTwoIssuesMovesToSupplement() {
        ApplicationEntity application = inReviewApplication();
        stubLockedApplication(application);
        when(workflowLauncher.findLatest(any(), any())).thenReturn(Optional.of(
                new WorkflowLaunch(UUID.fromString("33333333-3333-3333-3333-333333333333"), "RUNNING")));
        FormalReviewCmd cmd = new FormalReviewCmd(VERSION_ID, TASK_ID, 2, "RETURN",
                List.of(new FormalReviewCmd.Issue("budget.total", "预算总额与明细合计不一致"),
                        new FormalReviewCmd.Issue("attachment.ethics", "缺少伦理审查附件")),
                "请补正后重新提交", REQUEST_ID);

        FormalReviewResult result = service.formalReview(APP_ID, cmd, reviewerAuth);

        assertThat(result.status()).isEqualTo(ApplicationEntity.STATUS_SUPPLEMENT);
        assertThat(result.issueCount()).isEqualTo(2);
        ArgumentCaptor<ApplicationReviewEntity> reviewCaptor =
                ArgumentCaptor.forClass(ApplicationReviewEntity.class);
        verify(applicationReviewRepository).insert(reviewCaptor.capture());
        ApplicationReviewEntity review = reviewCaptor.getValue();
        assertThat(review.getReviewStage()).isEqualTo(ApplicationReviewEntity.STAGE_FORMAL);
        assertThat(review.getDecision()).isEqualTo(ApplicationReviewEntity.DECISION_RETURN);
        assertThat(review.getIssues()).contains("budget.total").contains("attachment.ethics");
        verify(approvalWorkflowPort).completeReview(
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                REVIEWER_ID, false, "形式审查退回", 2, REQUEST_ID);
        assertThat(application.getStatus()).isEqualTo(ApplicationEntity.STATUS_SUPPLEMENT);
    }

    @Test
    void tcApp013PassCompletesTaskAndKeepsVersionUnchanged() {
        ApplicationEntity application = inReviewApplication();
        stubLockedApplication(application);
        when(workflowLauncher.findLatest(any(), any())).thenReturn(Optional.of(
                new WorkflowLaunch(UUID.fromString("33333333-3333-3333-3333-333333333333"), "RUNNING")));
        FormalReviewCmd cmd = new FormalReviewCmd(VERSION_ID, TASK_ID, 1, "PASS",
                List.of(), "材料齐全", REQUEST_ID);

        FormalReviewResult result = service.formalReview(APP_ID, cmd, reviewerAuth);

        assertThat(result.status()).isEqualTo(ApplicationEntity.STATUS_IN_REVIEW);
        assertThat(result.decision()).isEqualTo("PASS");
        assertThat(application.getCurrentVersionId()).isEqualTo(VERSION_ID);
        verify(approvalWorkflowPort).completeReview(any(), any(), eq(REVIEWER_ID), eq(true),
                eq("形式审查通过"), eq(1), eq(REQUEST_ID));
        verifyNoInteractions(outboxAppendPort);
    }

    @Test
    void tcApp014SelfReviewReturnsWf003WithoutReviewRecord() {
        ApplicationEntity application = inReviewApplication();
        when(applicationRepository.selectForUpdate(APP_ID)).thenReturn(Optional.of(application));
        when(authorizationService.check(any(), any(), any())).thenReturn(AuthDecision.allow());
        AuthContext applicantAuth = new AuthContext(APPLICANT_ID, null, "E1001",
                Set.of(ApplicationPermissions.FORMAL_REVIEW), Set.of(),
                Instant.parse("2026-11-18T02:00:00Z"));
        FormalReviewCmd cmd = new FormalReviewCmd(VERSION_ID, null, null, "PASS",
                List.of(), "同意", REQUEST_ID);

        assertThatThrownBy(() -> service.formalReview(APP_ID, cmd, applicantAuth))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.WF_003));

        verify(applicationReviewRepository, never()).insert(any());
    }

    @Test
    void tcApp101NotAcceptWithoutReasonReturnsCommon400() {
        FormalReviewCmd cmd = new FormalReviewCmd(VERSION_ID, null, null, "NOT_ACCEPT",
                List.of(), "不足", REQUEST_ID);

        assertThatThrownBy(() -> service.formalReview(APP_ID, cmd, reviewerAuth))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.COMMON_400));

        verify(applicationReviewRepository, never()).insert(any());
    }

    @Test
    void tcApp102NotAcceptedIsTerminalAndCannotBeSubmittedAgain() {
        ApplicationEntity application = inReviewApplication();
        stubLockedApplication(application);
        when(workflowLauncher.findLatest(any(), any())).thenReturn(Optional.of(
                new WorkflowLaunch(UUID.fromString("33333333-3333-3333-3333-333333333333"), "RUNNING")));
        FormalReviewCmd cmd = new FormalReviewCmd(VERSION_ID, TASK_ID, 1, "NOT_ACCEPT",
                List.of(), "申报方向与本批次指南不符，不予受理", REQUEST_ID);

        FormalReviewResult result = service.formalReview(APP_ID, cmd, reviewerAuth);

        assertThat(result.status()).isEqualTo(ApplicationEntity.STATUS_NOT_ACCEPTED);
        assertThat(application.getStatus()).isEqualTo(ApplicationEntity.STATUS_NOT_ACCEPTED);
        assertThatThrownBy(() -> application.markInReview("new-version"))
                .isInstanceOf(IllegalStateException.class);
        verify(approvalWorkflowPort).cancel(
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                REVIEWER_ID, "形式审查不予受理：申报方向与本批次指南不符，不予受理", REQUEST_ID);
    }
}
