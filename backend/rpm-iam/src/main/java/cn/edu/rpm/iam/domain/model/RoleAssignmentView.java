package cn.edu.rpm.iam.domain.model;

import java.util.Objects;

/**
 * 用户角色关联的领域只读视图（Batch-4 / T-IAM-008、T-IAM-009）。
 *
 * <p>隔离持久化模型：领域服务只依赖本视图与 {@code UserRoleQueryPort}，
 * 由基础设施适配器负责从 {@code iam_user_role} 投影；{@code orgId} 为角色授权部门，
 * 可为空（空表示按人员主部门解析）。</p>
 *
 * @param roleId 角色 ID（32 位或标准 uuid 文本，原样透传）
 * @param orgId  角色授权部门 ID；可为 {@code null}
 */
public record RoleAssignmentView(String roleId, String orgId) {

    /**
     * 紧凑构造器：角色 ID 必填。
     */
    public RoleAssignmentView {
        Objects.requireNonNull(roleId, "roleId 不能为空");
    }
}
