package cn.edu.rpm.funding.infrastructure.adapter;

import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.common.util.UuidUtils;
import cn.edu.rpm.funding.domain.port.ExpenseWorkflowLauncher;
import cn.edu.rpm.workflow.domain.dto.CancelWorkflowCmd;
import cn.edu.rpm.workflow.domain.dto.StartWorkflowCmd;
import cn.edu.rpm.workflow.domain.dto.WorkflowInstance;
import cn.edu.rpm.workflow.domain.model.WorkflowTemplateVersionCandidate;
import cn.edu.rpm.workflow.domain.port.WorkflowPort;
import cn.edu.rpm.workflow.domain.port.WorkflowTemplateQueryPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 支出/特批审批适配器（T-FUND-020 / T-FUND-024；技术文档 05 §3.2、08 §7.1）。
 *
 * <p>按业务编码查询业务时点生效的模板版本，再通过 {@link WorkflowPort} 启动实例；
 * 流程定义 key 外部化配置，禁止硬编码。取消按业务标识查询最近实例后幂等取消。</p>
 */
@Component
public class ExpenseWorkflowAdapter implements ExpenseWorkflowLauncher {

    /** 模板业务编码：支出审批。 */
    public static final String TEMPLATE_EXPENSE = "EXPENSE_APPROVAL";

    /** 模板业务编码：特批审批。 */
    public static final String TEMPLATE_SPECIAL = "SPECIAL_APPROVAL";

    private final WorkflowTemplateQueryPort templateQueryPort;

    private final WorkflowPort workflowPort;

    private final String expenseProcessDefinitionKey;

    private final String specialProcessDefinitionKey;

    /**
     * 构造适配器。
     *
     * @param templateQueryPort              模板查询端口
     * @param workflowPort                   工作流端口
     * @param expenseProcessDefinitionKey    支出流程定义 key
     * @param specialProcessDefinitionKey    特批流程定义 key
     */
    public ExpenseWorkflowAdapter(
            WorkflowTemplateQueryPort templateQueryPort,
            WorkflowPort workflowPort,
            @Value("${rpm.funding.expense.process-definition-key:expenseApproval}")
            String expenseProcessDefinitionKey,
            @Value("${rpm.funding.special-approval.process-definition-key:specialApproval}")
            String specialProcessDefinitionKey) {
        this.templateQueryPort = Objects.requireNonNull(templateQueryPort, "templateQueryPort 不能为空");
        this.workflowPort = Objects.requireNonNull(workflowPort, "workflowPort 不能为空");
        if (expenseProcessDefinitionKey == null || expenseProcessDefinitionKey.isBlank()) {
            throw new IllegalArgumentException(
                    "rpm.funding.expense.process-definition-key 不能为空");
        }
        if (specialProcessDefinitionKey == null || specialProcessDefinitionKey.isBlank()) {
            throw new IllegalArgumentException(
                    "rpm.funding.special-approval.process-definition-key 不能为空");
        }
        this.expenseProcessDefinitionKey = expenseProcessDefinitionKey;
        this.specialProcessDefinitionKey = specialProcessDefinitionKey;
    }

    @Override
    public String startExpense(ExpenseWorkflowStart command) {
        return start(command, TEMPLATE_EXPENSE, expenseProcessDefinitionKey);
    }

    @Override
    public String startSpecialApproval(ExpenseWorkflowStart command) {
        return start(command, TEMPLATE_SPECIAL, specialProcessDefinitionKey);
    }

    @Override
    public void cancel(String bizType, UUID bizId, UUID bizVersionId, UUID operatorId,
                       String reason, UUID requestId) {
        Objects.requireNonNull(bizType, "bizType 不能为空");
        Objects.requireNonNull(bizId, "bizId 不能为空");
        Objects.requireNonNull(operatorId, "operatorId 不能为空");
        Objects.requireNonNull(requestId, "requestId 不能为空");
        workflowPort.findInstanceByBiz(bizType, bizId, bizVersionId)
                .ifPresent(instance -> workflowPort.cancelInstance(new CancelWorkflowCmd(
                        instance.instanceId(), operatorId, reason, requestId)));
    }

    @Override
    public java.util.Optional<String> findInstanceId(String bizType, UUID bizId, UUID bizVersionId) {
        Objects.requireNonNull(bizType, "bizType 不能为空");
        Objects.requireNonNull(bizId, "bizId 不能为空");
        return workflowPort.findInstanceByBiz(bizType, bizId, bizVersionId)
                .map(instance -> instance.instanceId().toString());
    }

    private String start(ExpenseWorkflowStart command, String templateCode, String processDefinitionKey) {
        Objects.requireNonNull(command, "command 不能为空");
        WorkflowTemplateVersionCandidate template = templateQueryPort
                .findEffectiveVersionByCode(templateCode, OffsetDateTime.now())
                .orElseThrow(() -> new BizException(ErrorCode.WF_001,
                        "未找到可用的经费审批模板：" + templateCode));
        Map<String, Object> variables = new HashMap<>(command.variables());
        variables.put("projectId", UuidUtils.format(command.projectId()));
        variables.put("amount", command.amount());
        WorkflowInstance instance = workflowPort.start(new StartWorkflowCmd(
                command.bizType(),
                command.bizId(),
                command.bizVersionId(),
                template.versionId(),
                processDefinitionKey,
                command.initiatorId(),
                variables));
        return instance.instanceId().toString();
    }
}
