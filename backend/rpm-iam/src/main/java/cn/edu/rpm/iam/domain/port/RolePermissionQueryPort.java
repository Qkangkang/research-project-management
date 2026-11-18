package cn.edu.rpm.iam.domain.port;

import java.util.List;

/**
 * 角色功能权限查询端口（Batch-4 / T-IAM-008）。
 *
 * <p>封装 {@code iam_role_permission} → {@code iam_permission} 的两步装配，
 * 领域层只关心"角色最终拥有哪些权限码"，不感知关联表与字典表结构。</p>
 */
public interface RolePermissionQueryPort {

    /**
     * 查询角色绑定的功能权限码集合。
     *
     * @param roleId 角色 ID（uuid 文本）
     * @return 权限码列表；未绑定任何权限时返回空列表
     */
    List<String> findPermissionCodesByRoleId(String roleId);
}
