package cn.edu.rpm.iam.infrastructure.repository;

import cn.edu.rpm.iam.infrastructure.entity.IamUserEntity;
import cn.edu.rpm.iam.infrastructure.mapper.IamUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * {@code iam_user} 人员账号仓储实现（T-IAM-001）。
 *
 * <p>基于 MyBatis-Plus {@link IamUserMapper} 的薄委托层；查询条件以
 * {@link LambdaQueryWrapper} 按实体注解生成，无手写 SQL。</p>
 */
@Repository
public class IamUserRepositoryImpl implements IamUserRepository {

    private final IamUserMapper iamUserMapper;

    public IamUserRepositoryImpl(IamUserMapper iamUserMapper) {
        this.iamUserMapper = iamUserMapper;
    }

    @Override
    public void insert(IamUserEntity user) {
        iamUserMapper.insert(user);
    }

    @Override
    public Optional<IamUserEntity> findById(String id) {
        return Optional.ofNullable(iamUserMapper.selectById(id));
    }

    @Override
    public Optional<IamUserEntity> findByEmployeeNo(String employeeNo) {
        return Optional.ofNullable(iamUserMapper.selectOne(
                new LambdaQueryWrapper<IamUserEntity>().eq(IamUserEntity::getEmployeeNo, employeeNo)));
    }

    @Override
    public Optional<IamUserEntity> findBySsoSubject(String ssoSubject) {
        return Optional.ofNullable(iamUserMapper.selectOne(
                new LambdaQueryWrapper<IamUserEntity>().eq(IamUserEntity::getSsoSubject, ssoSubject)));
    }

    @Override
    public List<IamUserEntity> findByIds(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return iamUserMapper.selectBatchIds(ids);
    }

    @Override
    public List<IamUserEntity> findActive(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        return iamUserMapper.selectList(new LambdaQueryWrapper<IamUserEntity>()
                .eq(IamUserEntity::getStatus, "ACTIVE")
                .orderByAsc(IamUserEntity::getDisplayName)
                .last("LIMIT " + safeLimit));
    }

    @Override
    public int update(IamUserEntity user) {
        return iamUserMapper.updateById(user);
    }
}
