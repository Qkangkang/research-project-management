package cn.edu.rpm.project.infrastructure.repository;

import cn.edu.rpm.project.infrastructure.entity.ProjectBaselineEntity;

import java.util.Optional;

/**
 * {@code project_baseline} 项目基线仓储（T-PRJ-003）。
 *
 * <p>同一项目同一时刻只有一个 {@code EFFECTIVE} 基线，由 V04 部分唯一索引兜底；
 * 插入冲突时抛出 {@link org.springframework.dao.DuplicateKeyException}。</p>
 */
public interface ProjectBaselineRepository {

    /**
     * 插入基线。
     *
     * @param baseline 待插入的基线 PO
     */
    void insert(ProjectBaselineEntity baseline);

    /**
     * 按主键查询基线。
     *
     * @param id 基线 ID
     * @return 命中的基线；不存在返回空
     */
    Optional<ProjectBaselineEntity> findById(String id);

    /**
     * 按项目 + 版本号查询基线（uk_project_baseline_no）。
     *
     * @param projectId 项目 ID
     * @param versionNo 版本号
     * @return 命中的基线；不存在返回空
     */
    Optional<ProjectBaselineEntity> findByProjectIdAndVersionNo(String projectId, int versionNo);

    /**
     * 查询项目当前生效基线（status = EFFECTIVE，取版本号最大者）。
     *
     * @param projectId 项目 ID
     * @return 生效基线；未生成返回空
     */
    Optional<ProjectBaselineEntity> findEffectiveByProjectId(String projectId);

    /**
     * 查询项目当前最大基线版本号（无基线返回 0）。
     *
     * @param projectId 项目 ID
     * @return 最大版本号
     */
    int findMaxVersionNo(String projectId);

    /**
     * 按乐观锁更新基线。
     *
     * @param baseline 携带主键与旧版本的基线 PO
     * @return 受影响行数；0 表示版本冲突或记录不存在
     */
    int update(ProjectBaselineEntity baseline);
}
