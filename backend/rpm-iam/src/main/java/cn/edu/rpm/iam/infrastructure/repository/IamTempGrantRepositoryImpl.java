package cn.edu.rpm.iam.infrastructure.repository;

import cn.edu.rpm.iam.infrastructure.entity.IamTempGrantEntity;
import cn.edu.rpm.iam.infrastructure.mapper.IamTempGrantMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * {@code iam_temp_grant} 临时授权仓储实现（T-IAM-005）。
 *
 * <p>基于 MyBatis-Plus {@link IamTempGrantMapper} 的薄委托层；查询条件以
 * {@link LambdaQueryWrapper} 按实体注解生成，无手写 SQL。</p>
 */
@Repository
public class IamTempGrantRepositoryImpl implements IamTempGrantRepository {

    private final IamTempGrantMapper iamTempGrantMapper;

    public IamTempGrantRepositoryImpl(IamTempGrantMapper iamTempGrantMapper) {
        this.iamTempGrantMapper = iamTempGrantMapper;
    }

    @Override
    public void insert(IamTempGrantEntity grant) {
        iamTempGrantMapper.insert(grant);
    }

    @Override
    public List<IamTempGrantEntity> findValidByGrantee(String granteeId, OffsetDateTime at) {
        return iamTempGrantMapper.selectList(new LambdaQueryWrapper<IamTempGrantEntity>()
                .eq(IamTempGrantEntity::getGranteeId, granteeId)
                .eq(IamTempGrantEntity::getStatus, IamTempGrantEntity.STATUS_ACTIVE)
                .le(IamTempGrantEntity::getValidFrom, at)
                .gt(IamTempGrantEntity::getValidTo, at));
    }
}
