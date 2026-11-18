package cn.edu.rpm.project.infrastructure.repository;

import cn.edu.rpm.project.infrastructure.entity.ProjectRiskEntity;
import cn.edu.rpm.project.infrastructure.mapper.ProjectRiskMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * {@code project_risk} 项目风险仓储实现（T-PRJ-006）。
 */
@Repository
public class ProjectRiskRepositoryImpl implements ProjectRiskRepository {

    private final ProjectRiskMapper projectRiskMapper;

    public ProjectRiskRepositoryImpl(ProjectRiskMapper projectRiskMapper) {
        this.projectRiskMapper = projectRiskMapper;
    }

    @Override
    public void insert(ProjectRiskEntity risk) {
        projectRiskMapper.insert(risk);
    }

    @Override
    public Optional<ProjectRiskEntity> findById(String id) {
        return Optional.ofNullable(projectRiskMapper.selectById(id));
    }

    @Override
    public List<ProjectRiskEntity> findByProjectIdAndStatus(String projectId, String status) {
        return projectRiskMapper.selectList(new LambdaQueryWrapper<ProjectRiskEntity>()
                .eq(ProjectRiskEntity::getProjectId, projectId)
                .eq(ProjectRiskEntity::getStatus, status)
                .orderByAsc(ProjectRiskEntity::getDueDate));
    }

    @Override
    public List<ProjectRiskEntity> findOpenByProjectId(String projectId) {
        return projectRiskMapper.selectList(new LambdaQueryWrapper<ProjectRiskEntity>()
                .eq(ProjectRiskEntity::getProjectId, projectId)
                .in(ProjectRiskEntity::getStatus, ProjectRiskEntity.STATUS_OPEN,
                        ProjectRiskEntity.STATUS_PROCESSING, ProjectRiskEntity.STATUS_ESCALATED)
                .orderByAsc(ProjectRiskEntity::getDueDate));
    }

    @Override
    public List<ProjectRiskEntity> findByProjectId(String projectId) {
        return projectRiskMapper.selectList(new LambdaQueryWrapper<ProjectRiskEntity>()
                .eq(ProjectRiskEntity::getProjectId, projectId)
                .orderByDesc(ProjectRiskEntity::getCreatedAt));
    }

    @Override
    public int transferOwner(String projectId, String fromUserId, String toUserId) {
        return projectRiskMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ProjectRiskEntity>()
                        .eq(ProjectRiskEntity::getProjectId, projectId)
                        .eq(ProjectRiskEntity::getOwnerId, fromUserId)
                        .in(ProjectRiskEntity::getStatus, ProjectRiskEntity.STATUS_OPEN,
                                ProjectRiskEntity.STATUS_PROCESSING, ProjectRiskEntity.STATUS_ESCALATED)
                        .set(ProjectRiskEntity::getOwnerId, toUserId));
    }

    @Override
    public List<ProjectRiskEntity> findOverdueByOwner(String ownerId, LocalDate date) {
        return projectRiskMapper.selectList(new LambdaQueryWrapper<ProjectRiskEntity>()
                .eq(ProjectRiskEntity::getOwnerId, ownerId)
                .le(ProjectRiskEntity::getDueDate, date)
                .in(ProjectRiskEntity::getStatus, ProjectRiskEntity.STATUS_OPEN,
                        ProjectRiskEntity.STATUS_PROCESSING)
                .orderByAsc(ProjectRiskEntity::getDueDate));
    }

    @Override
    public List<ProjectRiskEntity> findOpenDueBy(LocalDate date) {
        return projectRiskMapper.selectList(new LambdaQueryWrapper<ProjectRiskEntity>()
                .le(ProjectRiskEntity::getDueDate, date)
                .in(ProjectRiskEntity::getStatus, ProjectRiskEntity.STATUS_OPEN,
                        ProjectRiskEntity.STATUS_PROCESSING)
                .orderByAsc(ProjectRiskEntity::getDueDate));
    }

    @Override
    public Optional<ProjectRiskEntity> findBySourceRule(String sourceRule) {
        return Optional.ofNullable(projectRiskMapper.selectOne(
                new LambdaQueryWrapper<ProjectRiskEntity>()
                        .eq(ProjectRiskEntity::getSourceRule, sourceRule)));
    }

    @Override
    public int update(ProjectRiskEntity risk) {
        return projectRiskMapper.updateById(risk);
    }
}
