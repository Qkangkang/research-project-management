package cn.edu.rpm.iam.infrastructure.repository;

import cn.edu.rpm.iam.infrastructure.entity.IamPermissionEntity;
import cn.edu.rpm.iam.infrastructure.mapper.IamPermissionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * {@code iam_permission} 功能权限点仓储实现（T-IAM-003）。
 *
 * <p>基于 MyBatis-Plus {@link IamPermissionMapper} 的薄委托层；查询条件以
 * {@link LambdaQueryWrapper} 按实体注解生成，无手写 SQL。</p>
 */
@Repository
public class IamPermissionRepositoryImpl implements IamPermissionRepository {

    private final IamPermissionMapper iamPermissionMapper;

    public IamPermissionRepositoryImpl(IamPermissionMapper iamPermissionMapper) {
        this.iamPermissionMapper = iamPermissionMapper;
    }

    @Override
    public void insert(IamPermissionEntity permission) {
        iamPermissionMapper.insert(permission);
    }

    @Override
    public Optional<IamPermissionEntity> findByCode(String code) {
        return Optional.ofNullable(iamPermissionMapper.selectOne(
                new LambdaQueryWrapper<IamPermissionEntity>().eq(IamPermissionEntity::getCode, code)));
    }

    @Override
    public List<IamPermissionEntity> findByModule(String module) {
        return iamPermissionMapper.selectList(
                new LambdaQueryWrapper<IamPermissionEntity>().eq(IamPermissionEntity::getModule, module));
    }

    @Override
    public List<IamPermissionEntity> findByIds(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return iamPermissionMapper.selectBatchIds(ids);
    }
}
