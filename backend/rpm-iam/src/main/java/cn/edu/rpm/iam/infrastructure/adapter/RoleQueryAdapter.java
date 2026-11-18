package cn.edu.rpm.iam.infrastructure.adapter;

import cn.edu.rpm.iam.domain.model.RoleView;
import cn.edu.rpm.iam.domain.port.RoleQueryPort;
import cn.edu.rpm.iam.infrastructure.entity.IamRoleEntity;
import cn.edu.rpm.iam.infrastructure.repository.IamRoleRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * {@link RoleQueryPort} 的持久化适配器（Batch-4 / T-IAM-009）。
 *
 * <p>一次批量查询完成角色投影，不解析角色状态（状态过滤属角色管理批次），
 * 领域层只消费角色码与授权范围类型。</p>
 */
@Repository
public class RoleQueryAdapter implements RoleQueryPort {

    private final IamRoleRepository roleRepository;

    public RoleQueryAdapter(IamRoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @Override
    public List<RoleView> findByIds(Collection<String> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return List.of();
        }
        return roleRepository.findByIds(roleIds).stream()
                .map(RoleQueryAdapter::toView)
                .toList();
    }

    private static RoleView toView(IamRoleEntity entity) {
        return new RoleView(entity.getId(), entity.getCode(), entity.getScopeType());
    }
}
