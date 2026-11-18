package cn.edu.rpm.workflow.api;

import cn.edu.rpm.common.api.PageVO;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.common.exception.GlobalExceptionHandler;
import cn.edu.rpm.iam.application.context.AuthContextHolder;
import cn.edu.rpm.iam.application.dto.AuthContext;
import cn.edu.rpm.workflow.application.dto.ActTaskCmd;
import cn.edu.rpm.workflow.application.service.WorkflowTaskAppService;
import cn.edu.rpm.workflow.domain.dto.ApprovalTrail;
import cn.edu.rpm.workflow.domain.dto.WorkflowInstance;
import cn.edu.rpm.workflow.domain.dto.WorkflowTaskView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link WorkflowTaskController} MockMvc 单元测试（T-WF-015 先行测试）。
 *
 * <p>覆盖：未登录 401、待办列表、任务处理响应（含下一节点 ID）、审批轨迹、
 * 非法 UUID 返回 400。</p>
 */
@ExtendWith(MockitoExtension.class)
class WorkflowTaskControllerTest {

    @Mock
    private WorkflowTaskAppService appService;

    private MockMvc mockMvc;

    private final UUID userUuid = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new WorkflowTaskController(appService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        AuthContextHolder.clear();
    }

    private void authenticate() {
        AuthContextHolder.set(new AuthContext(userUuid, null, "emp-001", Set.of(), Set.of(),
                Instant.now()));
    }

    private WorkflowTaskView taskView() {
        return new WorkflowTaskView(UUID.randomUUID(), UUID.randomUUID(), "APPLICATION",
                UUID.randomUUID(), "formalReview", "形式审查", userUuid, null,
                "PENDING", OffsetDateTime.now(), 1, 0);
    }

    @Test
    void unauthenticatedRequestReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/workflow/tasks"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("RPM-COMMON-401"));
    }

    @Test
    void listMyTasksReturnsPagedItems() throws Exception {
        authenticate();
        WorkflowTaskView view = taskView();
        when(appService.findMyTasks(any(), any()))
                .thenReturn(PageVO.of(List.of(view), 1, 20, 1));

        mockMvc.perform(get("/api/v1/workflow/tasks")
                        .param("bizType", "APPLICATION")
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].task_id").value(view.taskId().toString()))
                .andExpect(jsonPath("$.data.items[0].biz_type").value("APPLICATION"))
                .andExpect(jsonPath("$.data.items[0].version").value(0));
    }

    @Test
    void actReturnsNextTaskIds() throws Exception {
        authenticate();
        UUID nextTaskId = UUID.randomUUID();
        when(appService.act(any(), any(ActTaskCmd.class), any()))
                .thenReturn(new cn.edu.rpm.workflow.domain.dto.TaskActionResult(
                        UUID.randomUUID(), UUID.randomUUID(), "APPROVED", "RUNNING",
                        List.of(nextTaskId)));

        String body = """
                {
                  "action": "APPROVED",
                  "comment": "同意",
                  "on_behalf_of": null,
                  "expected_version": 0,
                  "request_id": "9f8e7d6c-5b4a-4938-8271-6a5b4c3d2e1f"
                }
                """;
        mockMvc.perform(post("/api/v1/workflow/tasks/" + UUID.randomUUID() + "/actions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.task_status").value("APPROVED"))
                .andExpect(jsonPath("$.data.instance_status").value("RUNNING"))
                .andExpect(jsonPath("$.data.next_task_ids[0]").value(nextTaskId.toString()));
    }

    @Test
    void malformedTaskIdReturns400() throws Exception {
        authenticate();

        mockMvc.perform(post("/api/v1/workflow/tasks/not-a-uuid/actions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"action":"APPROVED","comment":"同意","expected_version":0,
                                 "request_id":"9f8e7d6c-5b4a-4938-8271-6a5b4c3d2e1f"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RPM-COMMON-400"));
    }

    @Test
    void missingRequestIdReturns400() throws Exception {
        authenticate();

        mockMvc.perform(post("/api/v1/workflow/tasks/" + UUID.randomUUID() + "/actions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"APPROVED\",\"comment\":\"同意\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RPM-COMMON-400"));
    }

    @Test
    void trailReturnsInstanceAndTasks() throws Exception {
        authenticate();
        UUID instanceId = UUID.randomUUID();
        WorkflowInstance instance = new WorkflowInstance(instanceId, "pi-1", "APPLICATION",
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), userUuid,
                "RUNNING", OffsetDateTime.now(), null);
        when(appService.findTrail(eq(instanceId), any()))
                .thenReturn(new ApprovalTrail(instance, List.of(taskView())));

        mockMvc.perform(get("/api/v1/workflow/instances/" + instanceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.instance_id").value(instanceId.toString()))
                .andExpect(jsonPath("$.data.status").value("RUNNING"))
                .andExpect(jsonPath("$.data.tasks[0].node_key").value("formalReview"));
    }

    @Test
    void businessExceptionUsesDocumentedHttpStatus() throws Exception {
        authenticate();
        when(appService.findTrail(any(), any()))
                .thenThrow(new BizException(ErrorCode.COMMON_404, "审批实例不存在"));

        mockMvc.perform(get("/api/v1/workflow/instances/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RPM-COMMON-404"));
    }
}
