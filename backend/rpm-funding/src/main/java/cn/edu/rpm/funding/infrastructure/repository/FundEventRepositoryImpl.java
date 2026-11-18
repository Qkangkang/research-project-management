package cn.edu.rpm.funding.infrastructure.repository;

import cn.edu.rpm.funding.infrastructure.entity.FundEventEntity;
import cn.edu.rpm.funding.infrastructure.mapper.FundEventMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * {@code fund_event} 资金事件仓储实现（T-FUND-006）。
 */
@Repository
public class FundEventRepositoryImpl implements FundEventRepository {

    private final FundEventMapper fundEventMapper;

    public FundEventRepositoryImpl(FundEventMapper fundEventMapper) {
        this.fundEventMapper = fundEventMapper;
    }

    @Override
    public void insert(FundEventEntity event) {
        fundEventMapper.insert(event);
    }

    @Override
    public Optional<FundEventEntity> findById(String id) {
        return Optional.ofNullable(fundEventMapper.selectById(id));
    }

    @Override
    public Optional<FundEventEntity> findByIdempotencyKey(String idempotencyKey) {
        return Optional.ofNullable(fundEventMapper.selectOne(
                new LambdaQueryWrapper<FundEventEntity>()
                        .eq(FundEventEntity::getIdempotencyKey, idempotencyKey)));
    }

    @Override
    public List<FundEventEntity> findByProjectIdAndEventType(String projectId, String eventType) {
        return fundEventMapper.selectList(new LambdaQueryWrapper<FundEventEntity>()
                .eq(FundEventEntity::getProjectId, projectId)
                .eq(FundEventEntity::getEventType, eventType)
                .orderByAsc(FundEventEntity::getOccurredAt));
    }

    @Override
    public List<FundEventEntity> findByProjectId(String projectId) {
        return fundEventMapper.selectList(new LambdaQueryWrapper<FundEventEntity>()
                .eq(FundEventEntity::getProjectId, projectId)
                .orderByAsc(FundEventEntity::getOccurredAt));
    }
}
