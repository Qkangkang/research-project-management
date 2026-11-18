package cn.edu.rpm.project.infrastructure.repository;

import cn.edu.rpm.project.infrastructure.entity.MilestoneEntity;
import cn.edu.rpm.project.infrastructure.mapper.MilestoneMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * {@code milestone} 里程碑仓储实现（T-PRJ-004）。
 *
 * <p>批量插入按 500 条分批（RULE-DB3）；批次极小（立项约 6 条），不引入额外依赖。</p>
 */
@Repository
public class MilestoneRepositoryImpl implements MilestoneRepository {

    private static final int BATCH_SIZE = 500;

    private final MilestoneMapper milestoneMapper;

    public MilestoneRepositoryImpl(MilestoneMapper milestoneMapper) {
        this.milestoneMapper = milestoneMapper;
    }

    @Override
    public void insert(MilestoneEntity milestone) {
        milestoneMapper.insert(milestone);
    }

    @Override
    public void insertBatch(List<MilestoneEntity> milestones) {
        if (milestones == null || milestones.isEmpty()) {
            return;
        }
        for (int start = 0; start < milestones.size(); start += BATCH_SIZE) {
            int end = Math.min(start + BATCH_SIZE, milestones.size());
            for (MilestoneEntity milestone : milestones.subList(start, end)) {
                milestoneMapper.insert(milestone);
            }
        }
    }

    @Override
    public Optional<MilestoneEntity> findById(String id) {
        return Optional.ofNullable(milestoneMapper.selectById(id));
    }

    @Override
    public List<MilestoneEntity> findByProjectIdAndBaselineId(String projectId, String baselineId) {
        return milestoneMapper.selectList(new LambdaQueryWrapper<MilestoneEntity>()
                .eq(MilestoneEntity::getProjectId, projectId)
                .eq(MilestoneEntity::getBaselineId, baselineId)
                .orderByAsc(MilestoneEntity::getPlanDate));
    }

    @Override
    public List<MilestoneEntity> findByOwnerIdAndStatus(String ownerId, String status) {
        return milestoneMapper.selectList(new LambdaQueryWrapper<MilestoneEntity>()
                .eq(MilestoneEntity::getOwnerId, ownerId)
                .eq(MilestoneEntity::getStatus, status)
                .orderByAsc(MilestoneEntity::getCurrentPlanDate));
    }

    @Override
    public List<MilestoneEntity> findUnfinishedDueBy(String projectId, LocalDate date) {
        return milestoneMapper.selectList(new LambdaQueryWrapper<MilestoneEntity>()
                .eq(MilestoneEntity::getProjectId, projectId)
                .le(MilestoneEntity::getCurrentPlanDate, date)
                .notIn(MilestoneEntity::getStatus,
                        MilestoneEntity.STATUS_COMPLETED, MilestoneEntity.STATUS_CANCELLED)
                .orderByAsc(MilestoneEntity::getCurrentPlanDate));
    }

    @Override
    public Optional<MilestoneEntity> selectForUpdate(String id) {
        return Optional.ofNullable(milestoneMapper.selectByIdForUpdate(id));
    }

    @Override
    public int update(MilestoneEntity milestone) {
        return milestoneMapper.updateById(milestone);
    }
}
