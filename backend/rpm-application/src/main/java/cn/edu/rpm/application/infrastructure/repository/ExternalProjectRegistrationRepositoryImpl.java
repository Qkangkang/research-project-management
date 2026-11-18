package cn.edu.rpm.application.infrastructure.repository;

import cn.edu.rpm.application.infrastructure.entity.ExternalProjectRegistrationEntity;
import cn.edu.rpm.application.infrastructure.mapper.ExternalProjectRegistrationMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * {@code external_project_registration} 外部项目登记仓储实现（T-APP-007）。
 *
 * <p>基于 MyBatis-Plus {@link ExternalProjectRegistrationMapper} 的薄委托层；
 * 按申报查询与 uk_external_registration_application 唯一索引同谓词。</p>
 */
@Repository
public class ExternalProjectRegistrationRepositoryImpl implements ExternalProjectRegistrationRepository {

    private final ExternalProjectRegistrationMapper externalProjectRegistrationMapper;

    public ExternalProjectRegistrationRepositoryImpl(
            ExternalProjectRegistrationMapper externalProjectRegistrationMapper) {
        this.externalProjectRegistrationMapper = externalProjectRegistrationMapper;
    }

    @Override
    public void insert(ExternalProjectRegistrationEntity registration) {
        externalProjectRegistrationMapper.insert(registration);
    }

    @Override
    public Optional<ExternalProjectRegistrationEntity> findById(String id) {
        return Optional.ofNullable(externalProjectRegistrationMapper.selectById(id));
    }

    @Override
    public Optional<ExternalProjectRegistrationEntity> findByApplicationId(String applicationId) {
        return Optional.ofNullable(externalProjectRegistrationMapper.selectOne(
                new LambdaQueryWrapper<ExternalProjectRegistrationEntity>()
                        .eq(ExternalProjectRegistrationEntity::getApplicationId, applicationId)));
    }

    @Override
    public Optional<ExternalProjectRegistrationEntity> findByContractNo(String contractNo) {
        return Optional.ofNullable(externalProjectRegistrationMapper.selectOne(
                new LambdaQueryWrapper<ExternalProjectRegistrationEntity>()
                        .eq(ExternalProjectRegistrationEntity::getContractNo, contractNo)
                        .last("LIMIT 1")));
    }

    @Override
    public int update(ExternalProjectRegistrationEntity registration) {
        return externalProjectRegistrationMapper.updateById(registration);
    }
}
