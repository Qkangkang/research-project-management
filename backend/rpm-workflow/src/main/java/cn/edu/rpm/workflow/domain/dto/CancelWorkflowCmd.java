package cn.edu.rpm.workflow.domain.dto;

import java.util.Objects;
import java.util.UUID;

/**
 * 取消审批实例命令（T-WF-008；技术文档 03 §3.2）。
 *
 * <p>触发场景：申请人撤回、管理员终止。同一 {@code requestId} 重复调用必须幂等，
 * 实例已处于终态时应返回首次结果而不是报错。</p>
 *
 * @param instanceId 审批实例 ID
 * @param operatorId 操作人用户 ID
 * @param reason     取消原因（审计必填）
 * @param requestId  请求幂等键
 */
public record CancelWorkflowCmd(
        UUID instanceId,
        UUID operatorId,
        String reason,
        UUID requestId
) {

    /** 校验必填字段与原因非空。 */
    public CancelWorkflowCmd {
        Objects.requireNonNull(instanceId, "instanceId 不能为空");
        Objects.requireNonNull(operatorId, "operatorId 不能为空");
        Objects.requireNonNull(reason, "reason 不能为空");
        Objects.requireNonNull(requestId, "requestId 不能为空");
        if (reason.isBlank()) {
            throw new IllegalArgumentException("reason 不能为空白");
        }
    }
}
