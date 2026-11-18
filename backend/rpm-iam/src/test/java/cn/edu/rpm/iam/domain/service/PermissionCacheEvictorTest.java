package cn.edu.rpm.iam.domain.service;

import cn.edu.rpm.iam.domain.port.PermissionCache;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link PermissionCacheEvictor} 纯单元测试（TC-IAM-025 先行测试，技术文档 03 §2.4）。
 *
 * <p>验收锚点：用户角色变更后，该用户全部角色权限快照立即删除，其他用户缓存不受影响；
 * 首次请求即按新角色回到数据库重建（由 {@link FunctionPermissionResolverTest} 覆盖）。</p>
 */
class PermissionCacheEvictorTest {

    private static final String USER_ID = "0123456789abcdef0123456789abcdef";
    private static final String OTHER_USER_ID = "ffffffffffffffffffffffffffffffff";

    @Test
    void evictsAllRoleSnapshotsOfChangedUserOnly() {
        RecordingPermissionCache cache = new RecordingPermissionCache();
        cache.putPermissionCodes(USER_ID, "role-1", Set.of("project:view"));
        cache.putPermissionCodes(USER_ID, "role-2", Set.of("fund:expense:submit"));
        cache.putPermissionCodes(OTHER_USER_ID, "role-1", Set.of("project:view"));
        PermissionCacheEvictor evictor = new PermissionCacheEvictor(cache);

        evictor.onRoleChanged(UUID.fromString("01234567-89ab-cdef-0123-456789abcdef"));

        assertThat(cache.findPermissionCodes(USER_ID, "role-1")).isEmpty();
        assertThat(cache.findPermissionCodes(USER_ID, "role-2")).isEmpty();
        assertThat(cache.findPermissionCodes(OTHER_USER_ID, "role-1")).contains(Set.of("project:view"));
    }

    /**
     * 记录型缓存替身：key 为用户 ID 与角色 ID 组合。
     */
    private static final class RecordingPermissionCache implements PermissionCache {

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
