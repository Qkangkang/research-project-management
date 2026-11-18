package cn.edu.rpm.application.api;

import cn.edu.rpm.application.api.dto.ApplicationActionRequests;
import cn.edu.rpm.application.api.dto.CreateApplicationRequest;
import cn.edu.rpm.application.application.dto.ApplicationWithdrawal;
import cn.edu.rpm.application.application.dto.ExternalRegistrationResult;
import cn.edu.rpm.application.application.dto.FormalReviewResult;
import cn.edu.rpm.application.application.dto.WithdrawApplicationCmd;
import cn.edu.rpm.application.api.dto.SubmitApplicationRequest;
import cn.edu.rpm.application.api.dto.UpdateDraftRequest;
import cn.edu.rpm.application.application.dto.ApplicationCreated;
import cn.edu.rpm.application.application.dto.ApplicationDraftSaved;
import cn.edu.rpm.application.application.dto.ApplicationSubmission;
import cn.edu.rpm.application.application.dto.CreateApplicationCmd;
import cn.edu.rpm.application.application.dto.SaveDraftCmd;
import cn.edu.rpm.application.application.dto.SubmitApplicationCmd;
import cn.edu.rpm.application.application.service.ApplicationCreateAppService;
import cn.edu.rpm.application.application.service.ApplicationDecisionAppService;
import cn.edu.rpm.application.application.service.ApplicationDraftAppService;
import cn.edu.rpm.application.application.service.ApplicationQueryAppService;
import cn.edu.rpm.application.application.service.ApplicationReviewAppService;
import cn.edu.rpm.application.application.service.ApplicationSubmitAppService;
import cn.edu.rpm.application.application.service.ApplicationWithdrawAppService;
import cn.edu.rpm.application.application.service.ExternalProjectAppService;
import cn.edu.rpm.application.domain.model.ApplicationPermissions;
import cn.edu.rpm.common.api.Result;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.iam.application.context.AuthContextHolder;
import cn.edu.rpm.iam.application.dto.AuthContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ApplicationController} 纯单元测试（T-APP-015）。
 *
 * <p>验证三个端点的请求→命令映射与响应载体，以及未登录时
 * {@code RPM-COMMON-401}；业务规则由应用服务测试覆盖，本类不重复断言。</p>
 */
@ExtendWith(MockitoExtension.class)
class ApplicationControllerTest {

    private static final String APP_ID = "aaaabbbbccccddddeeeeffff00001111";
    private static final UUID APPLICANT_ID = UUID.fromString("11112222-3333-4444-5555-666677778888");
    private static final String FILE_ID = "abcdabcd-abcd-abcd-abcd-abcdabcdabcd";

    @Mock
    private ApplicationCreateAppService createAppService;

    @Mock
    private ApplicationDraftAppService draftAppService;

    @Mock
    private ApplicationQueryAppService queryAppService;

    @Mock
    private ApplicationSubmitAppService submitAppService;

    @Mock
    private ApplicationReviewAppService reviewAppService;

    @Mock
    private ApplicationWithdrawAppService withdrawAppService;

    @Mock
    private ApplicationDecisionAppService decisionAppService;

    @Mock
    private ExternalProjectAppService externalProjectAppService;

    private ApplicationController controller;

    private AuthContext auth;

    @BeforeEach
    void setUp() {
        controller = new ApplicationController(createAppService, queryAppService, draftAppService,
                submitAppService, reviewAppService, withdrawAppService, decisionAppService,
                externalProjectAppService);
        auth = new AuthContext(APPLICANT_ID, null, "E1001",
                Set.of(ApplicationPermissions.CREATE, ApplicationPermissions.EDIT,
                        ApplicationPermissions.SUBMIT),
                Set.of(), Instant.parse("2026-11-18T02:00:00Z"));
        AuthContextHolder.set(auth);
    }

    @AfterEach
    void tearDown() {
        AuthContextHolder.clear();
    }

    @Test
    void createMapsRequestAndReturnsCreatedFields() {
        when(createAppService.create(any(), eq(auth)))
                .thenReturn(new ApplicationCreated(APP_ID, "APP-2026-000123", "DRAFT", 0));

        Result<ApplicationCreated> result = controller.create(new CreateApplicationRequest(
                "aaaabbbbccccddddeeeeffff00002222", "YEAR_PLAN", "智能感知方法研究",
                "dddddddddddddddddddddddddddddddd"));

        ArgumentCaptor<CreateApplicationCmd> cmdCaptor = ArgumentCaptor.forClass(CreateApplicationCmd.class);
        verify(createAppService).create(cmdCaptor.capture(), eq(auth));
        assertThat(cmdCaptor.getValue().batchId()).isEqualTo("aaaabbbbccccddddeeeeffff00002222");
        assertThat(cmdCaptor.getValue().projectType()).isEqualTo("YEAR_PLAN");
        assertThat(cmdCaptor.getValue().title()).isEqualTo("智能感知方法研究");
        assertThat(cmdCaptor.getValue().deptId()).isEqualTo("dddddddddddddddddddddddddddddddd");

        assertThat(result.getCode()).isEqualTo(Result.CODE_OK);
        assertThat(result.getData().applicationId()).isEqualTo(APP_ID);
        assertThat(result.getData().applicationNo()).isEqualTo("APP-2026-000123");
        assertThat(result.getData().status()).isEqualTo("DRAFT");
        assertThat(result.getData().version()).isZero();
    }

    @Test
    void saveDraftPassesExpectedVersionAndFormData() {
        when(draftAppService.save(eq(APP_ID), any(), eq(auth)))
                .thenReturn(new ApplicationDraftSaved(APP_ID, "DRAFT", 5, "ver-1", 1));

        Result<ApplicationDraftSaved> result = controller.saveDraft(APP_ID,
                new UpdateDraftRequest(4, "修订标题", Map.of("title", "修订标题")));

        ArgumentCaptor<SaveDraftCmd> cmdCaptor = ArgumentCaptor.forClass(SaveDraftCmd.class);
        verify(draftAppService).save(eq(APP_ID), cmdCaptor.capture(), eq(auth));
        assertThat(cmdCaptor.getValue().expectedVersion()).isEqualTo(4);
        assertThat(cmdCaptor.getValue().title()).isEqualTo("修订标题");
        assertThat(cmdCaptor.getValue().formData()).containsEntry("title", "修订标题");
        assertThat(result.getData().version()).isEqualTo(5);
        assertThat(result.getData().draftVersionNo()).isEqualTo(1);
    }

    @Test
    void submitPassesIdempotencyKeyAndMapsNestedItems() {
        when(submitAppService.submit(eq(APP_ID), any(), eq("submit-key-1"), eq(auth)))
                .thenReturn(new ApplicationSubmission(APP_ID, "ver-1", 1, "IN_REVIEW",
                        "fedcfedc-fedc-fedc-fedc-fedcfedcfedc"));

        SubmitApplicationRequest request = new SubmitApplicationRequest(4, "请审阅",
                Map.of("title", "智能感知"),
                List.of(new SubmitApplicationRequest.Attachment(
                        "ETHICS_APPROVAL", FILE_ID)),
                List.of(new SubmitApplicationRequest.Collaborator(
                        "99998888-7777-6666-5555-444433332222", "CO_AUTHOR", "参与算法设计")));

        Result<ApplicationSubmission> result = controller.submit(APP_ID, "submit-key-1", request);

        ArgumentCaptor<SubmitApplicationCmd> cmdCaptor = ArgumentCaptor.forClass(SubmitApplicationCmd.class);
        verify(submitAppService).submit(eq(APP_ID), cmdCaptor.capture(), eq("submit-key-1"), eq(auth));
        SubmitApplicationCmd cmd = cmdCaptor.getValue();
        assertThat(cmd.expectedVersion()).isEqualTo(4);
        assertThat(cmd.comment()).isEqualTo("请审阅");
        assertThat(cmd.formData()).containsEntry("title", "智能感知");
        assertThat(cmd.attachments()).hasSize(1);
        assertThat(cmd.attachments().get(0).purpose()).isEqualTo("ETHICS_APPROVAL");
        assertThat(cmd.attachments().get(0).fileId()).isEqualTo(FILE_ID);
        assertThat(cmd.collaborators()).hasSize(1);
        assertThat(cmd.collaborators().get(0).role()).isEqualTo("CO_AUTHOR");

        assertThat(result.getData().versionId()).isEqualTo("ver-1");
        assertThat(result.getData().workflowInstanceId())
                .isEqualTo("fedcfedc-fedc-fedc-fedc-fedcfedcfedc");
    }

    @Test
    void decisionMapsRequestAndReturnsProjectIdentifiers() {
        when(decisionAppService.decide(eq(APP_ID), any(), eq(auth)))
                .thenReturn(new cn.edu.rpm.application.application.dto.DecisionResult(
                        APP_ID, "p-1", "KY-2026-000001", "b-1", "APPROVED"));
        ApplicationActionRequests.DecisionRequest request =
                new ApplicationActionRequests.DecisionRequest("ver-1", "APPROVE", "同意立项",
                        java.time.LocalDate.of(2026, 12, 1),
                        java.time.LocalDate.of(2028, 11, 30));

        Result<cn.edu.rpm.application.application.dto.DecisionResult> result =
                controller.decision(APP_ID, "abcdabcd-abcd-abcd-abcd-abcdabcdabcd", request);

        ArgumentCaptor<cn.edu.rpm.application.application.dto.DecisionCmd> captor =
                ArgumentCaptor.forClass(cn.edu.rpm.application.application.dto.DecisionCmd.class);
        verify(decisionAppService).decide(eq(APP_ID), captor.capture(), eq(auth));
        assertThat(captor.getValue().versionId()).isEqualTo("ver-1");
        assertThat(captor.getValue().decision()).isEqualTo("APPROVE");
        assertThat(captor.getValue().requestId()).isEqualTo(UUID.fromString(
                "abcdabcd-abcd-abcd-abcd-abcdabcdabcd"));
        assertThat(result.getData().projectId()).isEqualTo("p-1");
        assertThat(result.getData().baselineId()).isEqualTo("b-1");
    }

    @Test
    void withdrawMapsReasonAndIdempotencyKey() {
        when(withdrawAppService.withdraw(eq(APP_ID), any(), eq(auth)))
                .thenReturn(new ApplicationWithdrawal(APP_ID, "DRAFT", "CANCELLED"));

        Result<ApplicationWithdrawal> result = controller.withdraw(APP_ID,
                "abcdabcd-abcd-abcd-abcd-abcdabcdabcd",
                new ApplicationActionRequests.WithdrawRequest("材料需补充"));

        ArgumentCaptor<WithdrawApplicationCmd> captor =
                ArgumentCaptor.forClass(WithdrawApplicationCmd.class);
        verify(withdrawAppService).withdraw(eq(APP_ID), captor.capture(), eq(auth));
        assertThat(captor.getValue().reason()).isEqualTo("材料需补充");
        assertThat(captor.getValue().requestId()).isEqualTo(UUID.fromString(
                "abcdabcd-abcd-abcd-abcd-abcdabcdabcd"));
        assertThat(result.getData().status()).isEqualTo("DRAFT");
    }

    @Test
    void formalReviewMapsDecisionAndIssues() {
        when(reviewAppService.formalReview(eq(APP_ID), any(), eq(auth)))
                .thenReturn(new FormalReviewResult(APP_ID, "ver-1", "RETURN", "SUPPLEMENT", 2));

        Result<FormalReviewResult> result = controller.formalReview(APP_ID,
                "abcdabcd-abcd-abcd-abcd-abcdabcdabcd",
                new ApplicationActionRequests.FormalReviewRequest("ver-1", "task-1", 3,
                        "RETURN", List.of(
                        new ApplicationActionRequests.FormalReviewRequest.Issue("title", "不能为空"),
                        new ApplicationActionRequests.FormalReviewRequest.Issue(
                                "attachments", "缺少伦理批件")), "请补正"));

        ArgumentCaptor<cn.edu.rpm.application.application.dto.FormalReviewCmd> captor =
                ArgumentCaptor.forClass(
                        cn.edu.rpm.application.application.dto.FormalReviewCmd.class);
        verify(reviewAppService).formalReview(eq(APP_ID), captor.capture(), eq(auth));
        assertThat(captor.getValue().versionId()).isEqualTo("ver-1");
        assertThat(captor.getValue().taskId()).isEqualTo("task-1");
        assertThat(captor.getValue().expectedTaskVersion()).isEqualTo(3);
        assertThat(captor.getValue().issues()).hasSize(2);
        assertThat(captor.getValue().issues().get(0).field()).isEqualTo("title");
        assertThat(result.getData().issueCount()).isEqualTo(2);
        assertThat(result.getData().status()).isEqualTo("SUPPLEMENT");
    }

    @Test
    void externalRegistrationMapsIdentifiersAndEvidence() {
        when(externalProjectAppService.register(any(), eq(auth)))
                .thenReturn(new ExternalRegistrationResult(APP_ID, "APP-2026-000001",
                        "ver-1", "p-1", "KY-2026-000001", "b-1"));

        Result<ExternalRegistrationResult> result = controller.registerExternal(
                "abcdabcd-abcd-abcd-abcd-abcdabcdabcd",
                new ApplicationActionRequests.ExternalRegistrationRequest(
                        "aaaabbbbccccddddeeeeffff00002222", "EXTERNAL", "国家自然科学基金项目",
                        "dddddddddddddddddddddddddddddddd",
                        "99998888-7777-6666-5555-444433332222", "国家自然科学基金委",
                        "NSFC-12345", "HT-2026-001", "CW-2026-001",
                        new java.math.BigDecimal("500000.00"),
                        java.time.LocalDate.of(2026, 1, 1),
                        java.time.LocalDate.of(2028, 12, 31), "E2001", List.of(FILE_ID)));

        ArgumentCaptor<cn.edu.rpm.application.application.dto.RegisterExternalProjectCmd> captor =
                ArgumentCaptor.forClass(
                        cn.edu.rpm.application.application.dto.RegisterExternalProjectCmd.class);
        verify(externalProjectAppService).register(captor.capture(), eq(auth));
        assertThat(captor.getValue().approvalNo()).isEqualTo("NSFC-12345");
        assertThat(captor.getValue().contractNo()).isEqualTo("HT-2026-001");
        assertThat(captor.getValue().financialProjectNo()).isEqualTo("CW-2026-001");
        assertThat(captor.getValue().evidenceFileIds()).containsExactly(FILE_ID);
        assertThat(captor.getValue().requestId()).isEqualTo(UUID.fromString(
                "abcdabcd-abcd-abcd-abcd-abcdabcdabcd"));
        assertThat(result.getData().projectNo()).isEqualTo("KY-2026-000001");
        assertThat(result.getData().baselineId()).isEqualTo("b-1");
    }

    @Test
    void unauthenticatedRequestReturns401() {
        AuthContextHolder.clear();

        assertThatThrownBy(() -> controller.create(new CreateApplicationRequest(
                "aaaabbbbccccddddeeeeffff00002222", "YEAR_PLAN", "标题",
                "dddddddddddddddddddddddddddddddd")))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.COMMON_401));
    }
}
