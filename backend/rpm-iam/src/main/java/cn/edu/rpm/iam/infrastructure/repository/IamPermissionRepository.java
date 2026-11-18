package cn.edu.rpm.iam.infrastructure.repository;

import cn.edu.rpm.iam.infrastructure.entity.IamPermissionEntity;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * {@code iam_permission} 功能权限点仓储接口（T-IAM-003）。
 *
 * <p>权限点为受控字典表：仅提供插入与查询契约，无更新/删除语义；
 * 供 Batch-4 功能权限解析按模块装配权限集合使用。</p>
 */
public interface IamPermissionRepository {

    /**
     * 插入权限点。
     *
     * <p>主键 {@code id} 由 MyBatis-Plus 在执行期生成；
     * 权限码重复时抛 {@link org.springframework.dao.DuplicateKeyException}
     * （uk_iam_permission_code 唯一）。</p>
     *
     * @param permission 待插入的权限点 PO
     */
    void insert(IamPermissionEntity permission);

    /**
     * 按权限码查询权限点（uk_iam_permission_code 唯一）。
     *
     * @param code 权限码，如 {@code project:view}
     * @return 命中的权限点；不存在返回空 {@link Optional}
     */
    Optional<IamPermissionEntity> findByCode(String code);

    /**
     * 按所属功能模块查询权限点列表。
     *
     * @param module 功能模块标识，如 {@code project}
     * @return 该模块下全部权限点；无命中返回空列表
     */
    List<IamPermissionEntity> findByModule(String module);

    /**
     * 按主键批量查询权限点（Batch-4 权限解析使用，禁止循环单条查询）。
     *
     * @param ids 权限点 ID 集合
     * @return 命中的权限点列表；输入为空或无命中返回空列表
     */
    List<IamPermissionEntity> findByIds(Collection<String> ids);
}
