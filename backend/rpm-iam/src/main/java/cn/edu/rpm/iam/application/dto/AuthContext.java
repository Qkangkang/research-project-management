package cn.edu.rpm.iam.application.dto;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 请求级授权上下文（T-IAM-007，技术文档 03 §2.1）。
 *
 * <p>由接入层认证成功后构建（会话建立时通过 {@code FunctionPermissionResolver} 装载权限快照），
 * 之后在应用服务/领域服务间透传，禁止各层自行从请求头或前端参数推断身份。</p>
 *
 * <p>不可变值对象：集合字段在构造时防御性复制为不可变集合；{@code requestTime} 是授权判定的
 * 唯一时间基准（临时授权有效期、角色有效期均以此为准，禁止使用 {@code now()} 隐式取值）。</p>
 *
 * @param userId               当前操作人用户 ID（按人员 ID 判断，不按账号名）
 * @param activeRoleId         当前生效角色 ID；为空时按全部有效角色解析数据范围
 * @param employeeNo           工号（审计/展示用）
 * @param permissionCodes      功能权限快照（如 {@code project:view}）
 * @param delegatedFromUserIds 有效委托的委托人用户 ID 集合（代理审批时使用）
 * @param requestTime          请求时点（授权时间基准）
 */
public record AuthContext(
        UUID userId,
        UUID activeRoleId,
        String employeeNo,
        Set<String> permissionCodes,
        Set<UUID> delegatedFromUserIds,
        Instant requestTime
) {

    /** 规范化集合字段并校验关键字段。 */
    public AuthContext {
        Objects.requireNonNull(userId, "userId 不能为空");
        Objects.requireNonNull(requestTime, "requestTime 不能为空");
        permissionCodes = permissionCodes == null ? Set.of() : Set.copyOf(permissionCodes);
        delegatedFromUserIds = delegatedFromUserIds == null ? Set.of() : Set.copyOf(delegatedFromUserIds);
    }

    /**
     * 判断功能权限快照中是否包含指定权限码。
     *
     * @param permissionCode 权限码，如 {@code project:view}
     * @return 包含返回 {@code true}
     */
    public boolean hasPermission(String permissionCode) {
        return permissionCode != null && permissionCodes.contains(permissionCode);
    }
}
