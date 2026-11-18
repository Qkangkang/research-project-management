package cn.edu.rpm.project.infrastructure.adapter;

import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.common.util.UuidUtils;
import cn.edu.rpm.project.domain.port.ProjectApprovalWorkflowLauncher;
import cn.edu.rpm.project.domain.port.ProjectApprovalWorkflowStart;
import cn.edu.rpm.workflow.domain.dto.CancelWorkflowCmd;
import cn.edu.rpm.workflow.domain.dto.StartWorkflowCmd;
import cn.edu.rpm.workflow.domain.dto.WorkflowInstance;
import cn.edu.rpm.workflow.domain.model.WorkflowTemplateVersionCandidate;
import cn.edu.rpm.workflow.domain.port.WorkflowPort;
import cn.edu.rpm.workflow.domain.port.WorkflowTemplateQueryPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 项目变更/交接审批适配器（T-PRJ-019 / T-PRJ-020；技术文档 08 §8.2）。
 *
 * <p>按命令中的模板业务编码查询业务时点生效的模板版本，再通过
 * {@link WorkflowPort} 启动实例；流程定义 key 按业务类型外部化配置，
 * 禁止硬编码。</p>
 */
@Component
public class ProjectApprovalWorkflowAdapter implements ProjectApprovalWorkflowLauncher {

    private final WorkflowTemplateQueryPort templateQueryPort;

    private final WorkflowPort workflowPort;

    private final String changeProcessDefinitionKey;

    private final String handoverProcessDefinitionKey;

    /**
     * 构造适配器。
     *
     * @param templateQueryPort            模板查询端口
     * @param workflowPort                 工作流端口
     * @param changeProcessDefinitionKey   变更流程定义 key
     * @param handoverProcessDefinitionKey 交接流程定义 key
     */
    public ProjectApprovalWorkflowAdapter(
            WorkflowTemplateQueryPort templateQueryPort,
            WorkflowPort workflowPort,
            @Value("${rpm.project.approval.change.process-definition-key:projectChange}")
            String changeProcessDefinitionKey,
            @Value("${rpm.project.approval.handover.process-definition-key:projectHandover}")
            String handoverProcessDefinitionKey) {
        this.templateQueryPort = Objects.requireNonNull(templateQueryPort, "templateQueryPort 不能为空");
        this.workflowPort = Objects.requireNonNull(workflowPort, "workflowPort 不能为空");
        if (changeProcessDefinitionKey == null || changeProcessDefinitionKey.isBlank()) {
            throw new IllegalArgumentException(
                    "rpm.project.approval.change.process-definition-key 不能为空");
        }
        if (handoverProcessDefinitionKey == null || handoverProcessDefinitionKey.isBlank()) {
            throw new IllegalArgumentException(
                    "rpm.project.approval.handover.process-definition-key 不能为空");
        }
        this.changeProcessDefinitionKey = changeProcessDefinitionKey;
        this.handoverProcessDefinitionKey = handoverProcessDefinitionKey;
    }

    @Override
    public String start(ProjectApprovalWorkflowStart command) {
        Objects.requireNonNull(command, "command 不能为空");
        WorkflowTemplateVersionCandidate template = templateQueryPort
                .findEffectiveVersionByCode(command.templateCode(), command.at())
                .orElseThrow(() -> new BizException(ErrorCode.WF_001,
                        "未找到可用的项目审批模板：" + command.templateCode()));

        Map<String, Object> variables = new HashMap<>(command.variables());
        variables.put("projectId", UuidUtils.format(command.projectId()));
        if (command.projectType() != null) {
            variables.put("projectType", command.projectType());
        }

        WorkflowInstance instance = workflowPort.start(new StartWorkflowCmd(
                command.bizType(),
                command.bizId(),
                command.bizVersionId(),
                template.versionId(),
                processDefinitionKeyOf(command.bizType()),
                command.initiatorId(),
                variables));
        return instance.instanceId().toString();
    }

    @Override
    public void cancel(String instanceId, UUID operatorId, String reason, UUID requestId) {
        Objects.requireNonNull(instanceId, "instanceId 不能为空");
        Objects.requireNonNull(operatorId, "operatorId 不能为空");
        Objects.requireNonNull(requestId, "requestId 不能为空");
        workflowPort.cancelInstance(new CancelWorkflowCmd(
                UuidUtils.parse(instanceId), operatorId, reason, requestId));
    }

    private String processDefinitionKeyOf(String bizType) {
        return switch (bizType) {
            case "CHANGE" -> changeProcessDefinitionKey;
            case "HANDOVER" -> handoverProcessDefinitionKey;
            default -> throw new IllegalArgumentException("不支持的项目审批业务类型：" + bizType);
        };
    }
}
