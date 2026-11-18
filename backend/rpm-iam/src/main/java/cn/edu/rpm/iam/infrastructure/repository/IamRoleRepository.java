package cn.edu.rpm.iam.infrastructure.repository;

import cn.edu.rpm.iam.infrastructure.entity.IamRoleEntity;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * {@code iam_role} 角色仓储接口（T-IAM-002）。
 *
 * <p>纯单表 CRUD 数据访问契约。约定：</p>
 * <ul>
 *   <li>插入撞唯一键（uk_iam_role_code）时抛出
 *       Spring {@link org.springframework.dao.DuplicateKeyException}；</li>
 *   <li>更新以实体 {@code version} 旧值为乐观锁条件。</li>
 * </ul>
 */
public interface IamRoleRepository {

    /**
     * 插入角色。
     *
     * <p>主键 {@code id} 由 MyBatis-Plus 在执行期生成；
     * 角色编码重复时抛 {@link org.springframework.dao.DuplicateKeyException}。</p>
     *
     * @param role 待插入的角色 PO
     */
    void insert(IamRoleEntity role);

    /**
     * 按主键查询角色。
     *
     * @param id 角色主键
     * @return 命中的角色；不存在返回空 {@link Optional}
     */
    Optional<IamRoleEntity> findById(String id);

    /**
     * 按角色编码查询角色（uk_iam_role_code 唯一）。
     *
     * @param code 角色编码
     * @return 命中的角色；不存在返回空 {@link Optional}
     */
    Optional<IamRoleEntity> findByCode(String code);

    /**
     * 按主键批量查询角色（Batch-4 数据范围解析使用，禁止循环单条查询）。
     *
     * @param ids 角色 ID 集合
     * @return 命中的角色列表；输入为空或无命中返回空列表
     */
    List<IamRoleEntity> findByIds(Collection<String> ids);

    /**
     * 按乐观锁更新角色（实体 {@code version} 旧值作并发条件）。
     *
     * @param role 携带主键与待更新字段的角色 PO
     * @return 受影响行数；0 表示记录不存在或版本冲突
     */
    int update(IamRoleEntity role);
}
