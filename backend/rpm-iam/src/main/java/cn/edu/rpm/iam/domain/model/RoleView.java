package cn.edu.rpm.iam.domain.model;

import java.util.Objects;

/**
 * 角色的领域只读视图（Batch-4 / T-IAM-009）。
 *
 * <p>仅承载数据范围解析所需的三个字段：角色 ID、角色码、授权范围类型；
 * 由基础设施适配器从 {@code iam_role} 投影，避免领域层依赖 PO。</p>
 *
 * @param id        角色 ID
 * @param code      角色码（如 {@code PI}、{@code DEPT_SECRETARY}）
 * @param scopeType 授权范围类型（见 {@link RoleScopeTypes}）
 */
public record RoleView(String id, String code, String scopeType) {

    /**
     * 紧凑构造器：角色 ID 必填。
     */
    public RoleView {
        Objects.requireNonNull(id, "id 不能为空");
    }
}
