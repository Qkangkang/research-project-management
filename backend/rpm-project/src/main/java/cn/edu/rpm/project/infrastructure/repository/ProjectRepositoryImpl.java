package cn.edu.rpm.project.infrastructure.repository;

import cn.edu.rpm.project.infrastructure.entity.ProjectEntity;
import cn.edu.rpm.project.infrastructure.mapper.ProjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * {@code project} 项目主档仓储实现（T-PRJ-001）。
 *
 * <p>基于 MyBatis-Plus 的薄委托层；条件查询按实体注解生成，悲观锁走 Mapper XML。</p>
 */
@Repository
public class ProjectRepositoryImpl implements ProjectRepository {

    private final ProjectMapper projectMapper;

    public ProjectRepositoryImpl(ProjectMapper projectMapper) {
        this.projectMapper = projectMapper;
    }

    @Override
    public void insert(ProjectEntity project) {
        projectMapper.insert(project);
    }

    @Override
    public Optional<ProjectEntity> findById(String id) {
        return Optional.ofNullable(projectMapper.selectById(id));
    }

    @Override
    public Optional<ProjectEntity> findByProjectNo(String projectNo) {
        return Optional.ofNullable(projectMapper.selectOne(
                new LambdaQueryWrapper<ProjectEntity>()
                        .eq(ProjectEntity::getProjectNo, projectNo)));
    }

    @Override
    public Optional<ProjectEntity> findBySourceApplicationVersionId(String sourceApplicationVersionId) {
        return Optional.ofNullable(projectMapper.selectOne(
                new LambdaQueryWrapper<ProjectEntity>()
                        .eq(ProjectEntity::getSourceApplicationVersionId, sourceApplicationVersionId)));
    }

    @Override
    public boolean existsByProjectNo(String projectNo) {
        return projectMapper.selectCount(new LambdaQueryWrapper<ProjectEntity>()
                .eq(ProjectEntity::getProjectNo, projectNo)) > 0;
    }

    @Override
    public List<ProjectEntity> findByPiIdAndStatus(String piId, String status) {
        return projectMapper.selectList(new LambdaQueryWrapper<ProjectEntity>()
                .eq(ProjectEntity::getPiId, piId)
                .eq(ProjectEntity::getStatus, status)
                .orderByDesc(ProjectEntity::getUpdatedAt));
    }

    @Override
    public List<ProjectEntity> findByDeptIdAndStatus(String deptId, String status) {
        return projectMapper.selectList(new LambdaQueryWrapper<ProjectEntity>()
                .eq(ProjectEntity::getDeptId, deptId)
                .eq(ProjectEntity::getStatus, status)
                .orderByDesc(ProjectEntity::getUpdatedAt));
    }

    @Override
    public List<ProjectEntity> findByProjectTypeAndStatus(String projectType, String status) {
        return projectMapper.selectList(new LambdaQueryWrapper<ProjectEntity>()
                .eq(ProjectEntity::getProjectType, projectType)
                .eq(ProjectEntity::getStatus, status)
                .orderByDesc(ProjectEntity::getUpdatedAt));
    }

    @Override
    public Optional<ProjectEntity> selectForUpdate(String id) {
        return Optional.ofNullable(projectMapper.selectByIdForUpdate(id));
    }

    @Override
    public int update(ProjectEntity project) {
        return projectMapper.updateById(project);
    }
}
