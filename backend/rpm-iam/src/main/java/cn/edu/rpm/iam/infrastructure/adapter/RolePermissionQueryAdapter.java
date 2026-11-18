package cn.edu.rpm.iam.infrastructure.adapter;

import cn.edu.rpm.iam.domain.port.RolePermissionQueryPort;
import cn.edu.rpm.iam.infrastructure.entity.IamPermissionEntity;
import cn.edu.rpm.iam.infrastructure.repository.IamPermissionRepository;
import cn.edu.rpm.iam.infrastructure.repository.IamRolePermissionRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * {@link RolePermissionQueryPort} 的持久化适配器（Batch-4 / T-IAM-008）。
 *
 * <p>装配链路：{@code iam_role_permission} 取权限点 ID（一次查询）→
 * {@code iam_permission} 批量取权限码（一次查询）；未绑定权限时不触发第二次查询，
 * 全程无循环内 SQL（RULE-DB2）。</p>
 */
@Repository
public class RolePermissionQueryAdapter implements RolePermissionQueryPort {

    private final IamRolePermissionRepository rolePermissionRepository;

    private final IamPermissionRepository permissionRepository;

    public RolePermissionQueryAdapter(IamRolePermissionRepository rolePermissionRepository,
                                      IamPermissionRepository permissionRepository) {
        this.rolePermissionRepository = rolePermissionRepository;
        this.permissionRepository = permissionRepository;
    }

    @Override
    public List<String> findPermissionCodesByRoleId(String roleId) {
        List<String> permissionIds = rolePermissionRepository.findPermissionIdsByRoleId(roleId);
        if (permissionIds == null || permissionIds.isEmpty()) {
            return List.of();
        }
        return permissionRepository.findByIds(permissionIds).stream()
                .map(IamPermissionEntity::getCode)
                .toList();
    }
}
