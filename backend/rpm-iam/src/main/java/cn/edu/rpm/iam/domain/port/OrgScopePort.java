package cn.edu.rpm.iam.domain.port;

import java.util.Set;
import java.util.UUID;

/**
 * 组织数据范围端口：数据范围解析所需的"人员主部门 / 部门子树"能力。
 *
 * <p>由 infrastructure 适配器桥接 {@code org_membership} 与 {@code org_unit}
 * （模块依赖图允许 {@code rpm-iam ─→ rpm-organization}）；领域层不直接访问
 * 组织模块的 Mapper/表。实现必须使用批量查询，禁止为每个部门逐条回源（RULE-DB2）。</p>
 */
public interface OrgScopePort {

    /**
     * 查询人员的主要部门（{@code org_membership.is_primary = true} 且状态生效）。
     *
     * @param userId 人员用户 ID
     * @return 主部门组织 ID 集合；无归属返回空集合
     */
    Set<UUID> findPrimaryDeptIds(UUID userId);

    /**
     * 将部门集合展开为其自身与全部后代组织（{@code org_unit.path} 前缀匹配）。
     *
     * <p>仅返回状态 {@code ACTIVE} 的组织节点；输入为空返回空集合。</p>
     *
     * @param deptIds 部门组织 ID 集合
     * @return 展开后的组织 ID 集合
     */
    Set<UUID> expandSubtrees(Set<UUID> deptIds);
}
