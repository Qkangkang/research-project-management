package cn.edu.rpm.application.domain.port;

import java.util.Objects;
import java.util.UUID;

/**
 * 审批启动结果（T-APP-014）。
 *
 * @param instanceId 审批实例 ID（{@code wf_instance.id}）
 * @param status     实例状态（RUNNING / 终态）
 */
public record WorkflowLaunch(UUID instanceId, String status) {

    /** 校验必填字段。 */
    public WorkflowLaunch {
        Objects.requireNonNull(instanceId, "instanceId 不能为空");
        Objects.requireNonNull(status, "status 不能为空");
    }
}
