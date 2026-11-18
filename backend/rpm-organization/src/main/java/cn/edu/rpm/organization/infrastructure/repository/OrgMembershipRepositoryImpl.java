package cn.edu.rpm.organization.infrastructure.repository;

import cn.edu.rpm.organization.infrastructure.entity.OrgMembershipEntity;
import cn.edu.rpm.organization.infrastructure.mapper.OrgMembershipMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * {@code org_membership} 人员归属仓储实现（T-ORG-002）。
 *
 * <p>基于 MyBatis-Plus {@link OrgMembershipMapper} 的薄委托层；查询条件以
 * {@link LambdaQueryWrapper} 按实体注解生成，无手写 SQL。</p>
 */
@Repository
public class OrgMembershipRepositoryImpl implements OrgMembershipRepository {

    private final OrgMembershipMapper orgMembershipMapper;

    public OrgMembershipRepositoryImpl(OrgMembershipMapper orgMembershipMapper) {
        this.orgMembershipMapper = orgMembershipMapper;
    }

    @Override
    public void insert(OrgMembershipEntity membership) {
        orgMembershipMapper.insert(membership);
    }

    @Override
    public List<OrgMembershipEntity> findByUserId(String userId) {
        return orgMembershipMapper.selectList(
                new LambdaQueryWrapper<OrgMembershipEntity>().eq(OrgMembershipEntity::getUserId, userId));
    }

    @Override
    public Optional<OrgMembershipEntity> findPrimaryByUserId(String userId) {
        return Optional.ofNullable(orgMembershipMapper.selectOne(new LambdaQueryWrapper<OrgMembershipEntity>()
                .eq(OrgMembershipEntity::getUserId, userId)
                .eq(OrgMembershipEntity::getIsPrimary, Boolean.TRUE)));
    }
}
