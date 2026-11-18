package cn.edu.rpm.iam.infrastructure.adapter;

import cn.edu.rpm.common.util.UuidUtils;
import cn.edu.rpm.iam.domain.port.OrgScopePort;
import cn.edu.rpm.organization.infrastructure.entity.OrgMembershipEntity;
import cn.edu.rpm.organization.infrastructure.entity.OrgUnitEntity;
import cn.edu.rpm.organization.infrastructure.repository.OrgMembershipRepository;
import cn.edu.rpm.organization.infrastructure.repository.OrgUnitRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * {@link OrgScopePort} 的组织模块适配器（Batch-4 / T-IAM-009）。
 *
 * <p>桥接 {@code org_membership}（主部门）与 {@code org_unit}（部门树）：<b>只调用组织模块
 * 暴露的 Repository 契约</b>，不直接访问其 Mapper/表；查询全部批量化
 * （{@code findByIds} + {@code findSubtreeByPaths} 各一次），无循环内 SQL（RULE-DB2）。
 * 模块依赖方向遵循任务拆解依赖图 {@code rpm-iam ─→ rpm-organization}。</p>
 */
@Repository
public class OrgScopeAdapter implements OrgScopePort {

    private final OrgMembershipRepository membershipRepository;

    private final OrgUnitRepository orgUnitRepository;

    public OrgScopeAdapter(OrgMembershipRepository membershipRepository, OrgUnitRepository orgUnitRepository) {
        this.membershipRepository = membershipRepository;
        this.orgUnitRepository = orgUnitRepository;
    }

    @Override
    public Set<UUID> findPrimaryDeptIds(UUID userId) {
        return membershipRepository.findPrimaryByUserId(UuidUtils.formatCompact(userId))
                .filter(membership -> OrgMembershipEntity.STATUS_ACTIVE.equals(membership.getStatus()))
                .map(membership -> Set.of(UuidUtils.parse(membership.getOrgId())))
                .orElse(Set.of());
    }

    @Override
    public Set<UUID> expandSubtrees(Set<UUID> deptIds) {
        if (deptIds == null || deptIds.isEmpty()) {
            return Set.of();
        }
        List<String> ids = deptIds.stream().map(UuidUtils::formatCompact).toList();
        List<String> paths = orgUnitRepository.findByIds(ids).stream()
                .map(OrgUnitEntity::getPath)
                .filter(path -> path != null && !path.isBlank())
                .distinct()
                .toList();
        if (paths.isEmpty()) {
            return Set.of();
        }
        return orgUnitRepository.findSubtreeByPaths(paths).stream()
                .filter(unit -> OrgUnitEntity.STATUS_ACTIVE.equals(unit.getStatus()))
                .map(unit -> UuidUtils.parse(unit.getId()))
                .collect(Collectors.toUnmodifiableSet());
    }
}
