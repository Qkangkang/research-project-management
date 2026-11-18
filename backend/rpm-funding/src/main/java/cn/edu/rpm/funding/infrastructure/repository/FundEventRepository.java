package cn.edu.rpm.funding.infrastructure.repository;

import cn.edu.rpm.funding.infrastructure.entity.FundEventEntity;

import java.util.List;
import java.util.Optional;

/**
 * {@code fund_event} 资金事件仓储（T-FUND-006）。
 *
 * <p>只追加：接口不提供 update/delete，保证事件不可变（数据库触发器兜底）。</p>
 */
public interface FundEventRepository {

    /**
     * 追加资金事件。
     *
     * @param event 待追加的事件 PO
     */
    void insert(FundEventEntity event);

    /**
     * 按主键查询事件。
     *
     * @param id 事件 ID
     * @return 命中的事件；不存在返回空
     */
    Optional<FundEventEntity> findById(String id);

    /**
     * 按幂等键查询事件（重复投递返回原事件）。
     *
     * @param idempotencyKey 幂等键
     * @return 命中的事件；不存在返回空
     */
    Optional<FundEventEntity> findByIdempotencyKey(String idempotencyKey);

    /**
     * 按项目与事件类型查询（发生时间升序，供投影重放）。
     *
     * @param projectId 项目 ID
     * @param eventType 事件类型
     * @return 事件列表；无命中返回空列表
     */
    List<FundEventEntity> findByProjectIdAndEventType(String projectId, String eventType);

    /**
     * 按项目查询全部事件（发生时间升序，供余额重建）。
     *
     * @param projectId 项目 ID
     * @return 事件列表；无命中返回空列表
     */
    List<FundEventEntity> findByProjectId(String projectId);
}
