package cn.edu.rpm.execution.api;

import cn.edu.rpm.common.api.PageVO;
import cn.edu.rpm.common.api.Result;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.execution.api.dto.ExecutionRequests;
import cn.edu.rpm.execution.application.dto.DeliverableCreated;
import cn.edu.rpm.execution.application.dto.DeliverableView;
import cn.edu.rpm.execution.application.dto.ProgressReportCreated;
import cn.edu.rpm.execution.application.dto.ProgressReportReviewed;
import cn.edu.rpm.execution.application.dto.ProgressReportSubmitted;
import cn.edu.rpm.execution.application.dto.ProgressReportView;
import cn.edu.rpm.execution.application.dto.ProjectTaskCreated;
import cn.edu.rpm.execution.application.dto.ProjectTaskStateChanged;
import cn.edu.rpm.execution.application.dto.ProjectTaskView;
import cn.edu.rpm.execution.application.dto.ReviewProgressReportCmd;
import cn.edu.rpm.execution.application.dto.SubmitProgressReportCmd;
import cn.edu.rpm.execution.application.service.DeliverableService;
import cn.edu.rpm.execution.application.service.ProgressReportAppService;
import cn.edu.rpm.execution.application.service.ProgressReportReviewService;
import cn.edu.rpm.execution.application.service.ProgressReportSubmitAppService;
import cn.edu.rpm.execution.application.service.ProjectTaskAppService;
import cn.edu.rpm.execution.domain.model.ExecutionPermissions;
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
import java.time.LocalDate;
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
 * {@link ExecutionController} 纯单元测试（T-EXEC-010）。
 *
 * <p>验证请求到命令映射、统一响应载体与未登录 401；业务规则由应用服务测试覆盖。</p>
 */
@ExtendWith(MockitoExtension.class)
class ExecutionControllerTest {

    private static final String PROJECT_ID = "8d7c6b5a4e3f4a2b9c1d0e2f3a4b5c6d";
    private static final String REPORT_ID = "11111111111111111111111111111111";
    private static final String TASK_ID = "22222222222222222222222222222222";
    private static final UUID ACTOR_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final String KEY = "abcdabcd-abcd-abcd-abcd-abcdabcdabcd";

    @Mock
    private ProgressReportAppService progressReportAppService;

    @Mock
    private ProgressReportSubmitAppService progressReportSubmitAppService;

    @Mock
    private ProgressReportReviewService progressReportReviewService;

    @Mock
    private DeliverableService deliverableService;

    @Mock
    private ProjectTaskAppService projectTaskAppService;

    private ExecutionController controller;

    private AuthContext auth;

    @BeforeEach
    void setUp() {
        controller = new ExecutionController(progressReportAppService,
                progressReportSubmitAppService, progressReportReviewService,
                deliverableService, projectTaskAppService);
        auth = new AuthContext(ACTOR_ID, null, "E1001",
                Set.of(ExecutionPermissions.VIEW, ExecutionPermissions.REPORT_EDIT,
                        ExecutionPermissions.REPORT_SUBMIT, ExecutionPermissions.REPORT_REVIEW,
                        ExecutionPermissions.DELIVERABLE_UPLOAD, ExecutionPermissions.TASK_ASSIGN),
                Set.of(), Instant.parse("2026-11-18T02:00:00Z"));
        AuthContextHolder.set(auth);
    }

    @AfterEach
    void tearDown() {
        AuthContextHolder.clear();
    }

    @Test
    void createReportMapsPeriodFields() {
        when(progressReportAppService.create(eq(PROJECT_ID), any(), eq(auth)))
                .thenReturn(new ProgressReportCreated(REPORT_ID, "MONTH", "2026-11", "DRAFT",
                        LocalDate.of(2026, 12, 5)));

        Result<ProgressReportCreated> result = controller.createReport(PROJECT_ID,
                new ExecutionRequests.CreateReportRequest("MONTH", "2026-11"));

        verify(progressReportAppService).create(eq(PROJECT_ID),
                eq(new cn.edu.rpm.execution.application.dto.CreateProgressReportCmd(
                        "MONTH", "2026-11")), eq(auth));
        assertThat(result.getData().dueDate()).isEqualTo(LocalDate.of(2026, 12, 5));
    }

    @Test
    void listReportsReturnsItems() {
        when(progressReportAppService.list(PROJECT_ID, auth))
                .thenReturn(List.<ProgressReportView>of());

        Result<List<ProgressReportView>> result = controller.listReports(PROJECT_ID);

        assertThat(result.getData()).isEmpty();
    }

    @Test
    void submitReportPassesIdempotencyKeyAndReturnsVersionNo() {
        when(progressReportSubmitAppService.submit(eq(REPORT_ID), any(), eq(auth)))
                .thenReturn(new ProgressReportSubmitted(REPORT_ID, "ver-1", 2, "SUBMITTED", false));

        Result<ProgressReportSubmitted> result = controller.submitReport(REPORT_ID, KEY,
                new ExecutionRequests.SubmitReportRequest(1, Map.of("work_done", "完成实验"),
                        List.of("file-1")));

        ArgumentCaptor<SubmitProgressReportCmd> captor =
                ArgumentCaptor.forClass(SubmitProgressReportCmd.class);
        verify(progressReportSubmitAppService).submit(eq(REPORT_ID), captor.capture(), eq(auth));
        assertThat(captor.getValue().expectedVersion()).isEqualTo(1);
        assertThat(captor.getValue().requestId()).isEqualTo(UUID.fromString(KEY));
        assertThat(result.getData().versionNo()).isEqualTo(2);
        assertThat(result.getData().lateFlag()).isFalse();
    }

    @Test
    void reviewReturnRoutesToReturnReport() {
        when(progressReportReviewService.returnReport(eq(REPORT_ID), any(), eq(auth)))
                .thenReturn(new ProgressReportReviewed(REPORT_ID, "ver-1", "RETURNED", "RETURN"));

        Result<ProgressReportReviewed> result = controller.reviewReport(REPORT_ID, KEY,
                new ExecutionRequests.ReviewReportRequest("RETURN", "补充数据"));

        ArgumentCaptor<ReviewProgressReportCmd> captor =
                ArgumentCaptor.forClass(ReviewProgressReportCmd.class);
        verify(progressReportReviewService).returnReport(eq(REPORT_ID), captor.capture(), eq(auth));
        assertThat(captor.getValue().comment()).isEqualTo("补充数据");
        assertThat(result.getData().status()).isEqualTo("RETURNED");
    }

    @Test
    void reviewApproveRoutesToApprove() {
        when(progressReportReviewService.approve(eq(REPORT_ID), any(), eq(auth)))
                .thenReturn(new ProgressReportReviewed(REPORT_ID, "ver-1", "APPROVED", "PASS"));

        Result<ProgressReportReviewed> result = controller.reviewReport(REPORT_ID, null,
                new ExecutionRequests.ReviewReportRequest("APPROVE", null));

        verify(progressReportReviewService).approve(eq(REPORT_ID), any(), eq(auth));
        assertThat(result.getData().decision()).isEqualTo("PASS");
    }

    @Test
    void reviewRejectsUnknownAction() {
        assertThatThrownBy(() -> controller.reviewReport(REPORT_ID, null,
                new ExecutionRequests.ReviewReportRequest("UNKNOWN", null)))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.COMMON_400));
    }

    @Test
    void uploadDeliverableReturnsFileId() {
        when(deliverableService.upload(eq(PROJECT_ID), any(), eq(auth)))
                .thenReturn(new DeliverableCreated("d-1", "file-1", "a".repeat(64), "ACTIVE", 0));

        Result<DeliverableCreated> result = controller.uploadDeliverable(PROJECT_ID,
                new ExecutionRequests.UploadDeliverableRequest("file-1", "报告.pdf",
                        null, null, "a".repeat(64)));

        assertThat(result.getData().fileId()).isEqualTo("file-1");
        assertThat(result.getData().deliverableId()).isEqualTo("d-1");
    }

    @Test
    void listDeliverablesReturnsPage() {
        when(deliverableService.list(PROJECT_ID, null, 1, 20, auth))
                .thenReturn(PageVO.of(List.<DeliverableView>of(), 1, 20, 0));

        Result<PageVO<DeliverableView>> result = controller.listDeliverables(PROJECT_ID,
                null, 1, 20);

        assertThat(result.getData().getItems()).isEmpty();
        assertThat(result.getData().getTotal()).isZero();
    }

    @Test
    void createTaskMapsFields() {
        when(projectTaskAppService.create(eq(PROJECT_ID), any(), eq(auth)))
                .thenReturn(new ProjectTaskCreated(TASK_ID, "TODO", "user-1", 0));

        Result<ProjectTaskCreated> result = controller.createTask(PROJECT_ID,
                new ExecutionRequests.CreateTaskRequest("写报告", "汇总进展", "user-1",
                        LocalDate.of(2026, 12, 10), null));

        assertThat(result.getData().taskId()).isEqualTo(TASK_ID);
        assertThat(result.getData().status()).isEqualTo("TODO");
    }

    @Test
    void startAndCompleteTaskReturnNewStatus() {
        when(projectTaskAppService.start(TASK_ID, auth))
                .thenReturn(new ProjectTaskStateChanged(TASK_ID, "IN_PROGRESS", 0));
        when(projectTaskAppService.complete(eq(TASK_ID), any(), eq(auth)))
                .thenReturn(new ProjectTaskStateChanged(TASK_ID, "DONE", 1));

        assertThat(controller.startTask(TASK_ID).getData().status())
                .isEqualTo("IN_PROGRESS");
        assertThat(controller.completeTask(TASK_ID,
                new ExecutionRequests.CompleteTaskRequest("d-1")).getData().status())
                .isEqualTo("DONE");
    }

    @Test
    void cancelTaskMapsReason() {
        when(projectTaskAppService.cancel(TASK_ID, "方向调整", auth))
                .thenReturn(new ProjectTaskStateChanged(TASK_ID, "CANCELLED", 1));

        Result<ProjectTaskStateChanged> result = controller.cancelTask(TASK_ID,
                new ExecutionRequests.CancelTaskRequest("方向调整"));

        verify(projectTaskAppService).cancel(TASK_ID, "方向调整", auth);
        assertThat(result.getData().status()).isEqualTo("CANCELLED");
    }

    @Test
    void listTasksReturnsItems() {
        when(projectTaskAppService.list(PROJECT_ID, "TODO", auth))
                .thenReturn(List.<ProjectTaskView>of());

        assertThat(controller.listTasks(PROJECT_ID, "TODO").getData()).isEmpty();
    }

    @Test
    void unauthenticatedRequestReturns401() {
        AuthContextHolder.clear();

        assertThatThrownBy(() -> controller.listReports(PROJECT_ID))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.COMMON_401));
    }
}
