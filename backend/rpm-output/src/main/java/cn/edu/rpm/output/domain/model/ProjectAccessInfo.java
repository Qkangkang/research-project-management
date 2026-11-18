package cn.edu.rpm.output.domain.model;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 项目数据范围最小引用（T-OUT-012；与 {@code ResourceRef} 的数据范围维度一致）。
 *
 * <p>由 rpm-boot 的项目访问适配器从 {@code project} / {@code project_member}
 * 装载，成果模块只依赖本端口模型，不直接访问项目表或仓储。</p>
 *
 * @param projectId     项目 ID
 * @param piId          项目负责人用户 ID
 * @param deptId        项目所属部门 ID
 * @param memberUserIds 当前有效成员用户 ID 集合
 */
public record ProjectAccessInfo(
        UUID projectId,
        UUID piId,
        UUID deptId,
        Set<UUID> memberUserIds
) {

    /** 规范化成员集合。 */
    public ProjectAccessInfo {
        Objects.requireNonNull(projectId, "projectId 不能为空");
        memberUserIds = memberUserIds == null ? Set.of() : Set.copyOf(memberUserIds);
    }
}
