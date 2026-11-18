package cn.edu.rpm.iam.domain.port;

import cn.edu.rpm.iam.domain.model.RoleAssignmentView;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 用户角色关联查询端口（Batch-4 / T-IAM-008、T-IAM-009）。
 *
 * <p>领域层通过本端口获取指定时点有效的角色关联，不直接依赖
 * {@code iam_user_role} 的 PO 或 Repository 实现（architecture-A：domain 不依赖 infrastructure）。
 * 有效口径由适配器保证：{@code status=ACTIVE} 且 {@code valid_from<=at<valid_to}。</p>
 */
public interface UserRoleQueryPort {

    /**
     * 查询用户在指定时点的全部有效角色关联。
     *
     * @param userId 用户 ID（uuid 文本）
     * @param at     判定时点
     * @return 有效关联列表；无关联返回空列表（不返回 {@code null}）
     */
    List<RoleAssignmentView> findValidAssignments(String userId, OffsetDateTime at);
}
