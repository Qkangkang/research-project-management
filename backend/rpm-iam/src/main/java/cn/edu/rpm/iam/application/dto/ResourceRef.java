package cn.edu.rpm.iam.application.dto;

import cn.edu.rpm.iam.domain.model.ResourceTypes;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 被访问资源的最小授权引用（技术文档 03 §2.1 {@code ResourceRef}）。
 *
 * <p>只承载授权判定所需的数据范围维度（负责人 / 部门 / 成员），不承载业务正文；
 * 调用方在应用服务入口加载资源后构造，避免授权服务反向依赖业务模块。
 * {@code memberUserIds} 由调用方从 {@code project_member} 当前有效成员装载。</p>
 *
 * <p>对非项目资源（支出、评审等）仍以其所属项目维度授权，{@code resourceType}
 * 决定字段脱敏策略（见 {@code FieldPolicyCodes.forResourceType}）。</p>
 *
 * @param resourceType   资源类型，取 {@link ResourceTypes} 常量
 * @param resourceId     资源 ID（项目场景为项目 ID）
 * @param piId           所属项目负责人用户 ID；非项目资源可为空
 * @param deptId         所属部门组织 ID；可为空
 * @param memberUserIds  所属项目当前有效成员用户 ID 集合
 */
public record ResourceRef(
        String resourceType,
        UUID resourceId,
        UUID piId,
        UUID deptId,
        Set<UUID> memberUserIds
) {

    /** 校验资源类型与资源 ID。 */
    public ResourceRef {
        Objects.requireNonNull(resourceType, "resourceType 不能为空");
        Objects.requireNonNull(resourceId, "resourceId 不能为空");
        memberUserIds = memberUserIds == null ? Set.of() : Set.copyOf(memberUserIds);
    }

    /**
     * 构造项目资源引用。
     *
     * @param projectId     项目 ID
     * @param piId          项目负责人用户 ID
     * @param deptId        项目所属部门组织 ID
     * @param memberUserIds 项目当前有效成员用户 ID 集合
     * @return 资源引用
     */
    public static ResourceRef project(UUID projectId, UUID piId, UUID deptId, Collection<UUID> memberUserIds) {
        return new ResourceRef(ResourceTypes.PROJECT, projectId, piId, deptId, memberUserIds == null
                ? Set.of() : Set.copyOf(memberUserIds));
    }
}
