package cn.edu.rpm.organization.infrastructure.repository;

import cn.edu.rpm.organization.infrastructure.entity.OrgUnitEntity;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * {@code org_unit} 组织树仓储接口（T-ORG-001）。
 *
 * <p>纯单表 CRUD 数据访问契约；子树查询基于 {@code path} 前缀 LIKE
 * （V01 未启用 ltree，路径为字符串拼接存储）。</p>
 */
public interface OrgUnitRepository {

    /**
     * 插入组织节点。
     *
     * <p>主键 {@code id} 由 MyBatis-Plus 在执行期生成；
     * 组织编码重复时抛 {@link org.springframework.dao.DuplicateKeyException}
     * （uk_org_unit_code 唯一）。调用方须保证 path / parent_id 树结构一致性。</p>
     *
     * @param unit 待插入的组织节点 PO
     */
    void insert(OrgUnitEntity unit);

    /**
     * 按主键查询组织节点。
     *
     * @param id 组织节点主键
     * @return 命中的组织节点；不存在返回空 {@link Optional}
     */
    Optional<OrgUnitEntity> findById(String id);

    /**
     * 按父节点查询直接子节点（idx_org_unit_parent），按 sort_order 升序稳定排序。
     *
     * @param parentId 父组织 ID
     * @return 直接子节点列表；无子节点返回空列表
     */
    List<OrgUnitEntity> findChildrenByParentId(String parentId);

    /**
     * 按 path 前缀查询子树（含前缀节点自身及其全部后代）。
     *
     * <p>实现为 {@code path LIKE '传入前缀%'}；调用方传入的 path 不得包含
     * LIKE 通配符（% 或 _），否则匹配语义失效（组织编码不含通配符字符）。</p>
     *
     * @param path 子树根节点路径，如 {@code /UNIV/CSE}
     * @return 命中的子树节点列表；无命中返回空列表
     */
    List<OrgUnitEntity> findSubtreeByPath(String path);

    /**
     * 按主键批量查询组织节点（Batch-4 部门范围树展开使用，禁止循环单条查询）。
     *
     * @param ids 组织节点 ID 集合
     * @return 命中的组织节点列表；输入为空或无命中返回空列表
     */
    List<OrgUnitEntity> findByIds(Collection<String> ids);

    /**
     * 查询全部生效组织节点（下拉选项用），按 sort_order、code 稳定排序。
     *
     * @return 生效组织列表；无数据返回空列表
     */
    List<OrgUnitEntity> findAllActive();

    /**
     * 按多个 path 前缀批量查询子树（单条参数化 SQL，避免逐部门查询）。
     *
     * <p>实现为 {@code path LIKE p1% OR path LIKE p2% ...}；调用方传入的 path
     * 不得包含 LIKE 通配符（% 或 _）。</p>
     *
     * @param paths 子树根节点路径集合
     * @return 命中的子树节点列表；输入为空或无命中返回空列表
     */
    List<OrgUnitEntity> findSubtreeByPaths(Collection<String> paths);
}
