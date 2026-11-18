package cn.edu.rpm.project.infrastructure.repository;

import cn.edu.rpm.project.infrastructure.entity.MilestoneDelayRequestEntity;
import cn.edu.rpm.project.infrastructure.mapper.MilestoneDelayRequestMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * {@code milestone_delay_request} 延期申请仓储实现（T-PRJ-005）。
 */
@Repository
public class MilestoneDelayRequestRepositoryImpl implements MilestoneDelayRequestRepository {

    private final MilestoneDelayRequestMapper milestoneDelayRequestMapper;

    public MilestoneDelayRequestRepositoryImpl(MilestoneDelayRequestMapper milestoneDelayRequestMapper) {
        this.milestoneDelayRequestMapper = milestoneDelayRequestMapper;
    }

    @Override
    public void insert(MilestoneDelayRequestEntity request) {
        milestoneDelayRequestMapper.insert(request);
    }

    @Override
    public Optional<MilestoneDelayRequestEntity> findById(String id) {
        return Optional.ofNullable(milestoneDelayRequestMapper.selectById(id));
    }

    @Override
    public Optional<MilestoneDelayRequestEntity> findPendingByMilestoneId(String milestoneId) {
        return milestoneDelayRequestMapper.selectList(new LambdaQueryWrapper<MilestoneDelayRequestEntity>()
                        .eq(MilestoneDelayRequestEntity::getMilestoneId, milestoneId)
                        .eq(MilestoneDelayRequestEntity::getStatus, MilestoneDelayRequestEntity.STATUS_PENDING)
                        .orderByDesc(MilestoneDelayRequestEntity::getCreatedAt))
                .stream()
                .findFirst();
    }

    @Override
    public List<MilestoneDelayRequestEntity> findByMilestoneId(String milestoneId) {
        return milestoneDelayRequestMapper.selectList(
                new LambdaQueryWrapper<MilestoneDelayRequestEntity>()
                        .eq(MilestoneDelayRequestEntity::getMilestoneId, milestoneId)
                        .orderByDesc(MilestoneDelayRequestEntity::getCreatedAt));
    }

    @Override
    public int update(MilestoneDelayRequestEntity request) {
        return milestoneDelayRequestMapper.updateById(request);
    }
}
