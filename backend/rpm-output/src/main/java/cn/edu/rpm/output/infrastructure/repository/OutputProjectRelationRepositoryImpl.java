package cn.edu.rpm.output.infrastructure.repository;

import cn.edu.rpm.output.infrastructure.entity.OutputProjectRelationEntity;
import cn.edu.rpm.output.infrastructure.mapper.OutputProjectRelationMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * {@code output_project_relation} 成果-项目关联仓储实现（T-OUT-005）。
 *
 * <p>基于 MyBatis-Plus {@link OutputProjectRelationMapper}；撤销仅做状态迁移，
 * 历史记录与验收快照引用保持可查（TC-OUT-020）。</p>
 */
@Repository
public class OutputProjectRelationRepositoryImpl implements OutputProjectRelationRepository {

    private final OutputProjectRelationMapper outputProjectRelationMapper;

    public OutputProjectRelationRepositoryImpl(OutputProjectRelationMapper outputProjectRelationMapper) {
        this.outputProjectRelationMapper = outputProjectRelationMapper;
    }

    @Override
    public void insert(OutputProjectRelationEntity relation) {
        outputProjectRelationMapper.insert(relation);
    }

    @Override
    public Optional<OutputProjectRelationEntity> findById(String id) {
        return Optional.ofNullable(outputProjectRelationMapper.selectById(id));
    }

    @Override
    public Optional<OutputProjectRelationEntity> findByOutputIdAndProjectId(String outputId, String projectId) {
        return Optional.ofNullable(outputProjectRelationMapper.selectOne(
                new LambdaQueryWrapper<OutputProjectRelationEntity>()
                        .eq(OutputProjectRelationEntity::getOutputId, outputId)
                        .eq(OutputProjectRelationEntity::getProjectId, projectId)));
    }

    @Override
    public List<OutputProjectRelationEntity> findByOutputId(String outputId) {
        return outputProjectRelationMapper.selectList(
                new LambdaQueryWrapper<OutputProjectRelationEntity>()
                        .eq(OutputProjectRelationEntity::getOutputId, outputId)
                        .orderByDesc(OutputProjectRelationEntity::getCreatedAt));
    }

    @Override
    public List<OutputProjectRelationEntity> findByProjectIdAndStatus(String projectId, String status) {
        return outputProjectRelationMapper.selectList(
                new LambdaQueryWrapper<OutputProjectRelationEntity>()
                        .eq(OutputProjectRelationEntity::getProjectId, projectId)
                        .eq(OutputProjectRelationEntity::getStatus, status)
                        .orderByDesc(OutputProjectRelationEntity::getCreatedAt));
    }

    @Override
    public long countByOutputIdAndStatus(String outputId, String status) {
        Long count = outputProjectRelationMapper.selectCount(
                new LambdaQueryWrapper<OutputProjectRelationEntity>()
                        .eq(OutputProjectRelationEntity::getOutputId, outputId)
                        .eq(OutputProjectRelationEntity::getStatus, status));
        return count == null ? 0L : count;
    }

    @Override
    public int update(OutputProjectRelationEntity relation) {
        return outputProjectRelationMapper.updateById(relation);
    }
}
