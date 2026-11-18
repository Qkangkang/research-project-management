package cn.edu.rpm.workflow.application.dto;

import java.util.Objects;
import java.util.UUID;

/**
 * 审批撤回命令（T-WF-014；技术文档 03 §3.2 / TC-APP-027）。
 *
 * <p>撤回后实例进入 CANCELLED，未处理任务全部转 CANCELLED；同一 {@code requestId}
 * 重复提交幂等。</p>
 *
 * @param instanceId 审批实例 ID
 * @param reason     撤回原因（审计必填，最长 2048）
 * @param requestId  请求幂等键
 */
public record WithdrawCmd(
        UUID instanceId,
        String reason,
        UUID requestId
) {

    /** 撤回原因最大长度。 */
    public static final int MAX_REASON_LENGTH = 2048;

    /** 校验必填字段。 */
    public WithdrawCmd {
        Objects.requireNonNull(instanceId, "instanceId 不能为空");
        Objects.requireNonNull(requestId, "requestId 不能为空");
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("撤回原因不能为空");
        }
        if (reason.length() > MAX_REASON_LENGTH) {
            throw new IllegalArgumentException("撤回原因不能超过 " + MAX_REASON_LENGTH + " 字");
        }
    }
}
