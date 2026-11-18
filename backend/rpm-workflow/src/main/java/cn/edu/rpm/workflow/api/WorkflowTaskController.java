package cn.edu.rpm.workflow.api;

import cn.edu.rpm.common.api.PageVO;
import cn.edu.rpm.common.api.Result;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.common.util.UuidUtils;
import cn.edu.rpm.iam.application.context.AuthContextHolder;
import cn.edu.rpm.iam.application.dto.AuthContext;
import cn.edu.rpm.workflow.api.dto.InitiatedInstanceItem;
import cn.edu.rpm.workflow.api.dto.TaskActionRequest;
import cn.edu.rpm.workflow.api.dto.TaskActionResponse;
import cn.edu.rpm.workflow.api.dto.WorkflowInstanceResponse;
import cn.edu.rpm.workflow.api.dto.WorkflowTaskItem;
import cn.edu.rpm.workflow.application.dto.ActTaskCmd;
import cn.edu.rpm.workflow.application.service.WorkflowTaskAppService;
import cn.edu.rpm.workflow.domain.dto.ApprovalAction;
import cn.edu.rpm.workflow.domain.dto.ApprovalTrail;
import cn.edu.rpm.workflow.domain.dto.InitiatedInstanceView;
import cn.edu.rpm.workflow.domain.dto.TaskQuery;
import cn.edu.rpm.workflow.domain.dto.WorkflowTaskView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 审批任务接口（T-WF-015；技术文档 04 §2.3 / §3.11）。
 *
 * <p>三个端点：</p>
 * <ul>
 *   <li>{@code GET /api/v1/workflow/tasks}：我的待办（当前登录用户为处理人）；</li>
 *   <li>{@code POST /api/v1/workflow/tasks/{taskId}/actions}：通过/退回/拒绝，
 *       返回下一节点任务 ID；</li>
 *   <li>{@code GET /api/v1/workflow/instances/{instanceId}}：审批轨迹。</li>
 * </ul>
 *
 * <p>Controller 只做参数形态校验与身份提取；任务归属、代理关系、版本、SOD
 * 与业务副作用全部在 {@link WorkflowTaskAppService} 内完成（禁止在 Controller 写业务规则）。
 * 未登录由 {@link AuthContextHolder#require()} 抛 {@code RPM-COMMON-401}。</p>
 */
@RestController
@RequestMapping("/api/v1/workflow")
public class WorkflowTaskController {

    private final WorkflowTaskAppService appService;

    /**
     * 构造控制器。
     *
     * @param appService 审批任务应用服务
     */
    public WorkflowTaskController(WorkflowTaskAppService appService) {
        this.appService = appService;
    }

    /**
     * 查询我的待办。
     *
     * @param bizType 业务类型过滤；为空不过滤
     * @param status  任务状态；默认为待处理
     * @param page    页码（从 1 开始）
     * @param size    每页条数（1～100）
     * @return 分页待办
     */
    @GetMapping("/tasks")
    public Result<PageVO<WorkflowTaskItem>> myTasks(
            @RequestParam(name = "bizType", required = false) String bizType,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        AuthContext auth = AuthContextHolder.require();
        TaskQuery query;
        try {
            query = new TaskQuery(bizType, status, page, size);
        } catch (IllegalArgumentException ex) {
            throw new BizException(ErrorCode.COMMON_400, ex.getMessage());
        }
        PageVO<WorkflowTaskView> pageResult = appService.findMyTasks(query, auth);
        List<WorkflowTaskItem> items = pageResult.getItems().stream()
                .map(WorkflowTaskItem::from)
                .toList();
        return Result.ok(PageVO.of(items, pageResult.getPage(), pageResult.getSize(),
                pageResult.getTotal()));
    }

    /**
     * 处理审批任务（通过/退回/拒绝）。
     *
     * @param taskId  任务 ID
     * @param request 处理请求
     * @return 动作后的任务/实例状态与下一节点任务
     */
    @PostMapping("/tasks/{taskId}/actions")
    public Result<TaskActionResponse> act(@PathVariable("taskId") String taskId,
                                          @RequestBody TaskActionRequest request) {
        AuthContext auth = AuthContextHolder.require();
        ActTaskCmd cmd = toCmd(request);
        return Result.ok(TaskActionResponse.from(
                appService.act(parseUuid(taskId, "taskId"), cmd, auth)));
    }

    /**
     * 查询单个审批任务（详情页/已办轨迹入口；参与人与管理员可见）。
     *
     * @param taskId 任务 ID
     * @return 任务详情
     */
    @GetMapping("/tasks/{taskId}")
    public Result<WorkflowTaskItem> task(@PathVariable("taskId") String taskId) {
        AuthContext auth = AuthContextHolder.require();
        return Result.ok(WorkflowTaskItem.from(
                appService.findTask(parseUuid(taskId, "taskId"), auth)));
    }

    /**
     * 查询审批轨迹。
     *
     * @param instanceId 审批实例 ID
     * @return 实例与任务轨迹
     */
    @GetMapping("/instances/{instanceId}")
    public Result<WorkflowInstanceResponse> trail(@PathVariable("instanceId") String instanceId) {
        AuthContext auth = AuthContextHolder.require();
        ApprovalTrail trail = appService.findTrail(parseUuid(instanceId, "instanceId"), auth);
        return Result.ok(WorkflowInstanceResponse.from(trail));
    }

    /**
     * 查询我发起的审批实例（审批中心「我发起的」）。
     *
     * @param page 页码（从 1 开始）
     * @param size 每页条数（1～100）
     * @return 分页实例（数据范围收敛为当前登录用户发起的实例）
     */
    @GetMapping("/instances/initiated")
    public Result<PageVO<InitiatedInstanceItem>> myInitiated(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        AuthContext auth = AuthContextHolder.require();
        PageVO<InitiatedInstanceView> pageResult = appService.findMyInitiated(page, size, auth);
        List<InitiatedInstanceItem> items = pageResult.getItems().stream()
                .map(InitiatedInstanceItem::from)
                .toList();
        return Result.ok(PageVO.of(items, pageResult.getPage(), pageResult.getSize(),
                pageResult.getTotal()));
    }

    // ---------------------------------------------------------------------
    // 参数解析（形态错误统一 RPM-COMMON-400，避免落到 500）
    // ---------------------------------------------------------------------

    private ActTaskCmd toCmd(TaskActionRequest request) {
        try {
            return new ActTaskCmd(
                    parseUuid(request.requestId(), "request_id"),
                    request.expectedVersion(),
                    parseAction(request.action()),
                    request.comment(),
                    optionalUuid(request.onBehalfOf(), "on_behalf_of"),
                    optionalUuid(request.reassignTo(), "reassign_to"));
        } catch (NullPointerException | IllegalArgumentException ex) {
            throw new BizException(ErrorCode.COMMON_400,
                    ex.getMessage() == null ? "请求参数不合法" : ex.getMessage());
        }
    }

    private ApprovalAction parseAction(String action) {
        if (action == null || action.isBlank()) {
            return null;
        }
        try {
            return ApprovalAction.valueOf(action);
        } catch (IllegalArgumentException ex) {
            throw new BizException(ErrorCode.COMMON_400, "不支持的处理动作: " + action);
        }
    }

    private UUID optionalUuid(String text, String field) {
        return text == null || text.isBlank() ? null : parseUuid(text, field);
    }

    private UUID parseUuid(String text, String field) {
        try {
            return UuidUtils.parse(text);
        } catch (IllegalArgumentException ex) {
            throw new BizException(ErrorCode.COMMON_400, field + " 不是合法 UUID");
        }
    }
}
