package cn.edu.rpm.project.infrastructure.repository;

import cn.edu.rpm.project.infrastructure.entity.ProjectBaselineEntity;
import cn.edu.rpm.project.infrastructure.mapper.ProjectBaselineMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * {@code project_baseline} 项目基线仓储实现（T-PRJ-003）。
 */
@Repository
public class ProjectBaselineRepositoryImpl implements ProjectBaselineRepository {

    private final ProjectBaselineMapper projectBaselineMapper;

    public ProjectBaselineRepositoryImpl(ProjectBaselineMapper projectBaselineMapper) {
        this.projectBaselineMapper = projectBaselineMapper;
    }

    @Override
    public void insert(ProjectBaselineEntity baseline) {
        projectBaselineMapper.insert(baseline);
    }

    @Override
    public Optional<ProjectBaselineEntity> findById(String id) {
        return Optional.ofNullable(projectBaselineMapper.selectById(id));
    }

    @Override
    public Optional<ProjectBaselineEntity> findByProjectIdAndVersionNo(String projectId, int versionNo) {
        return Optional.ofNullable(projectBaselineMapper.selectOne(
                new LambdaQueryWrapper<ProjectBaselineEntity>()
                        .eq(ProjectBaselineEntity::getProjectId, projectId)
                        .eq(ProjectBaselineEntity::getVersionNo, versionNo)));
    }

    @Override
    public Optional<ProjectBaselineEntity> findEffectiveByProjectId(String projectId) {
        return projectBaselineMapper.selectList(new LambdaQueryWrapper<ProjectBaselineEntity>()
                        .eq(ProjectBaselineEntity::getProjectId, projectId)
                        .eq(ProjectBaselineEntity::getStatus, ProjectBaselineEntity.STATUS_EFFECTIVE)
                        .orderByDesc(ProjectBaselineEntity::getVersionNo))
                .stream()
                .findFirst();
    }

    @Override
    public int findMaxVersionNo(String projectId) {
        return projectBaselineMapper.selectList(new LambdaQueryWrapper<ProjectBaselineEntity>()
                        .select(ProjectBaselineEntity::getVersionNo)
                        .eq(ProjectBaselineEntity::getProjectId, projectId))
                .stream()
                .map(ProjectBaselineEntity::getVersionNo)
                .filter(java.util.Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(0);
    }

    @Override
    public int update(ProjectBaselineEntity baseline) {
        return projectBaselineMapper.updateById(baseline);
    }
}
