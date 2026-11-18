package cn.edu.rpm.application.infrastructure.repository;

import cn.edu.rpm.application.infrastructure.entity.ApplicationVersionEntity;
import cn.edu.rpm.application.infrastructure.mapper.ApplicationVersionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * {@code application_version} 申报版本仓储实现（T-APP-004）。
 *
 * <p>基于 MyBatis-Plus {@link ApplicationVersionMapper} 的薄委托层；幂等键查询与
 * {@code uk_application_version_idem} 唯一索引同谓词。</p>
 */
@Repository
public class ApplicationVersionRepositoryImpl implements ApplicationVersionRepository {

    private final ApplicationVersionMapper applicationVersionMapper;

    public ApplicationVersionRepositoryImpl(ApplicationVersionMapper applicationVersionMapper) {
        this.applicationVersionMapper = applicationVersionMapper;
    }

    @Override
    public void insert(ApplicationVersionEntity version) {
        applicationVersionMapper.insert(version);
    }

    @Override
    public Optional<ApplicationVersionEntity> findById(String id) {
        return Optional.ofNullable(applicationVersionMapper.selectById(id));
    }

    @Override
    public Optional<ApplicationVersionEntity> findByApplicationIdAndVersionNo(String applicationId, int versionNo) {
        return Optional.ofNullable(applicationVersionMapper.selectOne(
                new LambdaQueryWrapper<ApplicationVersionEntity>()
                        .eq(ApplicationVersionEntity::getApplicationId, applicationId)
                        .eq(ApplicationVersionEntity::getVersionNo, versionNo)));
    }

    @Override
    public Optional<ApplicationVersionEntity> findByIdempotencyKey(String idempotencyKey) {
        return Optional.ofNullable(applicationVersionMapper.selectOne(
                new LambdaQueryWrapper<ApplicationVersionEntity>()
                        .eq(ApplicationVersionEntity::getSubmitIdempotencyKey, idempotencyKey)));
    }

    @Override
    public Optional<ApplicationVersionEntity> findDraftByApplicationId(String applicationId) {
        return Optional.ofNullable(applicationVersionMapper.selectDraftByApplicationId(applicationId));
    }

    @Override
    public int findMaxVersionNo(String applicationId) {
        return applicationVersionMapper.selectMaxVersionNo(applicationId);
    }

    @Override
    public int update(ApplicationVersionEntity version) {
        return applicationVersionMapper.updateById(version);
    }
}
