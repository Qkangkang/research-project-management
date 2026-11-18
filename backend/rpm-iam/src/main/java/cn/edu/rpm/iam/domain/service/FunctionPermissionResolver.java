package cn.edu.rpm.iam.domain.service;

import cn.edu.rpm.iam.domain.model.RoleAssignmentView;
import cn.edu.rpm.iam.domain.port.PermissionCache;
import cn.edu.rpm.iam.domain.port.RolePermissionQueryPort;
import cn.edu.rpm.iam.domain.port.UserRoleQueryPort;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.LongAdder;

/**
 * 功能权限解析器（T-IAM-008，技术文档 03 §2.1、06 §1.1）。
 *
 * <p>解析链路：{@code iam_user_role}（校验用户在该时点确有该有效角色）→
 * {@code iam_role_permission} → {@code iam_permission.code}；权限快照写短期缓存
 * （Redis TTL 5 分钟，key {@code rpm:perm:user:{userId}:role:{roleId}}）。
 * 角色变更时由 {@link PermissionCacheEvictor} 主动清理，首次请求即回源重建（TC-IAM-025）。</p>
 *
 * <p>领域层只依赖 {@link UserRoleQueryPort} / {@link RolePermissionQueryPort} 两个端口，
 * 不感知 PO 与 Mapper（architecture-A：domain 不依赖 infrastructure）。</p>
 *
 * <p>降级口径（RULE-CA1）：缓存不可用视为 miss，直接回源数据库，不得放行匿名或空权限；
 * 缓存命中率指标供验收观察（验收阈值 &gt; 80%），不作为鉴权判定条件。</p>
 */
public class FunctionPermissionResolver {

    private final UserRoleQueryPort userRoleQueryPort;

    private final RolePermissionQueryPort rolePermissionQueryPort;

    private final PermissionCache permissionCache;

    private final LongAdder cacheHits = new LongAdder();

    private final LongAdder cacheMisses = new LongAdder();

    public FunctionPermissionResolver(UserRoleQueryPort userRoleQueryPort,
                                      RolePermissionQueryPort rolePermissionQueryPort,
                                      PermissionCache permissionCache) {
        this.userRoleQueryPort = Objects.requireNonNull(userRoleQueryPort, "userRoleQueryPort 不能为空");
        this.rolePermissionQueryPort = Objects.requireNonNull(rolePermissionQueryPort,
                "rolePermissionQueryPort 不能为空");
        this.permissionCache = Objects.requireNonNull(permissionCache, "permissionCache 不能为空");
    }

    /**
     * 解析用户在指定时点、指定角色下的功能权限码集合。
     *
     * <p>角色关联必须在该时点有效（{@code status=ACTIVE} 且有效期内），否则返回空集合且
     * 不写缓存（避免角色刚授予时被负缓存挡住）。</p>
     *
     * @param userId 用户 ID（uuid 文本）
     * @param roleId 角色 ID（uuid 文本）
     * @param at     授权判定时点
     * @return 权限码集合；参数非法、角色失效或无权限点返回空集合
     */
    public Set<String> resolvePermissions(String userId, String roleId, OffsetDateTime at) {
        if (userId == null || userId.isBlank() || roleId == null || roleId.isBlank() || at == null) {
            return Set.of();
        }
        Optional<Set<String>> cached = permissionCache.findPermissionCodes(userId, roleId);
        if (cached.isPresent()) {
            cacheHits.increment();
            return cached.get();
        }
        cacheMisses.increment();
        boolean roleValidAtTime = userRoleQueryPort.findValidAssignments(userId, at).stream()
                .map(RoleAssignmentView::roleId)
                .anyMatch(roleId::equals);
        if (!roleValidAtTime) {
            return Set.of();
        }
        List<String> codes = rolePermissionQueryPort.findPermissionCodesByRoleId(roleId);
        Set<String> permissionCodes = codes == null || codes.isEmpty() ? Set.of() : Set.copyOf(codes);
        permissionCache.putPermissionCodes(userId, roleId, permissionCodes);
        return permissionCodes;
    }

    /**
     * 缓存命中率（命中次数 / 总查询次数），用于验收观察（目标 &gt; 80%）。
     *
     * @return 命中率；尚无查询时返回 0
     */
    public double cacheHitRate() {
        long hits = cacheHits.sum();
        long total = hits + cacheMisses.sum();
        return total == 0 ? 0D : (double) hits / total;
    }
}
