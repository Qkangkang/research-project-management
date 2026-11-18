package cn.edu.rpm.iam.infrastructure.repository;

import cn.edu.rpm.iam.infrastructure.entity.IamRolePermissionEntity;
import cn.edu.rpm.iam.infrastructure.mapper.IamRolePermissionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * {@code iam_role_permission} 角色-权限点绑定仓储实现（Batch-4 / T-IAM-008）。
 *
 * <p>基于 MyBatis-Plus {@link IamRolePermissionMapper} 的薄委托层；只投影
 * {@code permission_id} 列，查询走复合主键前缀 {@code (role_id, permission_id)}。</p>
 */
@Repository
public class IamRolePermissionRepositoryImpl implements IamRolePermissionRepository {

    private final IamRolePermissionMapper iamRolePermissionMapper;

    public IamRolePermissionRepositoryImpl(IamRolePermissionMapper iamRolePermissionMapper) {
        this.iamRolePermissionMapper = iamRolePermissionMapper;
    }

    @Override
    public List<String> findPermissionIdsByRoleId(String roleId) {
        return iamRolePermissionMapper.selectList(new LambdaQueryWrapper<IamRolePermissionEntity>()
                        .select(IamRolePermissionEntity::getPermissionId)
                        .eq(IamRolePermissionEntity::getRoleId, roleId))
                .stream()
                .map(IamRolePermissionEntity::getPermissionId)
                .toList();
    }
}
