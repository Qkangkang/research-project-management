package cn.edu.rpm.workflow.domain.dto;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 启动审批实例命令（T-WF-008；技术文档 03 §3.2 / §3.5）。
 *
 * <p>字段与 {@code wf_instance} 的业务列一一对应；{@code variables} 仅承载
 * 流程路由/处理人解析所需的流程变量，不得承载业务事实（业务事实由业务模块持有）。</p>
 *
 * <p>{@code processDefinitionKey} 指向 Flowable 已部署的流程定义 key。模板版本
 * {@code definition} 与 BPMN 定义的绑定属于部署期配置（一期由运维随模板发布部署），
 * 适配器不解析模板 JSON 生成 BPMN（技术文档 03 §3.3 一期不开放任意 BPMN）。</p>
 *
 * @param bizType              业务类型（APPLICATION / EXPENSE / CHANGE 等，取值以 V06/V07 约束为准）
 * @param bizId                业务对象 ID
 * @param bizVersionId         业务版本 ID（如 application_version.id）
 * @param templateVersionId    绑定的模板版本 ID
 * @param processDefinitionKey Flowable 流程定义 key（已部署）
 * @param initiatorId          发起人用户 ID
 * @param variables            流程变量（条件网关/候选人解析）；为空按空映射处理
 */
public record StartWorkflowCmd(
        String bizType,
        UUID bizId,
        UUID bizVersionId,
        UUID templateVersionId,
        String processDefinitionKey,
        UUID initiatorId,
        Map<String, Object> variables
) {

    /** 校验必填字段并复制流程变量。 */
    public StartWorkflowCmd {
        Objects.requireNonNull(bizType, "bizType 不能为空");
        Objects.requireNonNull(bizId, "bizId 不能为空");
        Objects.requireNonNull(bizVersionId, "bizVersionId 不能为空");
        Objects.requireNonNull(templateVersionId, "templateVersionId 不能为空");
        if (processDefinitionKey == null || processDefinitionKey.isBlank()) {
            throw new IllegalArgumentException("processDefinitionKey 不能为空");
        }
        Objects.requireNonNull(initiatorId, "initiatorId 不能为空");
        variables = variables == null ? Map.of() : Map.copyOf(variables);
    }
}
