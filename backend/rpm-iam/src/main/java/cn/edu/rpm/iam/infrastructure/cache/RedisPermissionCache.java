package cn.edu.rpm.iam.infrastructure.cache;

import cn.edu.rpm.common.util.UuidUtils;
import cn.edu.rpm.iam.domain.port.PermissionCache;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * 功能权限快照 Redis 适配器（技术文档 06 §1.1，T-IAM-008 缓存契约）。
 *
 * <p>Key 固定为 {@code rpm:perm:user:{userId}:role:{roleId}}，Value 为权限码 JSON 数组，
 * TTL 由 {@code rpm.security.permission-cache-ttl} 外化配置（默认 5 分钟，禁止永不过期）。</p>
 *
 * <p>降级口径（RULE-CA1/CA2）：Redis 读失败/反序列化失败 → 返回空（调用方回源 DB）；
 * 写失败 → 记录告警但不阻断主链路（TTL 与角色变更失效兜底）；删除失败 → 记录告警，
 * 由 TTL 兜底，禁止静默。Redis 连接超时由 rpm-boot 的
 * {@code spring.data.redis.timeout / connect-timeout} 统一配置（C-CHECK）。</p>
 */
@Repository
public class RedisPermissionCache implements PermissionCache {

    private static final Logger LOG = LoggerFactory.getLogger(RedisPermissionCache.class);

    private static final String KEY_PREFIX = "rpm:perm:user:";

    private static final String ROLE_SEGMENT = ":role:";

    /** SCAN 单批游标数量；避免阻塞 Redis 主线程（禁止使用 KEYS）。 */
    private static final long SCAN_BATCH = 200L;

    private final StringRedisTemplate redisTemplate;

    private final ObjectMapper objectMapper;

    private final Duration ttl;

    public RedisPermissionCache(StringRedisTemplate redisTemplate,
                                ObjectMapper objectMapper,
                                @Value("${rpm.security.permission-cache-ttl:5m}") Duration ttl) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.ttl = ttl;
    }

    @Override
    public Optional<Set<String>> findPermissionCodes(String userId, String roleId) {
        String key = key(userId, roleId);
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) {
                return Optional.empty();
            }
            String[] codes = objectMapper.readValue(json, String[].class);
            return Optional.of(Set.of(codes));
        } catch (DataAccessException | JsonProcessingException ex) {
            LOG.warn("权限缓存读取失败，降级回源数据库 key={} error={}", key, ex.toString());
            return Optional.empty();
        }
    }

    @Override
    public void putPermissionCodes(String userId, String roleId, Set<String> permissionCodes) {
        String key = key(userId, roleId);
        try {
            String json = objectMapper.writeValueAsString(permissionCodes);
            redisTemplate.opsForValue().set(key, json, ttl);
        } catch (DataAccessException | JsonProcessingException ex) {
            LOG.warn("权限缓存写入失败（忽略，TTL 兜底） key={} error={}", key, ex.toString());
        }
    }

    @Override
    public void evictUser(String userId) {
        String pattern = KEY_PREFIX + normalize(userId) + ROLE_SEGMENT + "*";
        try {
            Set<String> keys = scanKeys(pattern);
            if (!keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (DataAccessException ex) {
            LOG.warn("权限缓存失效失败（TTL 兜底） userId={} error={}", userId, ex.toString());
        }
    }

    private Set<String> scanKeys(String pattern) {
        Set<String> keys = redisTemplate.execute((RedisCallback<Set<String>>) connection -> {
            Set<String> collected = new HashSet<>();
            ScanOptions options = ScanOptions.scanOptions().match(pattern).count(SCAN_BATCH).build();
            try (Cursor<byte[]> cursor = connection.keyCommands().scan(options)) {
                while (cursor.hasNext()) {
                    collected.add(new String(cursor.next(), StandardCharsets.UTF_8));
                }
            }
            return collected;
        });
        return keys == null ? Set.of() : keys;
    }

    private static String key(String userId, String roleId) {
        return KEY_PREFIX + normalize(userId) + ROLE_SEGMENT + normalize(roleId);
    }

    /**
     * 统一 cache key 中的 uuid 文本形态（32 位无连字符），保证写入与失效 key 一致；
     * 非 uuid 文本原样保留（仅用于防御异常入参，不参与业务判定）。
     */
    private static String normalize(String uuidText) {
        try {
            return UuidUtils.formatCompact(UuidUtils.parse(uuidText));
        } catch (IllegalArgumentException ex) {
            return uuidText;
        }
    }
}
