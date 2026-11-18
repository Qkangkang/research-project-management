package cn.edu.rpm.project.infrastructure.repository;

import cn.edu.rpm.project.infrastructure.entity.ChangeRequestEntity;

import java.util.List;
import java.util.Optional;

/**
 * {@code change_request} 变更主档仓储（T-PRJ-007）。
 */
public interface ChangeRequestRepository {

    /**
     * 插入变更单。
     *
     * @param change 待插入的变更单 PO
     */
    void insert(ChangeRequestEntity change);

    /**
     * 按主键查询变更单。
     *
     * @param id 变更单 ID
     * @return 命中的变更单；不存在返回空
     */
    Optional<ChangeRequestEntity> findById(String id);

    /**
     * 按主键加行级排他锁查询（审批回调幂等）。
     *
     * @param id 变更单 ID
     * @return 加锁命中的变更单；不存在返回空
     */
    Optional<ChangeRequestEntity> selectForUpdate(String id);

    /**
     * 按项目与状态查询变更单。
     *
     * @param projectId 项目 ID
     * @param status    变更状态
     * @return 变更单列表；无命中返回空列表
     */
    List<ChangeRequestEntity> findByProjectIdAndStatus(String projectId, String status);

    /**
     * 按乐观锁更新变更单。
     *
     * @param change 携带主键与旧版本的变更单 PO
     * @return 受影响行数；0 表示版本冲突或记录不存在
     */
    int update(ChangeRequestEntity change);
}
