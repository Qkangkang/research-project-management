package cn.edu.rpm.workflow.infrastructure.repository;

import cn.edu.rpm.workflow.infrastructure.entity.WfDelegationEntity;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * {@code wf_delegation} 代理审批仓储（T-WF-006）。
 *
 * <p>有效委托语义：{@code status = ACTIVE} 且
 * {@code valid_from <= at < valid_to}；过期/撤销委托不返回。</p>
 */
public interface WfDelegationRepository {

    /**
     * 插入代理委托。
     *
     * @param delegation 待插入的委托 PO
     */
    void insert(WfDelegationEntity delegation);

    /**
     * 查询指定代理人在指定时点有效的委托列表。
     *
     * <p>过滤条件：{@code delegate_id}、{@code status = ACTIVE}、
     * {@code valid_from <= at}、{@code valid_to > at}；
     * 查询列与 idx_wf_delegation_delegate_valid 对齐。</p>
     *
     * @param delegateId 代理人用户 ID
     * @param at         判定时点
     * @return 有效委托列表；无命中返回空列表
     */
    List<WfDelegationEntity> findValidByDelegate(String delegateId, OffsetDateTime at);
}
