package cn.edu.rpm.workflow.infrastructure.repository;

import cn.edu.rpm.workflow.infrastructure.entity.WfDelegationEntity;
import cn.edu.rpm.workflow.infrastructure.mapper.WfDelegationMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * {@code wf_delegation} 代理审批仓储实现（T-WF-006）。
 *
 * <p>有效期语义与 Batch-4 GrantValidityGuard 一致：{@code valid_from <= at < valid_to}，
 * 委托状态必须为 {@code ACTIVE}；过期与撤销记录不返回。</p>
 */
@Repository
public class WfDelegationRepositoryImpl implements WfDelegationRepository {

    private final WfDelegationMapper wfDelegationMapper;

    public WfDelegationRepositoryImpl(WfDelegationMapper wfDelegationMapper) {
        this.wfDelegationMapper = wfDelegationMapper;
    }

    @Override
    public void insert(WfDelegationEntity delegation) {
        wfDelegationMapper.insert(delegation);
    }

    @Override
    public List<WfDelegationEntity> findValidByDelegate(String delegateId, OffsetDateTime at) {
        return wfDelegationMapper.selectList(new LambdaQueryWrapper<WfDelegationEntity>()
                .eq(WfDelegationEntity::getDelegateId, delegateId)
                .eq(WfDelegationEntity::getStatus, WfDelegationEntity.STATUS_ACTIVE)
                .le(WfDelegationEntity::getValidFrom, at)
                .gt(WfDelegationEntity::getValidTo, at));
    }
}
