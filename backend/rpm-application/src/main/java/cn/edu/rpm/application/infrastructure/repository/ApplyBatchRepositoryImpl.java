package cn.edu.rpm.application.infrastructure.repository;

import cn.edu.rpm.application.infrastructure.entity.ApplyBatchEntity;
import cn.edu.rpm.application.infrastructure.mapper.ApplyBatchMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * {@code apply_batch} 申报批次仓储实现（T-APP-001）。
 *
 * <p>基于 MyBatis-Plus {@link ApplyBatchMapper} 的薄委托层；查询条件以
 * {@link LambdaQueryWrapper} 按实体注解生成，无手写 SQL。</p>
 */
@Repository
public class ApplyBatchRepositoryImpl implements ApplyBatchRepository {

    private final ApplyBatchMapper applyBatchMapper;

    public ApplyBatchRepositoryImpl(ApplyBatchMapper applyBatchMapper) {
        this.applyBatchMapper = applyBatchMapper;
    }

    @Override
    public void insert(ApplyBatchEntity batch) {
        applyBatchMapper.insert(batch);
    }

    @Override
    public Optional<ApplyBatchEntity> findById(String id) {
        return Optional.ofNullable(applyBatchMapper.selectById(id));
    }

    @Override
    public Optional<ApplyBatchEntity> findByBatchNo(String batchNo) {
        return Optional.ofNullable(applyBatchMapper.selectOne(
                new LambdaQueryWrapper<ApplyBatchEntity>()
                        .eq(ApplyBatchEntity::getBatchNo, batchNo)));
    }

    @Override
    public List<ApplyBatchEntity> findByYearAndStatus(int year, String status) {
        return applyBatchMapper.selectList(
                new LambdaQueryWrapper<ApplyBatchEntity>()
                        .eq(ApplyBatchEntity::getYear, year)
                        .eq(ApplyBatchEntity::getStatus, status));
    }

    @Override
    public List<ApplyBatchEntity> findOpen() {
        return applyBatchMapper.selectList(
                new LambdaQueryWrapper<ApplyBatchEntity>()
                        .eq(ApplyBatchEntity::getStatus, ApplyBatchEntity.STATUS_OPEN)
                        .orderByDesc(ApplyBatchEntity::getYear)
                        .orderByDesc(ApplyBatchEntity::getStartAt));
    }

    @Override
    public int update(ApplyBatchEntity batch) {
        return applyBatchMapper.updateById(batch);
    }
}
