package cn.edu.rpm.project.infrastructure.repository;

import cn.edu.rpm.project.infrastructure.entity.ProjectHandoverEntity;
import cn.edu.rpm.project.infrastructure.mapper.ProjectHandoverMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * {@code project_handover} 负责人交接仓储实现（T-PRJ-009）。
 */
@Repository
public class ProjectHandoverRepositoryImpl implements ProjectHandoverRepository {

    private final ProjectHandoverMapper projectHandoverMapper;

    public ProjectHandoverRepositoryImpl(ProjectHandoverMapper projectHandoverMapper) {
        this.projectHandoverMapper = projectHandoverMapper;
    }

    @Override
    public void insert(ProjectHandoverEntity handover) {
        projectHandoverMapper.insert(handover);
    }

    @Override
    public Optional<ProjectHandoverEntity> findById(String id) {
        return Optional.ofNullable(projectHandoverMapper.selectById(id));
    }

    @Override
    public List<ProjectHandoverEntity> findByProjectIdAndStatus(String projectId, String status) {
        return projectHandoverMapper.selectList(new LambdaQueryWrapper<ProjectHandoverEntity>()
                .eq(ProjectHandoverEntity::getProjectId, projectId)
                .eq(ProjectHandoverEntity::getStatus, status)
                .orderByDesc(ProjectHandoverEntity::getCreatedAt));
    }

    @Override
    public List<ProjectHandoverEntity> findByStatusAndEffectiveAtBefore(
            String status, java.time.OffsetDateTime effectiveAt) {
        return projectHandoverMapper.selectList(new LambdaQueryWrapper<ProjectHandoverEntity>()
                .eq(ProjectHandoverEntity::getStatus, status)
                .le(ProjectHandoverEntity::getEffectiveAt, effectiveAt)
                .orderByAsc(ProjectHandoverEntity::getEffectiveAt));
    }

    @Override
    public int update(ProjectHandoverEntity handover) {
        return projectHandoverMapper.updateById(handover);
    }
}
