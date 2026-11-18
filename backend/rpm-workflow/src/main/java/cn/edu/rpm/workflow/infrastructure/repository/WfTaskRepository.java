package cn.edu.rpm.workflow.infrastructure.repository;

import cn.edu.rpm.workflow.infrastructure.entity.WfTaskEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * {@code wf_task} 审批任务仓储（T-WF-005）。
 *
 * <p>待办查询走 idx_wf_task_assignee_status (assignee_id, status, due_at)；
 * 任务完成使用 {@code version} 乐观锁，同一任务只能被一个处理人完成。</p>
 */
public interface WfTaskRepository {

    /**
     * 插入审批任务。
     *
     * @param task 待插入的任务 PO
     */
    void insert(WfTaskEntity task);

    /**
     * 按主键查询任务。
     *
     * @param id 任务 ID
     * @return 任务；不存在返回 {@link Optional#empty()}
     */
    Optional<WfTaskEntity> findById(String id);

    /**
     * 查询指定处理人的待办任务（status = PENDING），按处理时限升序。
     *
     * @param assigneeId 处理人用户 ID
     * @return 待办任务列表；无命中返回空列表
     */
    List<WfTaskEntity> findPendingByAssignee(String assigneeId);

    /**
     * 查询指定审批实例下的全部任务（审批轨迹），按轮次与创建时间升序。
     *
     * @param instanceId 审批实例 ID
     * @return 任务列表；无命中返回空列表
     */
    List<WfTaskEntity> findByInstance(String instanceId);

    /**
     * 按处理请求幂等键查询任务（uk_wf_task_action_request）。
     *
     * <p>同一 request_id 重复提交时返回首次处理结果（TC-APP-023）。</p>
     *
     * @param actionRequestId 处理请求幂等键
     * @return 任务；无命中返回 {@link Optional#empty()}
     */
    Optional<WfTaskEntity> findByActionRequestId(String actionRequestId);

    /**
     * 更新任务（乐观锁：携带旧 {@code version}）。
     *
     * @param task 待更新任务（version 为读取到的旧值）
     * @return 受影响行数；0 表示已被他人处理（映射 RPM-WF-004）或记录不存在
     */
    int update(WfTaskEntity task);

    /**
     * 分页查询指定处理人的任务（联表 {@code wf_instance} 带出业务类型/业务 ID）。
     *
     * <p>排序 {@code due_at ASC NULLS LAST, created_at ASC}；{@code bizType} 为空时
     * 不过滤。分页大小由调用方在 1～100 内约束。</p>
     *
     * @param assigneeId 处理人用户 ID（32 位无连字符文本）
     * @param status     任务状态
     * @param bizType    业务类型；为空不过滤
     * @param offset     偏移量（从 0 开始）
     * @param limit      每页条数
     * @return 任务投影列表；无命中返回空列表
     */
    List<WorkflowTaskRow> findTaskPage(String assigneeId, String status, String bizType,
                                       long offset, int limit);

    /**
     * 统计指定处理人的任务总数（分页 total）。
     *
     * @param assigneeId 处理人用户 ID
     * @param status     任务状态
     * @param bizType    业务类型；为空不过滤
     * @return 总记录数
     */
    long countTasks(String assigneeId, String status, String bizType);

    /**
     * 取消实例下全部未处理任务（PENDING / PROCESSING → CANCELLED）。
     *
     * <p>逐条 {@code updateById} 并携带乐观锁版本：任务数按单实例待办量级有限，
     * 避免批量 SQL 绕过版本控制；并发完成的单条任务更新返回 0 属于正常竞争，
     * 不计入取消数量。</p>
     *
     * @param instanceId 审批实例 ID
     * @param at         取消时刻
     * @return 实际取消的任务数
     */
    int cancelPendingTasks(String instanceId, OffsetDateTime at);
}
