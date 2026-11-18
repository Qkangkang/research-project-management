package cn.edu.rpm.project.domain.port;

/**
 * 交接时待办/任务转移端口（T-PRJ-022；技术文档 04 §3.10）。
 *
 * <p>由持有 {@code wf_task}/{@code todo_item} 的模块实现；应用服务按
 * {@code List<HandoverTaskTransferPort>} 合并调用，增加来源无需修改交接逻辑。</p>
 */
public interface HandoverTaskTransferPort {

    /**
     * 将项目下原负责人待处理事项转移给新负责人。
     *
     * @param projectId  项目 ID
     * @param fromUserId 原负责人用户 ID
     * @param toUserId   新负责人用户 ID
     */
    void transferTasksAndTodos(String projectId, String fromUserId, String toUserId);
}
