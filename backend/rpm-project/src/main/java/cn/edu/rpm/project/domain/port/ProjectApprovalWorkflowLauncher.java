package cn.edu.rpm.project.domain.port;

import java.util.UUID;

/**
 * 项目变更/交接审批启动端口（T-PRJ-019 / T-PRJ-020）。
 *
 * <p>定义在 {@code rpm-project}、适配器位于基础设施层，内部仅调用
 * {@code rpm-workflow} 的 {@code WorkflowPort} 与模板查询端口，
 * 业务模块不得直接操作 Flowable 表。</p>
 */
public interface ProjectApprovalWorkflowLauncher {

    /**
     * 启动变更或交接审批实例。
     *
     * @param command 启动命令
     * @return 审批实例 ID
     * @throws cn.edu.rpm.common.exception.BizException 模板缺失 {@code RPM-WF-001}
     */
    String start(ProjectApprovalWorkflowStart command);

    /**
     * 取消审批实例（幂等：实例已终态返回首次结果）。
     *
     * @param instanceId 审批实例 ID
     * @param operatorId 操作人用户 ID
     * @param reason     取消原因
     * @param requestId  请求幂等键
     */
    void cancel(String instanceId, UUID operatorId, String reason, UUID requestId);
}
