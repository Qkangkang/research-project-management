package cn.edu.rpm.project.infrastructure.repository;

import cn.edu.rpm.project.infrastructure.entity.MilestoneDelayRequestEntity;

import java.util.List;
import java.util.Optional;

/**
 * {@code milestone_delay_request} 延期申请仓储（T-PRJ-005）。
 */
public interface MilestoneDelayRequestRepository {

    /**
     * 插入延期申请。
     *
     * @param request 待插入的申请 PO
     */
    void insert(MilestoneDelayRequestEntity request);

    /**
     * 按主键查询申请。
     *
     * @param id 申请 ID
     * @return 命中的申请；不存在返回空
     */
    Optional<MilestoneDelayRequestEntity> findById(String id);

    /**
     * 查询里程碑当前 PENDING 申请（同一里程碑仅允许一条）。
     *
     * @param milestoneId 里程碑 ID
     * @return PENDING 申请；无则返回空
     */
    Optional<MilestoneDelayRequestEntity> findPendingByMilestoneId(String milestoneId);

    /**
     * 查询里程碑延期历史（按创建时间倒序）。
     *
     * @param milestoneId 里程碑 ID
     * @return 申请列表；无命中返回空列表
     */
    List<MilestoneDelayRequestEntity> findByMilestoneId(String milestoneId);

    /**
     * 按乐观锁更新申请。
     *
     * @param request 携带主键与旧版本的申请 PO
     * @return 受影响行数；0 表示版本冲突或记录不存在
     */
    int update(MilestoneDelayRequestEntity request);
}
