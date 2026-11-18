package cn.edu.rpm.project.infrastructure.repository;

import cn.edu.rpm.project.infrastructure.entity.ProjectMemberEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * {@code project_member} 项目成员仓储（T-PRJ-002）。
 *
 * <p>有效成员口径：{@code status = ACTIVE} 且 {@code valid_from <= at} 且
 * （{@code valid_to} 为空或 {@code valid_to > at}）。</p>
 */
public interface ProjectMemberRepository {

    /**
     * 插入成员记录。
     *
     * @param member 待插入的成员 PO
     */
    void insert(ProjectMemberEntity member);

    /**
     * 按主键查询成员记录。
     *
     * @param id 成员记录 ID
     * @return 命中的成员记录；不存在返回空
     */
    Optional<ProjectMemberEntity> findById(String id);

    /**
     * 查询项目全部成员记录（含历史失效记录）。
     *
     * @param projectId 项目 ID
     * @return 成员记录列表；无命中返回空列表
     */
    List<ProjectMemberEntity> findByProjectId(String projectId);

    /**
     * 查询项目在指定时点的有效成员。
     *
     * @param projectId 项目 ID
     * @param at        时点
     * @return 有效成员列表；无命中返回空列表
     */
    List<ProjectMemberEntity> findActiveByProjectId(String projectId, OffsetDateTime at);

    /**
     * 查询项目在指定时点是否已有同角色有效成员（重复角色校验，TC-PRJ-015）。
     *
     * @param projectId   项目 ID
     * @param userId      成员用户 ID
     * @param projectRole 项目角色
     * @param at          时点
     * @return 存在返回 {@code true}
     */
    boolean existsActiveMember(String projectId, String userId, String projectRole, OffsetDateTime at);

    /**
     * 查询用户在指定时点有有效成员记录的项目 ID 集合（数据范围用）。
     *
     * @param userId 用户 ID
     * @param at     时点
     * @return 项目 ID 列表；无命中返回空列表
     */
    List<String> findActiveProjectIdsByUserId(String userId, OffsetDateTime at);

    /**
     * 按乐观锁更新成员记录。
     *
     * @param member 携带主键与旧版本的成员 PO
     * @return 受影响行数；0 表示版本冲突或记录不存在
     */
    int update(ProjectMemberEntity member);
}
