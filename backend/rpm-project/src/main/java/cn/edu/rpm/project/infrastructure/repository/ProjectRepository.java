package cn.edu.rpm.project.infrastructure.repository;

import cn.edu.rpm.project.infrastructure.entity.ProjectEntity;

import java.util.List;
import java.util.Optional;

/**
 * {@code project} 项目主档仓储（T-PRJ-001）。
 *
 * <p>纯单表数据访问契约，不含业务规则。插入撞 {@code uk_project_no} /
 * {@code uk_project_source_version} 时抛出 Spring
 * {@link org.springframework.dao.DuplicateKeyException}；更新以实体
 * {@code version} 旧值作乐观锁条件。</p>
 */
public interface ProjectRepository {

    /**
     * 插入项目主档。
     *
     * @param project 待插入的项目 PO
     */
    void insert(ProjectEntity project);

    /**
     * 按主键查询项目。
     *
     * @param id 项目 ID
     * @return 命中的项目；不存在返回空
     */
    Optional<ProjectEntity> findById(String id);

    /**
     * 按项目编号查询（uk_project_no 唯一）。
     *
     * @param projectNo 项目编号
     * @return 命中的项目；不存在返回空
     */
    Optional<ProjectEntity> findByProjectNo(String projectNo);

    /**
     * 按来源申报版本查询（uk_project_source_version，立项幂等键，TC-APP-029）。
     *
     * @param sourceApplicationVersionId 来源申报版本 ID
     * @return 已生成的项目；尚未立项返回空
     */
    Optional<ProjectEntity> findBySourceApplicationVersionId(String sourceApplicationVersionId);

    /**
     * 判断项目编号是否已存在（编号生成重试，TC-APP-040）。
     *
     * @param projectNo 项目编号
     * @return 存在返回 {@code true}
     */
    boolean existsByProjectNo(String projectNo);

    /**
     * 按负责人与状态查询（索引 idx_project_pi_status）。
     *
     * @param piId   负责人用户 ID
     * @param status 项目状态
     * @return 项目列表；无命中返回空列表
     */
    List<ProjectEntity> findByPiIdAndStatus(String piId, String status);

    /**
     * 按部门与状态查询（索引 idx_project_dept_status）。
     *
     * @param deptId 部门 ID
     * @param status 项目状态
     * @return 项目列表；无命中返回空列表
     */
    List<ProjectEntity> findByDeptIdAndStatus(String deptId, String status);

    /**
     * 按项目类型与状态查询。
     *
     * @param projectType 项目类型
     * @param status      项目状态
     * @return 项目列表；无命中返回空列表
     */
    List<ProjectEntity> findByProjectTypeAndStatus(String projectType, String status);

    /**
     * 按主键加行级排他锁查询（状态迁移/基线切换入口）。
     *
     * @param id 项目 ID
     * @return 加锁命中的项目；不存在返回空
     */
    Optional<ProjectEntity> selectForUpdate(String id);

    /**
     * 按乐观锁更新项目。
     *
     * @param project 携带主键与旧版本的项目 PO
     * @return 受影响行数；0 表示版本冲突或记录不存在
     */
    int update(ProjectEntity project);
}
