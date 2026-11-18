package cn.edu.rpm.execution.infrastructure.repository;

import cn.edu.rpm.execution.infrastructure.entity.ProjectTaskEntity;

import java.util.List;
import java.util.Optional;

/**
 * {@code project_task} 轻量任务仓储（T-EXEC-004）。
 */
public interface ProjectTaskRepository {

    /**
     * 插入任务。
     *
     * @param task 待插入的任务 PO
     */
    void insert(ProjectTaskEntity task);

    /**
     * 按主键查询任务。
     *
     * @param id 任务 ID
     * @return 命中的任务；不存在返回空
     */
    Optional<ProjectTaskEntity> findById(String id);

    /**
     * 按项目查询全部任务。
     *
     * @param projectId 项目 ID
     * @return 任务列表（按截止日期升序）；无命中返回空列表
     */
    List<ProjectTaskEntity> findByProjectId(String projectId);

    /**
     * 按项目与状态查询任务。
     *
     * @param projectId 项目 ID
     * @param status    任务状态
     * @return 任务列表；无命中返回空列表
     */
    List<ProjectTaskEntity> findByProjectIdAndStatus(String projectId, String status);

    /**
     * 按处理人与状态查询待办任务。
     *
     * @param assigneeId 处理人用户 ID
     * @param status     任务状态
     * @return 任务列表；无命中返回空列表
     */
    List<ProjectTaskEntity> findByAssigneeIdAndStatus(String assigneeId, String status);

    /**
     * 按乐观锁更新任务。
     *
     * @param task 携带主键与旧版本的任务 PO
     * @return 受影响行数；0 表示版本冲突或记录不存在
     */
    int update(ProjectTaskEntity task);
}
