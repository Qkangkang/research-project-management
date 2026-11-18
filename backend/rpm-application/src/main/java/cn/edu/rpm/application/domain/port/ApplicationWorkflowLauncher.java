package cn.edu.rpm.application.domain.port;

import java.util.Optional;
import java.util.UUID;

/**
 * 申报审批启动端口（T-APP-014；技术文档 04 §3.2 步骤 6）。
 *
 * <p>应用服务不直接依赖 Flowable 或模板路由细节：本端口负责「按项目类型选择
 * 审批模板版本 + 启动实例」，并在幂等回放时返回首次实例。实现位于
 * {@code rpm-application/infrastructure/adapter}，内部只调用
 * {@code rpm-workflow} 的 {@code WorkflowPort} 与 {@code WorkflowTemplateResolver}。</p>
 */
public interface ApplicationWorkflowLauncher {

    /**
     * 启动申报审批实例。
     *
     * <p>同一业务版本已有运行中实例时由 {@code uk_wf_instance_running} 拒绝重复启动；
     * 模板缺失 {@code RPM-WF-001}、模板条件冲突 {@code RPM-SYS-001} 由路由服务抛出。</p>
     *
     * @param command 启动命令
     * @return 审批实例 ID 与状态（RUNNING）
     */
    WorkflowLaunch start(ApplicationWorkflowStart command);

    /**
     * 查询申报版本对应的最近审批实例（幂等回放取首次 {@code workflow_instance_id}）。
     *
     * @param applicationId 申报 ID
     * @param versionId     申报版本 ID
     * @return 审批实例；尚未启动返回空 {@link Optional}
     */
    Optional<WorkflowLaunch> findLatest(UUID applicationId, UUID versionId);
}
