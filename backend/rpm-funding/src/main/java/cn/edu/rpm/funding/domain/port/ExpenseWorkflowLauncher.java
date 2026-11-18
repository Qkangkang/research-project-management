package cn.edu.rpm.funding.domain.port;

import java.util.Optional;
import java.util.UUID;

/**
 * 支出与特批审批启动端口（T-FUND-020 / T-FUND-024）。
 *
 * <p>定义在 {@code rpm-funding}，适配器位于基础设施层，内部只调用
 * {@code rpm-workflow} 的 {@link cn.edu.rpm.workflow.domain.port.WorkflowPort}
 * 与模板查询端口；业务模块不得直接操作 Flowable 表。</p>
 */
public interface ExpenseWorkflowLauncher {

    /**
     * 启动支出审批实例。
     *
     * @param command 启动命令
     * @return 审批实例 ID
     */
    String startExpense(ExpenseWorkflowStart command);

    /**
     * 启动特批审批实例。
     *
     * @param command 启动命令
     * @return 审批实例 ID
     */
    String startSpecialApproval(ExpenseWorkflowStart command);

    /**
     * 按业务标识查询审批实例 ID（幂等重放时恢复首次结果）。
     *
     * @param bizType      业务类型（EXPENSE / SPECIAL_APPROVAL）
     * @param bizId        业务对象 ID
     * @param bizVersionId 业务版本 ID
     * @return 实例 ID；不存在返回空
     */
    Optional<String> findInstanceId(String bizType, UUID bizId, UUID bizVersionId);

    /**
     * 按业务对象取消审批实例（幂等：实例已终态返回首次结果）。
     *
     * @param bizType    业务类型（EXPENSE / SPECIAL_APPROVAL）
     * @param bizId      业务对象 ID
     * @param bizVersionId 业务版本 ID
     * @param operatorId 操作人用户 ID
     * @param reason     取消原因
     * @param requestId  请求幂等键
     */
    void cancel(String bizType, UUID bizId, UUID bizVersionId, UUID operatorId,
                String reason, UUID requestId);

    /**
     * 审批启动命令。
     *
     * @param bizType     业务类型
     * @param projectId   项目 ID
     * @param bizId       业务对象 ID
     * @param bizVersionId 业务版本 ID
     * @param initiatorId 发起人用户 ID
     * @param amount      金额（用于流程路由）
     * @param variables   额外流程变量
     */
    record ExpenseWorkflowStart(
            String bizType,
            UUID projectId,
            UUID bizId,
            UUID bizVersionId,
            UUID initiatorId,
            java.math.BigDecimal amount,
            java.util.Map<String, Object> variables
    ) {
    }
}
