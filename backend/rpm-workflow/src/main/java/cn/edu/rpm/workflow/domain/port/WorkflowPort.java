package cn.edu.rpm.workflow.domain.port;

import cn.edu.rpm.workflow.domain.dto.CancelWorkflowCmd;
import cn.edu.rpm.workflow.domain.dto.CompleteTaskCmd;
import cn.edu.rpm.workflow.domain.dto.StartWorkflowCmd;
import cn.edu.rpm.workflow.domain.dto.TaskQuery;
import cn.edu.rpm.workflow.domain.dto.WorkflowInstance;
import cn.edu.rpm.workflow.domain.dto.WorkflowTaskView;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 工作流端口（T-WF-008；技术文档 03 §3.2）。
 *
 * <p>业务模块只依赖本接口，不得直接操作 Flowable 表或 API；实现由
 * {@code FlowableWorkflowAdapter} 在 Batch-7 提供。边界约定（技术文档 03 §3.1）：</p>
 * <ul>
 *   <li>Flowable 负责节点流转、任务分配、会签/或签、时限、退回、代理与模板版本；</li>
 *   <li>业务模块负责批准后的业务动作（立项、占用预算、切换基线、冻结验收）；</li>
 *   <li>Flowable 不得直接更新预算余额、项目状态或验收快照，只发出审批结果。</li>
 * </ul>
 */
public interface WorkflowPort {

    /**
     * 按模板版本启动审批实例。
     *
     * <p>同一业务版本已有 RUNNING 实例时，由 {@code uk_wf_instance_running}
     * 拒绝重复启动（TC-APP-009）；调用方需保证 {@code templateVersionId} 为
     * 已发布且对业务时点生效的版本。</p>
     *
     * @param cmd 启动命令（业务类型、业务/版本 ID、模板版本、发起人、流程变量）
     * @return 已创建的审批实例视图（含 Flowable 流程实例 ID）
     */
    WorkflowInstance start(StartWorkflowCmd cmd);

    /**
     * 查询业务版本对应的最近审批实例（含终态）。
     *
     * <p>幂等回放场景使用：业务侧重复提交（同一 {@code Idempotency-Key}）需要返回
     * 首次启动的实例 ID，而不是再次启动流程。按 {@code started_at} 倒序取最近一条；
     * 业务版本尚未发起审批时返回空。</p>
     *
     * @param bizType      业务类型（如 APPLICATION）
     * @param bizId        业务对象 ID
     * @param bizVersionId 业务版本 ID
     * @return 最近审批实例视图；无实例返回空 {@link Optional}
     */
    Optional<WorkflowInstance> findInstanceByBiz(String bizType, UUID bizId, UUID bizVersionId);

    /**
     * 完成审批任务（通过/退回/拒绝）。
     *
     * <p>实现必须校验任务归属、代理关系、任务版本与职责分离规则；同一
     * {@code requestId} 重复提交返回首次结果（TC-APP-023），携带旧
     * {@code expectedVersion} 时不得覆盖已处理结果（TC-APP-024）。</p>
     *
     * @param cmd 任务处理命令（任务/实例、真实操作人、代理身份、动作、意见、幂等键）
     */
    void completeTask(CompleteTaskCmd cmd);

    /**
     * 取消审批实例（申请人撤回或管理员终止）。
     *
     * <p>取消后实例进入 CANCELLED 终态，未处理任务置为 CANCELLED；同一
     * {@code requestId} 重复调用必须幂等。</p>
     *
     * @param cmd 取消命令（实例、操作人、原因、幂等键）
     */
    void cancelInstance(CancelWorkflowCmd cmd);

    /**
     * 查询指定用户的待办任务。
     *
     * <p>只返回该用户作为处理人的任务；代理场景由调用方先解析有效委托，
     * 或由实现按 {@code TaskQuery} 状态过滤。分页参数在
     * {@link TaskQuery} 内约束。</p>
     *
     * @param userId 当前用户 ID（按人员 ID，不按账号名）
     * @param query  查询条件（业务类型、状态、分页）
     * @return 待办任务视图列表；无命中返回空列表
     */
    List<WorkflowTaskView> findMyTasks(UUID userId, TaskQuery query);
}
