package cn.edu.rpm.iam.domain.port;

import java.util.Optional;
import java.util.Set;

/**
 * 功能权限快照缓存端口（技术文档 06 §1.1，key {@code rpm:perm:user:{userId}:role:{roleId}}）。
 *
 * <p>领域层只声明契约，具体 Redis 实现在 infrastructure；TTL 由适配器外化配置
 * （默认 5 分钟），领域层不感知缓存介质。约定：</p>
 * <ul>
 *   <li>读失败/未命中返回 {@link Optional#empty()}，调用方降级回源数据库（RULE-CA1）；</li>
 *   <li>写失败不得影响主链路（TTL 与角色变更失效兜底）；</li>
 *   <li>角色/授权变更时按用户清理其全部角色快照（TC-IAM-025）。</li>
 * </ul>
 */
public interface PermissionCache {

    /**
     * 读取指定用户在指定角色下的权限码快照。
     *
     * @param userId 用户 ID
     * @param roleId 角色 ID
     * @return 命中的权限码集合；未命中或缓存不可用返回空 {@link Optional}
     */
    Optional<Set<String>> findPermissionCodes(String userId, String roleId);

    /**
     * 写入权限码快照（TTL 由实现配置，禁止永不过期）。
     *
     * @param userId          用户 ID
     * @param roleId          角色 ID
     * @param permissionCodes 权限码集合
     */
    void putPermissionCodes(String userId, String roleId, Set<String> permissionCodes);

    /**
     * 清理指定用户的全部角色权限快照（角色变更/临时授权变更后调用）。
     *
     * @param userId 用户 ID
     */
    void evictUser(String userId);
}
