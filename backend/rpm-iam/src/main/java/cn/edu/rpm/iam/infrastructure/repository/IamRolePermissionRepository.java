package cn.edu.rpm.iam.infrastructure.repository;

import java.util.List;

/**
 * {@code iam_role_permission} 角色-权限点绑定仓储接口（Batch-4 / T-IAM-008）。
 *
 * <p>只读数据访问契约：功能权限解析按角色批量取权限点 ID，再经
 * {@link IamPermissionRepository#findByIds} 批量取权限码（禁止 N+1）。</p>
 */
public interface IamRolePermissionRepository {

    /**
     * 按角色查询绑定的权限点 ID 列表（主键前缀 {@code role_id}）。
     *
     * @param roleId 角色 ID（uuid 文本）
     * @return 权限点 ID 列表；无绑定返回空列表
     */
    List<String> findPermissionIdsByRoleId(String roleId);
}
