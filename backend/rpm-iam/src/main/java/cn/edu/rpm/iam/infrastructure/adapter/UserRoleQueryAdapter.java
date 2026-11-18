package cn.edu.rpm.iam.infrastructure.adapter;

import cn.edu.rpm.iam.domain.model.RoleAssignmentView;
import cn.edu.rpm.iam.domain.port.UserRoleQueryPort;
import cn.edu.rpm.iam.infrastructure.entity.IamUserRoleEntity;
import cn.edu.rpm.iam.infrastructure.repository.IamUserRoleRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * {@link UserRoleQueryPort} 的持久化适配器（Batch-4 / T-IAM-008、T-IAM-009）。
 *
 * <p>薄投影层：只把 {@code iam_user_role} 的有效关联映射为领域视图，不承载业务判断；
 * 有效期与状态过滤由数据层查询保证（与 Batch-3 契约一致）。</p>
 */
@Repository
public class UserRoleQueryAdapter implements UserRoleQueryPort {

    private final IamUserRoleRepository userRoleRepository;

    public UserRoleQueryAdapter(IamUserRoleRepository userRoleRepository) {
        this.userRoleRepository = userRoleRepository;
    }

    @Override
    public List<RoleAssignmentView> findValidAssignments(String userId, OffsetDateTime at) {
        return userRoleRepository.findValidByUserId(userId, at).stream()
                .map(UserRoleQueryAdapter::toView)
                .toList();
    }

    private static RoleAssignmentView toView(IamUserRoleEntity entity) {
        return new RoleAssignmentView(entity.getRoleId(), entity.getOrgId());
    }
}
