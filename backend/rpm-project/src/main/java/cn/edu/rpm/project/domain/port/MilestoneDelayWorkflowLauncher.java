package cn.edu.rpm.project.domain.port;

import java.util.UUID;

/**
 * 里程碑延期审批启动端口（T-PRJ-016；技术文档 08 §6.1）。
 *
 * <p>定义在 {@code rpm-project}、适配器位于基础设施层，内部只调用
 * {@code rpm-workflow} 的 {@code WorkflowPort} 与模板查询端口；
 * 业务模块不得直接操作 Flowable 表。</p>
 */
public interface MilestoneDelayWorkflowLauncher {

    /**
     * 启动延期审批实例（{@code biz_type = MILESTONE_DELAY}）。
     *
     * @param command 启动命令
     * @return 审批实例 ID
     * @throws cn.edu.rpm.common.exception.BizException 模板缺失 {@code RPM-WF-001}
     */
    String start(MilestoneDelayWorkflowStart command);

    /**
     * 撤回延期申请时取消审批实例（幂等：实例已终态返回首次结果）。
     *
     * @param instanceId 审批实例 ID
     * @param operatorId 操作人用户 ID
     * @param reason     取消原因
     * @param requestId  请求幂等键（取延期申请 ID）
     */
    void cancel(String instanceId, UUID operatorId, String reason, UUID requestId);
}
