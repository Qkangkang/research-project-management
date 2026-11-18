package cn.edu.rpm.iam.infrastructure.repository;

import cn.edu.rpm.iam.infrastructure.entity.IamUserRoleEntity;
import cn.edu.rpm.iam.infrastructure.mapper.IamUserRoleMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * {@code iam_user_role} 用户-角色-组织关联仓储实现（T-IAM-004）。
 *
 * <p>基于 MyBatis-Plus {@link IamUserRoleMapper} 的薄委托层；查询条件以
 * {@link LambdaQueryWrapper} 按实体注解生成，无手写 SQL。</p>
 */
@Repository
public class IamUserRoleRepositoryImpl implements IamUserRoleRepository {

    private final IamUserRoleMapper iamUserRoleMapper;

    public IamUserRoleRepositoryImpl(IamUserRoleMapper iamUserRoleMapper) {
        this.iamUserRoleMapper = iamUserRoleMapper;
    }

    @Override
    public void insert(IamUserRoleEntity association) {
        iamUserRoleMapper.insert(association);
    }

    @Override
    public List<IamUserRoleEntity> findByUserId(String userId) {
        return iamUserRoleMapper.selectList(
                new LambdaQueryWrapper<IamUserRoleEntity>().eq(IamUserRoleEntity::getUserId, userId));
    }

    @Override
    public List<IamUserRoleEntity> findValidByUserId(String userId, OffsetDateTime at) {
        return iamUserRoleMapper.selectList(new LambdaQueryWrapper<IamUserRoleEntity>()
                .eq(IamUserRoleEntity::getUserId, userId)
                .eq(IamUserRoleEntity::getStatus, IamUserRoleEntity.STATUS_ACTIVE)
                .le(IamUserRoleEntity::getValidFrom, at)
                .and(w -> w.isNull(IamUserRoleEntity::getValidTo).or().gt(IamUserRoleEntity::getValidTo, at)));
    }
}
