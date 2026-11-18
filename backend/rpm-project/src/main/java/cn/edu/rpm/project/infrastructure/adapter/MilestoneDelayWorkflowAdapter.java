package cn.edu.rpm.project.infrastructure.adapter;

import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.common.util.UuidUtils;
import cn.edu.rpm.project.domain.port.MilestoneDelayWorkflowLauncher;
import cn.edu.rpm.project.domain.port.MilestoneDelayWorkflowStart;
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
 * 里程碑延期审批适配器（T-PRJ-016；技术文档 08 §6.1）。
 *
 * <p>按模板业务编码（外部配置 {@code rpm.project.milestone-delay.template-code}）
 * 查询业务时点生效的模板版本，再通过 {@link WorkflowPort} 启动
 * {@code MILESTONE_DELAY} 实例；流程定义 key 同样外部化
 * （{@code rpm.project.milestone-delay.process-definition-key}），禁止硬编码。</p>
 */
@Component
public class MilestoneDelayWorkflowAdapter implements MilestoneDelayWorkflowLauncher {

    /** 审批业务类型（V06 约束新增取值）。 */
    static final String BIZ_TYPE = "MILESTONE_DELAY";

    private final WorkflowTemplateQueryPort templateQueryPort;

    private final WorkflowPort workflowPort;

    private final String templateCode;

    private final String processDefinitionKey;

    /**
     * 构造适配器。
     *
     * @param templateQueryPort    模板查询端口
     * @param workflowPort         工作流端口
     * @param templateCode         延期审批模板编码
     * @param processDefinitionKey Flowable 流程定义 key
     */
    public MilestoneDelayWorkflowAdapter(WorkflowTemplateQueryPort templateQueryPort,
                                         WorkflowPort workflowPort,
                                         @Value("${rpm.project.milestone-delay.template-code:MILESTONE_DELAY}")
                                         String templateCode,
                                         @Value("${rpm.project.milestone-delay.process-definition-key}")
                                         String processDefinitionKey) {
        this.templateQueryPort = Objects.requireNonNull(templateQueryPort, "templateQueryPort 不能为空");
        this.workflowPort = Objects.requireNonNull(workflowPort, "workflowPort 不能为空");
        if (templateCode == null || templateCode.isBlank()) {
            throw new IllegalArgumentException("rpm.project.milestone-delay.template-code 不能为空");
        }
        if (processDefinitionKey == null || processDefinitionKey.isBlank()) {
            throw new IllegalArgumentException(
                    "rpm.project.milestone-delay.process-definition-key 不能为空");
        }
        this.templateCode = templateCode;
        this.processDefinitionKey = processDefinitionKey;
    }

    @Override
    public String start(MilestoneDelayWorkflowStart command) {
        Objects.requireNonNull(command, "command 不能为空");
        WorkflowTemplateVersionCandidate template = templateQueryPort
                .findEffectiveVersionByCode(templateCode, command.at())
                .orElseThrow(() -> new BizException(ErrorCode.WF_001,
                        "未找到可用的里程碑延期审批模板：" + templateCode));

        Map<String, Object> variables = new HashMap<>();
        variables.put("projectId", UuidUtils.format(command.projectId()));
        variables.put("milestoneId", UuidUtils.format(command.milestoneId()));
        if (command.projectType() != null) {
            variables.put("projectType", command.projectType());
        }

        WorkflowInstance instance = workflowPort.start(new StartWorkflowCmd(
                BIZ_TYPE,
                command.requestId(),
                command.milestoneId(),
                template.versionId(),
                processDefinitionKey,
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
}
