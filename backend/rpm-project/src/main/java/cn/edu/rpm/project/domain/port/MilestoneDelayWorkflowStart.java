package cn.edu.rpm.project.domain.port;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * 里程碑延期审批启动命令（T-PRJ-016）。
 *
 * @param requestId   延期申请 ID（审批业务对象）
 * @param milestoneId 里程碑 ID（业务版本维度，用于一次里程碑多次申请的实例区分）
 * @param projectId   项目 ID
 * @param projectType 项目类型（流程变量）
 * @param initiatorId 发起人用户 ID（PI）
 * @param at          业务时点（选择该时点生效的模板版本）
 */
public record MilestoneDelayWorkflowStart(
        UUID requestId,
        UUID milestoneId,
        UUID projectId,
        String projectType,
        UUID initiatorId,
        OffsetDateTime at
) {

    /** 校验必填字段。 */
    public MilestoneDelayWorkflowStart {
        Objects.requireNonNull(requestId, "requestId 不能为空");
        Objects.requireNonNull(milestoneId, "milestoneId 不能为空");
        Objects.requireNonNull(projectId, "projectId 不能为空");
        Objects.requireNonNull(initiatorId, "initiatorId 不能为空");
        Objects.requireNonNull(at, "at 不能为空");
    }
}
