package cn.edu.rpm.execution.infrastructure.repository;

import cn.edu.rpm.execution.infrastructure.entity.DeliverableEntity;

import java.util.List;
import java.util.Optional;

/**
 * {@code deliverable} 交付物仓储（T-EXEC-003）。
 */
public interface DeliverableRepository {

    /**
     * 插入交付物。
     *
     * @param deliverable 待插入的交付物 PO
     */
    void insert(DeliverableEntity deliverable);

    /**
     * 按主键查询交付物。
     *
     * @param id 交付物 ID
     * @return 命中的交付物；不存在返回空
     */
    Optional<DeliverableEntity> findById(String id);

    /**
     * 按项目查询交付物列表。
     *
     * @param projectId 项目 ID
     * @return 交付物列表；无命中返回空列表
     */
    List<DeliverableEntity> findByProjectId(String projectId);

    /**
     * 按里程碑查询交付物列表。
     *
     * @param milestoneId 里程碑 ID
     * @return 交付物列表；无命中返回空列表
     */
    List<DeliverableEntity> findByMilestoneId(String milestoneId);

    /**
     * 判断里程碑下是否存在有效交付物（里程碑完成证据校验，T-PRJ-014）。
     *
     * @param projectId   项目 ID
     * @param milestoneId 里程碑 ID
     * @return 存在 {@code ACTIVE} 交付物返回 {@code true}
     */
    boolean existsActiveByMilestoneId(String projectId, String milestoneId);

    /**
     * 按乐观锁更新交付物。
     *
     * @param deliverable 携带主键与旧版本的交付物 PO
     * @return 受影响行数；0 表示版本冲突或记录不存在
     */
    int update(DeliverableEntity deliverable);
}
