package cn.edu.rpm.execution.infrastructure.repository;

import cn.edu.rpm.execution.infrastructure.entity.ProjectTaskEntity;
import cn.edu.rpm.execution.infrastructure.mapper.ProjectTaskMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * {@code project_task} 轻量任务仓储实现（T-EXEC-004）。
 */
@Repository
public class ProjectTaskRepositoryImpl implements ProjectTaskRepository {

    private final ProjectTaskMapper projectTaskMapper;

    public ProjectTaskRepositoryImpl(ProjectTaskMapper projectTaskMapper) {
        this.projectTaskMapper = projectTaskMapper;
    }

    @Override
    public void insert(ProjectTaskEntity task) {
        projectTaskMapper.insert(task);
    }

    @Override
    public Optional<ProjectTaskEntity> findById(String id) {
        return Optional.ofNullable(projectTaskMapper.selectById(id));
    }

    @Override
    public List<ProjectTaskEntity> findByProjectId(String projectId) {
        return projectTaskMapper.selectList(new LambdaQueryWrapper<ProjectTaskEntity>()
                .eq(ProjectTaskEntity::getProjectId, projectId)
                .orderByAsc(ProjectTaskEntity::getDueDate));
    }

    @Override
    public List<ProjectTaskEntity> findByProjectIdAndStatus(String projectId, String status) {
        return projectTaskMapper.selectList(new LambdaQueryWrapper<ProjectTaskEntity>()
                .eq(ProjectTaskEntity::getProjectId, projectId)
                .eq(ProjectTaskEntity::getStatus, status)
                .orderByAsc(ProjectTaskEntity::getDueDate));
    }

    @Override
    public List<ProjectTaskEntity> findByAssigneeIdAndStatus(String assigneeId, String status) {
        return projectTaskMapper.selectList(new LambdaQueryWrapper<ProjectTaskEntity>()
                .eq(ProjectTaskEntity::getAssigneeId, assigneeId)
                .eq(ProjectTaskEntity::getStatus, status)
                .orderByAsc(ProjectTaskEntity::getDueDate));
    }

    @Override
    public int update(ProjectTaskEntity task) {
        return projectTaskMapper.updateById(task);
    }
}
