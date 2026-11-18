package cn.edu.rpm.project.infrastructure.repository;

import cn.edu.rpm.project.infrastructure.entity.ChangeRequestEntity;
import cn.edu.rpm.project.infrastructure.mapper.ChangeRequestMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * {@code change_request} 变更主档仓储实现（T-PRJ-007）。
 */
@Repository
public class ChangeRequestRepositoryImpl implements ChangeRequestRepository {

    private final ChangeRequestMapper changeRequestMapper;

    public ChangeRequestRepositoryImpl(ChangeRequestMapper changeRequestMapper) {
        this.changeRequestMapper = changeRequestMapper;
    }

    @Override
    public void insert(ChangeRequestEntity change) {
        changeRequestMapper.insert(change);
    }

    @Override
    public Optional<ChangeRequestEntity> findById(String id) {
        return Optional.ofNullable(changeRequestMapper.selectById(id));
    }

    @Override
    public Optional<ChangeRequestEntity> selectForUpdate(String id) {
        return Optional.ofNullable(changeRequestMapper.selectByIdForUpdate(id));
    }

    @Override
    public List<ChangeRequestEntity> findByProjectIdAndStatus(String projectId, String status) {
        return changeRequestMapper.selectList(new LambdaQueryWrapper<ChangeRequestEntity>()
                .eq(ChangeRequestEntity::getProjectId, projectId)
                .eq(ChangeRequestEntity::getStatus, status)
                .orderByDesc(ChangeRequestEntity::getCreatedAt));
    }

    @Override
    public int update(ChangeRequestEntity change) {
        return changeRequestMapper.updateById(change);
    }
}
