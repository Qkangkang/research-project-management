package cn.edu.rpm.project.infrastructure.repository;

import cn.edu.rpm.project.infrastructure.entity.MilestoneEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * {@code milestone} 里程碑仓储（T-PRJ-004）。
 */
public interface MilestoneRepository {

    /**
     * 插入里程碑。
     *
     * @param milestone 待插入的里程碑 PO
     */
    void insert(MilestoneEntity milestone);

    /**
     * 批量插入里程碑（每批 ≤500，RULE-DB3）。
     *
     * @param milestones 里程碑列表
     */
    void insertBatch(List<MilestoneEntity> milestones);

    /**
     * 按主键查询里程碑。
     *
     * @param id 里程碑 ID
     * @return 命中的里程碑；不存在返回空
     */
    Optional<MilestoneEntity> findById(String id);

    /**
     * 按项目 + 基线查询里程碑（同一次立项/变更生成的同一基线）。
     *
     * @param projectId  项目 ID
     * @param baselineId 基线 ID
     * @return 里程碑列表（按计划日期升序）；无命中返回空列表
     */
    List<MilestoneEntity> findByProjectIdAndBaselineId(String projectId, String baselineId);

    /**
     * 按责任人与状态查询待办里程碑。
     *
     * @param ownerId 责任人用户 ID
     * @param status  里程碑状态
     * @return 里程碑列表；无命中返回空列表
     */
    List<MilestoneEntity> findByOwnerIdAndStatus(String ownerId, String status);

    /**
     * 查询指定截止日期前未完成的里程碑（到期提醒/风险扫描）。
     *
     * @param projectId 项目 ID
     * @param date      当前计划日期上界（含）
     * @return 里程碑列表；无命中返回空列表
     */
    List<MilestoneEntity> findUnfinishedDueBy(String projectId, LocalDate date);

    /**
     * 按主键加行级排他锁查询（延期申请入口）。
     *
     * @param id 里程碑 ID
     * @return 加锁命中的里程碑；不存在返回空
     */
    Optional<MilestoneEntity> selectForUpdate(String id);

    /**
     * 按乐观锁更新里程碑。
     *
     * @param milestone 携带主键与旧版本的里程碑 PO
     * @return 受影响行数；0 表示版本冲突或记录不存在
     */
    int update(MilestoneEntity milestone);
}
