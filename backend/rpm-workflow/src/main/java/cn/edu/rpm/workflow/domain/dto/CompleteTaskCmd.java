package cn.edu.rpm.workflow.domain.dto;

import java.util.Objects;
import java.util.UUID;

/**
 * 完成审批任务命令（T-WF-008；技术文档 03 §3.2 / §3.5）。
 *
 * <p>幂等与并发口径：</p>
 * <ul>
 *   <li>{@code requestId} 为处理请求幂等键，同一键重复提交返回首次结果
 *       （wf_callback_log / wf_task.action_request_id）；</li>
 *   <li>{@code expectedVersion} 为读取任务时的 {@code wf_task.version}，
 *       携带旧版本提交返回 RPM-WF-004，不覆盖已处理结果（TC-APP-024）；</li>
 *   <li>{@code onBehalfOf} 仅在有效委托代理审批时填写委托人 ID，
 *       任务与审计记录同时保留真实操作者（TC-IAM-009）。</li>
 * </ul>
 *
 * @param instanceId      审批实例 ID
 * @param taskId          审批任务 ID
 * @param operatorId      真实操作人用户 ID
 * @param onBehalfOf      委托人用户 ID（代理审批时必填，否则为空）
 * @param action          处理动作：通过/退回/拒绝
 * @param comment         处理意见（退回/拒绝时必填，由应用服务校验）
 * @param expectedVersion 读取任务时的版本号（乐观锁）
 * @param requestId       请求幂等键
 */
public record CompleteTaskCmd(
        UUID instanceId,
        UUID taskId,
        UUID operatorId,
        UUID onBehalfOf,
        ApprovalAction action,
        String comment,
        Integer expectedVersion,
        UUID requestId
) {

    /** 校验必填字段。 */
    public CompleteTaskCmd {
        Objects.requireNonNull(instanceId, "instanceId 不能为空");
        Objects.requireNonNull(taskId, "taskId 不能为空");
        Objects.requireNonNull(operatorId, "operatorId 不能为空");
        Objects.requireNonNull(action, "action 不能为空");
        Objects.requireNonNull(expectedVersion, "expectedVersion 不能为空");
        Objects.requireNonNull(requestId, "requestId 不能为空");
    }
}
