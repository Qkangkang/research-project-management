package cn.edu.rpm.project.domain.port;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 项目类审批启动命令（T-PRJ-019 / T-PRJ-020）。
 *
 * <p>变更（{@code biz_type = CHANGE}）与负责人交接（{@code biz_type = HANDOVER}）
 * 共用同一启动端口，模板编码由业务服务按类型确定。</p>
 *
 * @param bizType     业务类型：CHANGE / HANDOVER
 * @param bizId       业务对象 ID（变更单 / 交接单）
 * @param bizVersionId 业务版本 ID（变更版本 / 交接单自身）
 * @param templateCode 审批模板业务编码，如 {@code CHANGE_PERIOD}
 * @param projectId   项目 ID（流程变量）
 * @param projectType 项目类型（流程变量，可为空）
 * @param initiatorId 发起人用户 ID
 * @param variables   附加流程变量
 * @param at          业务时点（选择生效模板版本）
 */
public record ProjectApprovalWorkflowStart(
        String bizType,
        UUID bizId,
        UUID bizVersionId,
        String templateCode,
        UUID projectId,
        String projectType,
        UUID initiatorId,
        Map<String, Object> variables,
        OffsetDateTime at
) {

    /** 校验必填字段并复制流程变量。 */
    public ProjectApprovalWorkflowStart {
        Objects.requireNonNull(bizType, "bizType 不能为空");
        Objects.requireNonNull(bizId, "bizId 不能为空");
        Objects.requireNonNull(bizVersionId, "bizVersionId 不能为空");
        Objects.requireNonNull(templateCode, "templateCode 不能为空");
        Objects.requireNonNull(projectId, "projectId 不能为空");
        Objects.requireNonNull(initiatorId, "initiatorId 不能为空");
        Objects.requireNonNull(at, "at 不能为空");
        variables = variables == null ? Map.of() : Map.copyOf(variables);
    }
}
