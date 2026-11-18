package cn.edu.rpm.workflow.infrastructure.repository;

import cn.edu.rpm.workflow.infrastructure.entity.WfTaskEntity;
import cn.edu.rpm.workflow.infrastructure.mapper.WfTaskMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * {@code wf_task} 审批任务仓储实现（T-WF-005）。
 *
 * <p>待办按 {@code due_at} 升序（与 idx_wf_task_assignee_status 对齐）；
 * 任务更新走 {@code updateById}，乐观锁拦截器自动追加 {@code version} 条件，
 * 返回 0 表示已被他人处理（TC-APP-024）。</p>
 */
@Repository
public class WfTaskRepositoryImpl implements WfTaskRepository {

    private final WfTaskMapper wfTaskMapper;

    public WfTaskRepositoryImpl(WfTaskMapper wfTaskMapper) {
        this.wfTaskMapper = wfTaskMapper;
    }

    @Override
    public void insert(WfTaskEntity task) {
        wfTaskMapper.insert(task);
    }

    @Override
    public Optional<WfTaskEntity> findById(String id) {
        return Optional.ofNullable(wfTaskMapper.selectById(id));
    }

    @Override
    public List<WfTaskEntity> findPendingByAssignee(String assigneeId) {
        return wfTaskMapper.selectList(new LambdaQueryWrapper<WfTaskEntity>()
                .eq(WfTaskEntity::getAssigneeId, assigneeId)
                .eq(WfTaskEntity::getStatus, WfTaskEntity.STATUS_PENDING)
                .orderByAsc(WfTaskEntity::getDueAt));
    }

    @Override
    public List<WfTaskEntity> findByInstance(String instanceId) {
        return wfTaskMapper.selectList(new LambdaQueryWrapper<WfTaskEntity>()
                .eq(WfTaskEntity::getInstanceId, instanceId)
                .orderByAsc(WfTaskEntity::getRoundNo)
                .orderByAsc(WfTaskEntity::getCreatedAt));
    }

    @Override
    public Optional<WfTaskEntity> findByActionRequestId(String actionRequestId) {
        return Optional.ofNullable(wfTaskMapper.selectOne(
                new LambdaQueryWrapper<WfTaskEntity>()
                        .eq(WfTaskEntity::getActionRequestId, actionRequestId)));
    }

    @Override
    public int update(WfTaskEntity task) {
        return wfTaskMapper.updateById(task);
    }

    @Override
    public List<WorkflowTaskRow> findTaskPage(String assigneeId, String status, String bizType,
                                              long offset, int limit) {
        return wfTaskMapper.selectTaskPage(assigneeId, status, bizType, offset, limit);
    }

    @Override
    public long countTasks(String assigneeId, String status, String bizType) {
        return wfTaskMapper.countTaskPage(assigneeId, status, bizType);
    }

    @Override
    public int cancelPendingTasks(String instanceId, OffsetDateTime at) {
        List<WfTaskEntity> openTasks = wfTaskMapper.selectList(new LambdaQueryWrapper<WfTaskEntity>()
                .eq(WfTaskEntity::getInstanceId, instanceId)
                .in(WfTaskEntity::getStatus, WfTaskEntity.STATUS_PENDING, WfTaskEntity.STATUS_PROCESSING));
        int cancelled = 0;
        for (WfTaskEntity task : openTasks) {
            task.cancel(at);
            cancelled += wfTaskMapper.updateById(task);
        }
        return cancelled;
    }
}
