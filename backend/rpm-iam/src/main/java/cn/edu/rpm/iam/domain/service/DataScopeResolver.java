package cn.edu.rpm.iam.domain.service;

import cn.edu.rpm.common.util.UuidUtils;
import cn.edu.rpm.iam.application.dto.ProjectQuery;
import cn.edu.rpm.iam.application.dto.ProjectScope;
import cn.edu.rpm.iam.domain.model.ResourceTypes;
import cn.edu.rpm.iam.domain.model.RoleAssignmentView;
import cn.edu.rpm.iam.domain.model.RoleCodes;
import cn.edu.rpm.iam.domain.model.RoleScopeTypes;
import cn.edu.rpm.iam.domain.model.RoleView;
import cn.edu.rpm.iam.domain.model.TempGrantView;
import cn.edu.rpm.iam.domain.port.OrgScopePort;
import cn.edu.rpm.iam.domain.port.RoleQueryPort;
import cn.edu.rpm.iam.domain.port.TempGrantQueryPort;
import cn.edu.rpm.iam.domain.port.UserRoleQueryPort;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 项目数据范围解析器（T-IAM-009，技术文档 03 §2.2 / §2.3）。
 *
 * <p>项目身份不等于全局角色：按当前生效角色解析</p>
 * <ul>
 *   <li>{@code PI}（或角色范围 {@code SELF}）→ {@code piId = 当前用户}；</li>
 *   <li>角色范围 {@code DEPT / DEPT_TREE} → 角色授权部门（缺省取人员主部门），
 *       {@code DEPT_TREE} 以组织树子树展开；</li>
 *   <li>{@code RESEARCH_ADMIN / LEADER / AUDITOR} 且范围 {@code ALL} → {@code scopeAll}；</li>
 *   <li>{@code iam_temp_grant} 有效授权 → {@code grantedProjectIds}，每次查询按
 *       {@link GrantValidityGuard} 复核有效期，并按动作权限码筛选。</li>
 * </ul>
 *
 * <p>{@code scopeAll} 只由角色授权范围产生，禁止前端传入；项目成员身份由仓储的
 * {@code EXISTS project_member} 条件表达，不放入 {@link ProjectScope}。
 * 平铺查询全部使用批量接口，无循环内 SQL（RULE-DB2）。领域层只依赖端口，
 * 不依赖 PO / Mapper（architecture-A）。</p>
 */
public class DataScopeResolver {

    private final UserRoleQueryPort userRoleQueryPort;

    private final RoleQueryPort roleQueryPort;

    private final TempGrantQueryPort tempGrantQueryPort;

    private final OrgScopePort orgScopePort;

    private final GrantValidityGuard grantValidityGuard;

    public DataScopeResolver(UserRoleQueryPort userRoleQueryPort,
                             RoleQueryPort roleQueryPort,
                             TempGrantQueryPort tempGrantQueryPort,
                             OrgScopePort orgScopePort,
                             GrantValidityGuard grantValidityGuard) {
        this.userRoleQueryPort = Objects.requireNonNull(userRoleQueryPort, "userRoleQueryPort 不能为空");
        this.roleQueryPort = Objects.requireNonNull(roleQueryPort, "roleQueryPort 不能为空");
        this.tempGrantQueryPort = Objects.requireNonNull(tempGrantQueryPort, "tempGrantQueryPort 不能为空");
        this.orgScopePort = Objects.requireNonNull(orgScopePort, "orgScopePort 不能为空");
        this.grantValidityGuard = Objects.requireNonNull(grantValidityGuard, "grantValidityGuard 不能为空");
    }

    /**
     * 解析用户在请求时点的项目数据范围。
     *
     * @param userId       用户 ID
     * @param activeRoleId 当前生效角色 ID；为空按全部有效角色解析；非空但该角色不在有效
     *                     关联内时仅保留临时授权范围（安全默认）
     * @param requestTime  请求时点
     * @param query        数据范围解析参数（动作、请求项目集）
     * @return 项目数据范围（永不为 {@code null}）
     */
    public ProjectScope resolve(UUID userId, UUID activeRoleId, Instant requestTime, ProjectQuery query) {
        Objects.requireNonNull(userId, "userId 不能为空");
        Objects.requireNonNull(requestTime, "requestTime 不能为空");
        OffsetDateTime at = OffsetDateTime.ofInstant(requestTime, ZoneOffset.UTC);

        List<RoleAssignmentView> assignments = effectiveAssignments(userId, activeRoleId, at);
        Set<String> roleIds = assignments.stream()
                .map(RoleAssignmentView::roleId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, RoleView> rolesById = roleIds.isEmpty()
                ? Map.of()
                : roleQueryPort.findByIds(roleIds).stream()
                        .collect(Collectors.toMap(RoleView::id, Function.identity(), (left, right) -> left));

        boolean scopeAll = false;
        UUID piId = null;
        Set<UUID> deptIds = new LinkedHashSet<>();
        boolean expandDeptTree = false;
        for (RoleAssignmentView assignment : assignments) {
            RoleView role = rolesById.get(assignment.roleId());
            if (role == null) {
                continue;
            }
            String roleCode = role.code();
            String scopeType = role.scopeType();
            if (RoleCodes.PI.equals(roleCode) || RoleScopeTypes.SELF.equals(scopeType)) {
                piId = userId;
            }
            if (RoleCodes.ALL_SCOPE_ROLES.contains(roleCode) && RoleScopeTypes.ALL.equals(scopeType)) {
                scopeAll = true;
            }
            if (RoleScopeTypes.DEPT.equals(scopeType) || RoleScopeTypes.DEPT_TREE.equals(scopeType)) {
                if (assignment.orgId() != null) {
                    deptIds.add(UuidUtils.parse(assignment.orgId()));
                } else {
                    deptIds.addAll(orgScopePort.findPrimaryDeptIds(userId));
                }
                expandDeptTree = expandDeptTree || RoleScopeTypes.DEPT_TREE.equals(scopeType);
            }
        }
        if (expandDeptTree && !deptIds.isEmpty()) {
            deptIds = new LinkedHashSet<>(orgScopePort.expandSubtrees(deptIds));
        }

        String action = query == null ? null : query.action();
        Set<UUID> grantedProjectIds = new LinkedHashSet<>();
        for (TempGrantView grant : tempGrantQueryPort.findValidByGrantee(
                UuidUtils.formatCompact(userId), at)) {
            if (!ResourceTypes.PROJECT.equals(grant.resourceType())) {
                continue;
            }
            if (action != null && !action.isBlank() && !action.equals(grant.permissionCode())) {
                continue;
            }
            if (!grantValidityGuard.isValid(grant.validFrom().toInstant(), grant.validTo().toInstant(),
                    requestTime)) {
                continue;
            }
            grantedProjectIds.add(UuidUtils.parse(grant.resourceId()));
        }

        return ProjectScope.of(scopeAll, piId, deptIds, grantedProjectIds);
    }

    private List<RoleAssignmentView> effectiveAssignments(UUID userId, UUID activeRoleId, OffsetDateTime at) {
        List<RoleAssignmentView> assignments = userRoleQueryPort.findValidAssignments(
                UuidUtils.formatCompact(userId), at);
        if (assignments == null || assignments.isEmpty()) {
            return List.of();
        }
        if (activeRoleId == null) {
            return assignments;
        }
        String activeRoleText = activeRoleId.toString();
        return assignments.stream()
                .filter(assignment -> activeRoleText.equals(normalize(assignment.roleId())))
                .toList();
    }

    private static String normalize(String uuidText) {
        return UuidUtils.format(UuidUtils.parse(uuidText));
    }
}
