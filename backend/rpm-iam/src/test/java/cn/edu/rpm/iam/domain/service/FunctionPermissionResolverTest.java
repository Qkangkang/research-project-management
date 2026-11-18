package cn.edu.rpm.iam.domain.service;

import cn.edu.rpm.iam.domain.model.RoleAssignmentView;
import cn.edu.rpm.iam.domain.port.PermissionCache;
import cn.edu.rpm.iam.domain.port.RolePermissionQueryPort;
import cn.edu.rpm.iam.domain.port.UserRoleQueryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link FunctionPermissionResolver} 纯单元测试（T-IAM-008 先行测试，技术文档 03 §2.1）。
 *
 * <p>验收锚点：按 {@code user_id + role_id} 解析权限集合；命中缓存后不再回源数据库
 * （缓存命中率 &gt; 80%）；角色有效期失效时不返回权限；角色变更缓存失效后首次请求
 * 即按新权限返回（TC-IAM-025）。依赖以领域端口模拟，不接触 PO/Redis。</p>
 */
@ExtendWith(MockitoExtension.class)
class FunctionPermissionResolverTest {

    private static final String USER_ID = "0123456789abcdef0123456789abcdef";
    private static final String ROLE_PI = "11111111111111111111111111111111";
    private static final OffsetDateTime AT = OffsetDateTime.parse("2026-11-17T10:00:00+08:00");

    @Mock
    private UserRoleQueryPort userRoleQueryPort;

    @Mock
    private RolePermissionQueryPort rolePermissionQueryPort;

    private InMemoryPermissionCache permissionCache;

    private FunctionPermissionResolver resolver;

    @BeforeEach
    void setUp() {
        permissionCache = new InMemoryPermissionCache();
        resolver = new FunctionPermissionResolver(userRoleQueryPort, rolePermissionQueryPort, permissionCache);
    }

    private void stubDatabaseWith(String... permissionCodes) {
        when(userRoleQueryPort.findValidAssignments(USER_ID, AT))
                .thenReturn(List.of(new RoleAssignmentView(ROLE_PI, null)));
        when(rolePermissionQueryPort.findPermissionCodesByRoleId(ROLE_PI))
                .thenReturn(List.of(permissionCodes));
    }

    @Test
    void resolvesPermissionCodesForUserAndRole() {
        stubDatabaseWith("project:view", "fund:expense:submit");

        Set<String> codes = resolver.resolvePermissions(USER_ID, ROLE_PI, AT);

        assertThat(codes).containsExactlyInAnyOrder("project:view", "fund:expense:submit");
        assertThat(permissionCache.findPermissionCodes(USER_ID, ROLE_PI)).contains(codes);
    }

    @Test
    void secondCallIsServedFromCacheWithoutDatabaseAccess() {
        stubDatabaseWith("project:view", "fund:expense:submit");

        resolver.resolvePermissions(USER_ID, ROLE_PI, AT);
        Set<String> cached = resolver.resolvePermissions(USER_ID, ROLE_PI, AT);

        assertThat(cached).containsExactlyInAnyOrder("project:view", "fund:expense:submit");
        verify(userRoleQueryPort, times(1)).findValidAssignments(USER_ID, AT);
        verify(rolePermissionQueryPort, times(1)).findPermissionCodesByRoleId(ROLE_PI);
    }

    @Test
    void cacheHitRateExceedsEightyPercentAfterWarmup() {
        stubDatabaseWith("project:view", "fund:expense:submit");

        for (int i = 0; i < 6; i++) {
            resolver.resolvePermissions(USER_ID, ROLE_PI, AT);
        }

        assertThat(resolver.cacheHitRate()).isGreaterThan(0.8);
    }

    @Test
    void invalidRoleAssignmentYieldsEmptySetAndDoesNotPopulateCache() {
        when(userRoleQueryPort.findValidAssignments(USER_ID, AT)).thenReturn(List.of());

        Set<String> codes = resolver.resolvePermissions(USER_ID, ROLE_PI, AT);

        assertThat(codes).isEmpty();
        assertThat(permissionCache.findPermissionCodes(USER_ID, ROLE_PI)).isEmpty();
        verify(rolePermissionQueryPort, never()).findPermissionCodesByRoleId(any());
    }

    @Test
    void blankArgumentsYieldEmptySet() {
        assertThat(resolver.resolvePermissions(null, ROLE_PI, AT)).isEmpty();
        assertThat(resolver.resolvePermissions(USER_ID, "  ", AT)).isEmpty();
        assertThat(resolver.resolvePermissions(USER_ID, ROLE_PI, null)).isEmpty();
        verify(userRoleQueryPort, never()).findValidAssignments(any(), any());
    }

    /**
     * 纯内存权限缓存替身：避免纯单元测试依赖 Redis（集成测试阶段回归真实适配器）。
     */
    private static final class InMemoryPermissionCache implements PermissionCache {

        private final Map<String, Set<String>> store = new HashMap<>();

        private static String key(String userId, String roleId) {
            return userId + ":" + roleId;
        }

        @Override
        public Optional<Set<String>> findPermissionCodes(String userId, String roleId) {
            return Optional.ofNullable(store.get(key(userId, roleId)));
        }

        @Override
        public void putPermissionCodes(String userId, String roleId, Set<String> permissionCodes) {
            store.put(key(userId, roleId), Set.copyOf(permissionCodes));
        }

        @Override
        public void evictUser(String userId) {
            store.keySet().removeIf(key -> key.startsWith(userId + ":"));
        }
    }
}
