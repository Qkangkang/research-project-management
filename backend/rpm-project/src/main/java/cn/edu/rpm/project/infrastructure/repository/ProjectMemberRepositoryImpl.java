package cn.edu.rpm.project.infrastructure.repository;

import cn.edu.rpm.project.infrastructure.entity.ProjectMemberEntity;
import cn.edu.rpm.project.infrastructure.mapper.ProjectMemberMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * {@code project_member} 项目成员仓储实现（T-PRJ-002）。
 */
@Repository
public class ProjectMemberRepositoryImpl implements ProjectMemberRepository {

    private final ProjectMemberMapper projectMemberMapper;

    public ProjectMemberRepositoryImpl(ProjectMemberMapper projectMemberMapper) {
        this.projectMemberMapper = projectMemberMapper;
    }

    @Override
    public void insert(ProjectMemberEntity member) {
        projectMemberMapper.insert(member);
    }

    @Override
    public Optional<ProjectMemberEntity> findById(String id) {
        return Optional.ofNullable(projectMemberMapper.selectById(id));
    }

    @Override
    public List<ProjectMemberEntity> findByProjectId(String projectId) {
        return projectMemberMapper.selectList(new LambdaQueryWrapper<ProjectMemberEntity>()
                .eq(ProjectMemberEntity::getProjectId, projectId)
                .orderByDesc(ProjectMemberEntity::getValidFrom));
    }

    @Override
    public List<ProjectMemberEntity> findActiveByProjectId(String projectId, OffsetDateTime at) {
        return projectMemberMapper.selectList(activeWrapper(projectId, at));
    }

    @Override
    public boolean existsActiveMember(String projectId, String userId, String projectRole,
                                      OffsetDateTime at) {
        return projectMemberMapper.selectCount(activeWrapper(projectId, at)
                .eq(ProjectMemberEntity::getUserId, userId)
                .eq(ProjectMemberEntity::getProjectRole, projectRole)) > 0;
    }

    @Override
    public List<String> findActiveProjectIdsByUserId(String userId, OffsetDateTime at) {
        return projectMemberMapper.selectList(new LambdaQueryWrapper<ProjectMemberEntity>()
                        .eq(ProjectMemberEntity::getUserId, userId)
                        .eq(ProjectMemberEntity::getStatus, ProjectMemberEntity.STATUS_ACTIVE)
                        .le(ProjectMemberEntity::getValidFrom, at)
                        .and(wrapper -> wrapper.isNull(ProjectMemberEntity::getValidTo)
                                .or().gt(ProjectMemberEntity::getValidTo, at)))
                .stream()
                .map(ProjectMemberEntity::getProjectId)
                .distinct()
                .toList();
    }

    @Override
    public int update(ProjectMemberEntity member) {
        return projectMemberMapper.updateById(member);
    }

    private static LambdaQueryWrapper<ProjectMemberEntity> activeWrapper(String projectId,
                                                                         OffsetDateTime at) {
        return new LambdaQueryWrapper<ProjectMemberEntity>()
                .eq(ProjectMemberEntity::getProjectId, projectId)
                .eq(ProjectMemberEntity::getStatus, ProjectMemberEntity.STATUS_ACTIVE)
                .le(ProjectMemberEntity::getValidFrom, at)
                .and(wrapper -> wrapper.isNull(ProjectMemberEntity::getValidTo)
                        .or().gt(ProjectMemberEntity::getValidTo, at))
                .orderByAsc(ProjectMemberEntity::getValidFrom);
    }
}
