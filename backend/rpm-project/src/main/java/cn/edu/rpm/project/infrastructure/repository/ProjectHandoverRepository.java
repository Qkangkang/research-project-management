package cn.edu.rpm.project.infrastructure.repository;

import cn.edu.rpm.project.infrastructure.entity.ProjectHandoverEntity;

import java.util.List;
import java.util.Optional;

/**
 * {@code project_handover} 负责人交接仓储（T-PRJ-009）。
 */
public interface ProjectHandoverRepository {

    /**
     * 插入交接单。
     *
     * @param handover 待插入的交接单 PO
     */
    void insert(ProjectHandoverEntity handover);

    /**
     * 按主键查询交接单。
     *
     * @param id 交接单 ID
     * @return 命中的交接单；不存在返回空
     */
    Optional<ProjectHandoverEntity> findById(String id);

    /**
     * 按项目与状态查询交接单。
     *
     * @param projectId 项目 ID
     * @param status    交接状态
     * @return 交接单列表；无命中返回空列表
     */
    List<ProjectHandoverEntity> findByProjectIdAndStatus(String projectId, String status);

    /**
     * 查询指定时刻前应生效的交接单（惰性/定时生效扫描）。
     *
     * @param status      交接状态（通常 APPROVED）
     * @param effectiveAt 生效时间上界（含）
     * @return 到期待生效的交接单；无命中返回空列表
     */
    List<ProjectHandoverEntity> findByStatusAndEffectiveAtBefore(String status,
                                                                 java.time.OffsetDateTime effectiveAt);

    /**
     * 按乐观锁更新交接单。
     *
     * @param handover 携带主键与旧版本的交接单 PO
     * @return 受影响行数；0 表示版本冲突或记录不存在
     */
    int update(ProjectHandoverEntity handover);
}
