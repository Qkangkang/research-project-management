package cn.edu.rpm.funding.infrastructure.repository;

import cn.edu.rpm.funding.infrastructure.entity.BudgetVersionEntity;

import java.util.List;
import java.util.Optional;

/**
 * {@code budget_version} 预算版本仓储（T-FUND-001）。
 */
public interface BudgetVersionRepository {

    /**
     * 插入预算版本。
     *
     * @param version 待插入的预算版本 PO
     */
    void insert(BudgetVersionEntity version);

    /**
     * 按主键查询预算版本。
     *
     * @param id 预算版本 ID
     * @return 命中的版本；不存在返回空
     */
    Optional<BudgetVersionEntity> findById(String id);

    /**
     * 按项目与状态查询预算版本（版本号降序）。
     *
     * @param projectId 项目 ID
     * @param status    状态
     * @return 版本列表；无命中返回空列表
     */
    List<BudgetVersionEntity> findByProjectIdAndStatus(String projectId, String status);

    /**
     * 按项目查询全部预算版本（版本号降序）。
     *
     * @param projectId 项目 ID
     * @return 版本列表；无命中返回空列表
     */
    List<BudgetVersionEntity> findByProjectId(String projectId);

    /**
     * 查询项目当前生效预算版本。
     *
     * @param projectId 项目 ID
     * @return 命中的 {@code EFFECTIVE} 版本；不存在返回空
     */
    Optional<BudgetVersionEntity> findEffectiveByProjectId(String projectId);

    /**
     * 按乐观锁更新预算版本。
     *
     * @param version 携带主键与旧版本的预算版本 PO
     * @return 受影响行数；0 表示版本冲突或记录不存在
     */
    int update(BudgetVersionEntity version);
}
