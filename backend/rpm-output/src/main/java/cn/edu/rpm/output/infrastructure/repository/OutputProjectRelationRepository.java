package cn.edu.rpm.output.infrastructure.repository;

import cn.edu.rpm.output.infrastructure.entity.OutputProjectRelationEntity;

import java.util.List;
import java.util.Optional;

/**
 * {@code output_project_relation} 成果-项目关联仓储（T-OUT-005）。
 *
 * <p>同一成果同一项目唯一（uk_output_project_relation）；撤销为状态迁移，
 * 记录保留（BR-20）。</p>
 */
public interface OutputProjectRelationRepository {

    /**
     * 插入关联。
     *
     * @param relation 待插入的关联 PO
     */
    void insert(OutputProjectRelationEntity relation);

    /**
     * 按主键查询关联。
     *
     * @param id 关联 ID
     * @return 命中的关联；不存在返回空
     */
    Optional<OutputProjectRelationEntity> findById(String id);

    /**
     * 按成果与项目查询（含已撤销记录，唯一键口径）。
     *
     * @param outputId  成果 ID
     * @param projectId 项目 ID
     * @return 命中的关联；不存在返回空
     */
    Optional<OutputProjectRelationEntity> findByOutputIdAndProjectId(String outputId, String projectId);

    /**
     * 查询成果全部关联（含已撤销历史）。
     *
     * @param outputId 成果 ID
     * @return 关联列表；无关联返回空列表
     */
    List<OutputProjectRelationEntity> findByOutputId(String outputId);

    /**
     * 按项目与状态查询关联（项目成果列表）。
     *
     * @param projectId 项目 ID
     * @param status    关联状态
     * @return 关联列表；无命中返回空列表
     */
    List<OutputProjectRelationEntity> findByProjectIdAndStatus(String projectId, String status);

    /**
     * 统计成果的有效关联数（全院成果库详情的关联项目数口径）。
     *
     * @param outputId 成果 ID
     * @param status   关联状态
     * @return 有效关联数量
     */
    long countByOutputIdAndStatus(String outputId, String status);

    /**
     * 按乐观锁更新关联（撤销/恢复）。
     *
     * @param relation 携带主键与旧版本的关联 PO
     * @return 受影响行数；0 表示版本冲突或记录不存在
     */
    int update(OutputProjectRelationEntity relation);
}
