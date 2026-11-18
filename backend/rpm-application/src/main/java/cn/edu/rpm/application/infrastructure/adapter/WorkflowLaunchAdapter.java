package cn.edu.rpm.application.infrastructure.adapter;

import cn.edu.rpm.application.domain.port.ApplicationWorkflowLauncher;
import cn.edu.rpm.application.domain.port.ApplicationWorkflowStart;
import cn.edu.rpm.application.domain.port.WorkflowLaunch;
import cn.edu.rpm.common.util.UuidUtils;
import cn.edu.rpm.workflow.domain.dto.StartWorkflowCmd;
import cn.edu.rpm.workflow.domain.dto.TemplateRoutingContext;
import cn.edu.rpm.workflow.domain.dto.WorkflowInstance;
import cn.edu.rpm.workflow.domain.port.WorkflowPort;
import cn.edu.rpm.workflow.domain.service.WorkflowTemplateResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 申报审批启动适配器（T-APP-014；技术文档 04 §3.2 步骤 6）。
 *
 * <p>组合 {@link WorkflowTemplateResolver}（按项目类型与业务时点选择模板版本）与
 * {@link WorkflowPort}（启动实例），业务模块不接触 Flowable API 或表。流程定义 key
 * 属于部署期配置，由 {@code rpm.application.workflow.process-definition-key}
 * 外部化（禁止硬编码在代码中）。</p>
 */
@Component
public class WorkflowLaunchAdapter implements ApplicationWorkflowLauncher {

    /** 审批业务类型（与 wf_instance ck 约束取值一致）。 */
    static final String BIZ_TYPE = "APPLICATION";

    private final WorkflowTemplateResolver templateResolver;

    private final WorkflowPort workflowPort;

    private final String processDefinitionKey;

    /**
     * 构造适配器。
     *
     * @param templateResolver     审批模板路由
     * @param workflowPort         工作流端口
     * @param processDefinitionKey Flowable 流程定义 key（外部配置）
     */
    public WorkflowLaunchAdapter(WorkflowTemplateResolver templateResolver,
                                 WorkflowPort workflowPort,
                                 @Value("${rpm.application.workflow.process-definition-key}")
                                 String processDefinitionKey) {
        this.templateResolver = Objects.requireNonNull(templateResolver, "templateResolver 不能为空");
        this.workflowPort = Objects.requireNonNull(workflowPort, "workflowPort 不能为空");
        if (processDefinitionKey == null || processDefinitionKey.isBlank()) {
            throw new IllegalArgumentException("rpm.application.workflow.process-definition-key 不能为空");
        }
        this.processDefinitionKey = processDefinitionKey;
    }

    @Override
    public WorkflowLaunch start(ApplicationWorkflowStart command) {
        Objects.requireNonNull(command, "command 不能为空");
        UUID templateVersionId = templateResolver.resolve(new TemplateRoutingContext(
                        command.projectType(), command.deptId(), null, null, null, null, null),
                command.at());
        WorkflowInstance instance = workflowPort.start(new StartWorkflowCmd(
                BIZ_TYPE,
                command.applicationId(),
                command.versionId(),
                templateVersionId,
                processDefinitionKey,
                command.applicantId(),
                Map.of(
                        "projectType", command.projectType(),
                        "assignee", UuidUtils.formatCompact(command.applicantId()))));
        return new WorkflowLaunch(instance.instanceId(), instance.status());
    }

    @Override
    public Optional<WorkflowLaunch> findLatest(UUID applicationId, UUID versionId) {
        Objects.requireNonNull(applicationId, "applicationId 不能为空");
        Objects.requireNonNull(versionId, "versionId 不能为空");
        return workflowPort.findInstanceByBiz(BIZ_TYPE, applicationId, versionId)
                .map(instance -> new WorkflowLaunch(instance.instanceId(), instance.status()));
    }
}
