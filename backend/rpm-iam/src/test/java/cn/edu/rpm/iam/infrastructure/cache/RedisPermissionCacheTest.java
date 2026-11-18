package cn.edu.rpm.iam.infrastructure.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link RedisPermissionCache} 纯单元测试（T-IAM-008 缓存契约先行测试，技术文档 06 §1.1）。
 *
 * <p>锚点：key 固定为 {@code rpm:perm:user:{userId}:role:{roleId}}；value 为权限码 JSON 数组；
 * TTL 5 分钟；Redis 不可用时读降级为空、写降级忽略（RULE-CA1/CA2）；角色变更按用户扫描删除
 * 全部角色快照（TC-IAM-025）。</p>
 */
@ExtendWith(MockitoExtension.class)
class RedisPermissionCacheTest {

    private static final String USER_ID = "0123456789abcdef0123456789abcdef";
    private static final String ROLE_ID = "11111111111111111111111111111111";
    private static final String KEY = "rpm:perm:user:" + USER_ID + ":role:" + ROLE_ID;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RedisPermissionCache cache;

    @BeforeEach
    void setUp() {
        cache = new RedisPermissionCache(redisTemplate, new ObjectMapper(), Duration.ofMinutes(5));
    }

    @Test
    void readsPermissionCodesFromDocumentedKey() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(KEY)).thenReturn("[\"project:view\",\"fund:expense:submit\"]");

        Optional<Set<String>> codes = cache.findPermissionCodes(USER_ID, ROLE_ID);

        assertThat(codes).contains(Set.of("project:view", "fund:expense:submit"));
    }

    @Test
    void returnsEmptyWhenKeyMissing() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(KEY)).thenReturn(null);

        assertThat(cache.findPermissionCodes(USER_ID, ROLE_ID)).isEmpty();
    }

    @Test
    void fallsBackToEmptyWhenRedisUnavailable() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenThrow(new RedisConnectionFailureException("redis down"));

        assertThat(cache.findPermissionCodes(USER_ID, ROLE_ID)).isEmpty();
    }

    @Test
    void writesJsonSnapshotWithFiveMinuteTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        cache.putPermissionCodes(USER_ID, ROLE_ID, Set.of("project:view"));

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(eq(KEY), jsonCaptor.capture(), eq(Duration.ofMinutes(5)));
        assertThat(jsonCaptor.getValue()).contains("project:view");
    }

    @Test
    void writeFailureDoesNotBreakMainFlow() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        doThrow(new RedisConnectionFailureException("redis down"))
                .when(valueOperations).set(anyString(), anyString(), any(Duration.class));

        assertThatCode(() -> cache.putPermissionCodes(USER_ID, ROLE_ID, Set.of("project:view")))
                .doesNotThrowAnyException();
    }

    @Test
    void evictsAllRoleSnapshotsOfUserByScan() {
        Set<String> keys = Set.of(KEY, "rpm:perm:user:" + USER_ID + ":role:other-role");
        when(redisTemplate.execute(any(RedisCallback.class))).thenReturn(keys);

        cache.evictUser(USER_ID);

        verify(redisTemplate).delete(keys);
    }
}
