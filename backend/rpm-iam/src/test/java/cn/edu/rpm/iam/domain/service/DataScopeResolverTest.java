package cn.edu.rpm.iam.domain.service;

import cn.edu.rpm.iam.application.dto.ProjectQuery;
import cn.edu.rpm.iam.application.dto.ProjectScope;
import cn.edu.rpm.iam.domain.model.RoleAssignmentView;
import cn.edu.rpm.iam.domain.model.RoleCodes;
import cn.edu.rpm.iam.domain.model.RoleScopeTypes;
import cn.edu.rpm.iam.domain.model.RoleView;
import cn.edu.rpm.iam.domain.model.TempGrantView;
import cn.edu.rpm.iam.domain.port.OrgScopePort;
import cn.edu.rpm.iam.domain.port.RoleQueryPort;
import cn.edu.rpm.iam.domain.port.TempGrantQueryPort;
import cn.edu.rpm.iam.domain.port.UserRoleQueryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DataScopeResolver} 纯单元测试（T-IAM-009 先行测试，技术文档 03 §2.2）。
 *
 * <p>验收锚点：PI 用户返回 {@code piId}；部门秘书返回 {@code deptIds}（DEPT_TREE 展开子树）；
 * 临时授权返回 {@code grantedProjectIds}（按动作与有效期过滤）；科研管理返回
 * {@code scopeAll=true}；不属于任何范围时返回空范围（TC-IAM-001 / TC-IAM-003 的数据前提）。
 * 依赖以领域端口模拟，不接触 PO。</p>
 */
@ExtendWith(MockitoExtension.class)
class DataScopeResolverTest {

    private static final String USER_ID_TEXT = "0123456789abcdef0123456789abcdef";
    private static final UUID USER_ID = UUID.fromString("01234567-89ab-cdef-0123-456789abcdef");
    private static final String ROLE_PI = "11111111111111111111111111111111";
    private static final String ROLE_SECRETARY = "22222222222222222222222222222222";
    private static final String ROLE_ADMIN = "33333333333333333333333333333333";
    private static final String DEPT_CSE = "d1111111111111111111111111111111";
    private static final UUID DEPT_CSE_UUID = UUID.fromString("d1111111-1111-1111-1111-111111111111");
    private static final String DEPT_CSE_CHILD = "d1111111111111111111111111111112";
    private static final UUID DEPT_CSE_CHILD_UUID = UUID.fromString("d1111111-1111-1111-1111-111111111112");
    private static final String PROJECT_P1 = "aa111111111111111111111111111111";
    private static final UUID PROJECT_P1_UUID = UUID.fromString("aa111111-1111-1111-1111-111111111111");
    private static final String PROJECT_P2 = "aa222222222222222222222222222222";
    private static final UUID PROJECT_P2_UUID = UUID.fromString("aa222222-2222-2222-2222-222222222222");
    private static final Instant REQUEST_TIME = Instant.parse("2026-11-17T02:00:00Z");
    private static final OffsetDateTime AT = OffsetDateTime.ofInstant(REQUEST_TIME, ZoneOffset.UTC);

    @Mock
    private UserRoleQueryPort userRoleQueryPort;

    @Mock
    private RoleQueryPort roleQueryPort;

    @Mock
    private TempGrantQueryPort tempGrantQueryPort;

    @Mock
    private OrgScopePort orgScopePort;

    private DataScopeResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new DataScopeResolver(userRoleQueryPort, roleQueryPort, tempGrantQueryPort,
                orgScopePort, new GrantValidityGuard());
    }

    private static RoleAssignmentView assignment(String roleId, String orgId) {
        return new RoleAssignmentView(roleId, orgId);
    }

    private static RoleView role(String id, String code, String scopeType) {
        return new RoleView(id, code, scopeType);
    }

    private static TempGrantView grant(String resourceId, String permissionCode,
                                       OffsetDateTime validFrom, OffsetDateTime validTo) {
        return new TempGrantView("PROJECT", resourceId, permissionCode, validFrom, validTo);
    }

    private static OffsetDateTime at() {
        return AT;
    }

    @Test
    void piRoleProducesPiScope() {
        when(userRoleQueryPort.findValidAssignments(USER_ID_TEXT, at()))
                .thenReturn(List.of(assignment(ROLE_PI, null)));
        when(roleQueryPort.findByIds(any())).thenReturn(List.of(role(ROLE_PI, RoleCodes.PI, RoleScopeTypes.SELF)));
        when(tempGrantQueryPort.findValidByGrantee(USER_ID_TEXT, at())).thenReturn(List.of());

        ProjectScope scope = resolver.resolve(USER_ID, null, REQUEST_TIME, ProjectQuery.all());

        assertThat(scope.scopeAll()).isFalse();
        assertThat(scope.piId()).isEqualTo(USER_ID);
        assertThat(scope.deptIds()).isEmpty();
        assertThat(scope.grantedProjectIds()).isEmpty();
    }

    @Test
    void deptSecretaryRoleProducesDeptScope() {
        when(userRoleQueryPort.findValidAssignments(USER_ID_TEXT, at()))
                .thenReturn(List.of(assignment(ROLE_SECRETARY, DEPT_CSE)));
        when(roleQueryPort.findByIds(any())).thenReturn(
                List.of(role(ROLE_SECRETARY, RoleCodes.DEPT_SECRETARY, RoleScopeTypes.DEPT)));
        when(tempGrantQueryPort.findValidByGrantee(USER_ID_TEXT, at())).thenReturn(List.of());

        ProjectScope scope = resolver.resolve(USER_ID, null, REQUEST_TIME, ProjectQuery.all());

        assertThat(scope.piId()).isNull();
        assertThat(scope.scopeAll()).isFalse();
        assertThat(scope.deptIds()).containsExactly(DEPT_CSE_UUID);
        verify(orgScopePort, never()).expandSubtrees(any());
    }

    @Test
    void deptTreeRoleExpandsSubtree() {
        when(userRoleQueryPort.findValidAssignments(USER_ID_TEXT, at()))
                .thenReturn(List.of(assignment(ROLE_SECRETARY, DEPT_CSE)));
        when(roleQueryPort.findByIds(any())).thenReturn(
                List.of(role(ROLE_SECRETARY, RoleCodes.DEPT_SECRETARY, RoleScopeTypes.DEPT_TREE)));
        when(tempGrantQueryPort.findValidByGrantee(USER_ID_TEXT, at())).thenReturn(List.of());
        when(orgScopePort.expandSubtrees(Set.of(DEPT_CSE_UUID)))
                .thenReturn(Set.of(DEPT_CSE_UUID, DEPT_CSE_CHILD_UUID));

        ProjectScope scope = resolver.resolve(USER_ID, null, REQUEST_TIME, ProjectQuery.all());

        assertThat(scope.deptIds()).containsExactlyInAnyOrder(DEPT_CSE_UUID, DEPT_CSE_CHILD_UUID);
    }

    @Test
    void deptRoleWithoutOrgFallsBackToPrimaryDept() {
        when(userRoleQueryPort.findValidAssignments(USER_ID_TEXT, at()))
                .thenReturn(List.of(assignment(ROLE_SECRETARY, null)));
        when(roleQueryPort.findByIds(any())).thenReturn(
                List.of(role(ROLE_SECRETARY, RoleCodes.DEPT_SECRETARY, RoleScopeTypes.DEPT)));
        when(tempGrantQueryPort.findValidByGrantee(USER_ID_TEXT, at())).thenReturn(List.of());
        when(orgScopePort.findPrimaryDeptIds(USER_ID)).thenReturn(Set.of(DEPT_CSE_UUID));

        ProjectScope scope = resolver.resolve(USER_ID, null, REQUEST_TIME, ProjectQuery.all());

        assertThat(scope.deptIds()).containsExactly(DEPT_CSE_UUID);
    }

    @Test
    void researchAdminWithAllScopeProducesScopeAll() {
        when(userRoleQueryPort.findValidAssignments(USER_ID_TEXT, at()))
                .thenReturn(List.of(assignment(ROLE_ADMIN, null)));
        when(roleQueryPort.findByIds(any())).thenReturn(
                List.of(role(ROLE_ADMIN, RoleCodes.RESEARCH_ADMIN, RoleScopeTypes.ALL)));
        when(tempGrantQueryPort.findValidByGrantee(USER_ID_TEXT, at())).thenReturn(List.of());

        ProjectScope scope = resolver.resolve(USER_ID, null, REQUEST_TIME, ProjectQuery.all());

        assertThat(scope.scopeAll()).isTrue();
    }

    @Test
    void builtInAdminWithAllScopeProducesScopeAll() {
        when(userRoleQueryPort.findValidAssignments(USER_ID_TEXT, at()))
                .thenReturn(List.of(assignment(ROLE_ADMIN, null)));
        when(roleQueryPort.findByIds(any())).thenReturn(
                List.of(role(ROLE_ADMIN, RoleCodes.ADMIN, RoleScopeTypes.ALL)));
        when(tempGrantQueryPort.findValidByGrantee(USER_ID_TEXT, at())).thenReturn(List.of());

        ProjectScope scope = resolver.resolve(USER_ID, null, REQUEST_TIME, ProjectQuery.all());

        assertThat(scope.scopeAll()).isTrue();
    }

    @Test
    void tempGrantAddsGrantedProjectsFilteredByActionAndValidity() {
        when(userRoleQueryPort.findValidAssignments(USER_ID_TEXT, at())).thenReturn(List.of());
        when(tempGrantQueryPort.findValidByGrantee(USER_ID_TEXT, at())).thenReturn(List.of(
                grant(PROJECT_P1, "project:view", at().minusHours(1), at().plusHours(1)),
                grant(PROJECT_P2, "project:export", at().minusHours(1), at().plusHours(1)),
                grant("aa333333333333333333333333333333", "project:view", at().minusHours(2), at().minusSeconds(1))));

        ProjectScope scope = resolver.resolve(USER_ID, null, REQUEST_TIME, ProjectQuery.ofAction("project:view"));

        assertThat(scope.grantedProjectIds()).containsExactly(PROJECT_P1_UUID);
    }

    @Test
    void allQueryActionsIncludeEveryValidGrantedProject() {
        when(userRoleQueryPort.findValidAssignments(USER_ID_TEXT, at())).thenReturn(List.of());
        when(tempGrantQueryPort.findValidByGrantee(USER_ID_TEXT, at())).thenReturn(List.of(
                grant(PROJECT_P1, "project:view", at().minusHours(1), at().plusHours(1)),
                grant(PROJECT_P2, "project:export", at().minusHours(1), at().plusHours(1))));

        ProjectScope scope = resolver.resolve(USER_ID, null, REQUEST_TIME, ProjectQuery.all());

        assertThat(scope.grantedProjectIds()).containsExactlyInAnyOrder(PROJECT_P1_UUID, PROJECT_P2_UUID);
    }

    @Test
    void userWithoutRolesOrGrantsGetsEmptyScope() {
        when(userRoleQueryPort.findValidAssignments(USER_ID_TEXT, at())).thenReturn(List.of());
        when(tempGrantQueryPort.findValidByGrantee(USER_ID_TEXT, at())).thenReturn(List.of());

        ProjectScope scope = resolver.resolve(USER_ID, null, REQUEST_TIME, ProjectQuery.all());

        assertThat(scope.scopeAll()).isFalse();
        assertThat(scope.piId()).isNull();
        assertThat(scope.deptIds()).isEmpty();
        assertThat(scope.grantedProjectIds()).isEmpty();
    }

    @Test
    void inactiveActiveRoleIsIgnored() {
        when(userRoleQueryPort.findValidAssignments(USER_ID_TEXT, at()))
                .thenReturn(List.of(assignment(ROLE_ADMIN, null)));
        when(tempGrantQueryPort.findValidByGrantee(USER_ID_TEXT, at())).thenReturn(List.of());

        ProjectScope scope = resolver.resolve(USER_ID, UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"),
                REQUEST_TIME, ProjectQuery.all());

        assertThat(scope.scopeAll()).isFalse();
        verify(roleQueryPort, never()).findByIds(any());
    }
}
