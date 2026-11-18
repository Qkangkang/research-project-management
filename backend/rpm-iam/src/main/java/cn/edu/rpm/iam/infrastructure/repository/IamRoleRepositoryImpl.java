package cn.edu.rpm.iam.infrastructure.repository;

import cn.edu.rpm.iam.infrastructure.entity.IamRoleEntity;
import cn.edu.rpm.iam.infrastructure.mapper.IamRoleMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * {@code iam_role} 角色仓储实现（T-IAM-002）。
 *
 * <p>基于 MyBatis-Plus {@link IamRoleMapper} 的薄委托层；查询条件以
 * {@link LambdaQueryWrapper} 按实体注解生成，无手写 SQL。</p>
 */
@Repository
public class IamRoleRepositoryImpl implements IamRoleRepository {

    private final IamRoleMapper iamRoleMapper;

    public IamRoleRepositoryImpl(IamRoleMapper iamRoleMapper) {
        this.iamRoleMapper = iamRoleMapper;
    }

    @Override
    public void insert(IamRoleEntity role) {
        iamRoleMapper.insert(role);
    }

    @Override
    public Optional<IamRoleEntity> findById(String id) {
        return Optional.ofNullable(iamRoleMapper.selectById(id));
    }

    @Override
    public Optional<IamRoleEntity> findByCode(String code) {
        return Optional.ofNullable(iamRoleMapper.selectOne(
                new LambdaQueryWrapper<IamRoleEntity>().eq(IamRoleEntity::getCode, code)));
    }

    @Override
    public List<IamRoleEntity> findByIds(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return iamRoleMapper.selectBatchIds(ids);
    }

    @Override
    public int update(IamRoleEntity role) {
        return iamRoleMapper.updateById(role);
    }
}
