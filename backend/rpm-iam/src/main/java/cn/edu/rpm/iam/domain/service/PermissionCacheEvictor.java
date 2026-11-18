package cn.edu.rpm.iam.domain.service;

import cn.edu.rpm.common.util.UuidUtils;
import cn.edu.rpm.iam.domain.port.PermissionCache;

import java.util.Objects;
import java.util.UUID;

/**
 * 权限快照失效器（技术文档 06 §1.1「角色/授权/组织变更主动 DEL」、TC-IAM-025）。
 *
 * <p>角色授权变更（{@code iam_user_role} 新增/撤销/过期）或临时授权变更后调用，
 * 删除该用户全部角色维度的权限快照；下一次请求由 {@link FunctionPermissionResolver}
 * 回源数据库并按新角色重建，保证"角色变更后首次请求即按新角色返回"。</p>
 */
public class PermissionCacheEvictor {

    private final PermissionCache permissionCache;

    public PermissionCacheEvictor(PermissionCache permissionCache) {
        this.permissionCache = Objects.requireNonNull(permissionCache, "permissionCache 不能为空");
    }

    /**
     * 处理角色变更事件：失效该用户全部权限快照。
     *
     * @param userId 发生角色变更的用户 ID
     */
    public void onRoleChanged(UUID userId) {
        Objects.requireNonNull(userId, "userId 不能为空");
        permissionCache.evictUser(UuidUtils.formatCompact(userId));
    }
}
