package cn.edu.rpm.execution.infrastructure.repository;

import cn.edu.rpm.execution.infrastructure.entity.DeliverableEntity;
import cn.edu.rpm.execution.infrastructure.mapper.DeliverableMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * {@code deliverable} 交付物仓储实现（T-EXEC-003）。
 */
@Repository
public class DeliverableRepositoryImpl implements DeliverableRepository {

    private final DeliverableMapper deliverableMapper;

    public DeliverableRepositoryImpl(DeliverableMapper deliverableMapper) {
        this.deliverableMapper = deliverableMapper;
    }

    @Override
    public void insert(DeliverableEntity deliverable) {
        deliverableMapper.insert(deliverable);
    }

    @Override
    public Optional<DeliverableEntity> findById(String id) {
        return Optional.ofNullable(deliverableMapper.selectById(id));
    }

    @Override
    public List<DeliverableEntity> findByProjectId(String projectId) {
        return deliverableMapper.selectList(new LambdaQueryWrapper<DeliverableEntity>()
                .eq(DeliverableEntity::getProjectId, projectId)
                .orderByDesc(DeliverableEntity::getCreatedAt));
    }

    @Override
    public List<DeliverableEntity> findByMilestoneId(String milestoneId) {
        return deliverableMapper.selectList(new LambdaQueryWrapper<DeliverableEntity>()
                .eq(DeliverableEntity::getMilestoneId, milestoneId)
                .orderByDesc(DeliverableEntity::getCreatedAt));
    }

    @Override
    public boolean existsActiveByMilestoneId(String projectId, String milestoneId) {
        return deliverableMapper.selectCount(new LambdaQueryWrapper<DeliverableEntity>()
                .eq(DeliverableEntity::getProjectId, projectId)
                .eq(DeliverableEntity::getMilestoneId, milestoneId)
                .eq(DeliverableEntity::getStatus, DeliverableEntity.STATUS_ACTIVE)) > 0;
    }

    @Override
    public int update(DeliverableEntity deliverable) {
        return deliverableMapper.updateById(deliverable);
    }
}
