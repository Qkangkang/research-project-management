package cn.edu.rpm.output.infrastructure.repository;

import cn.edu.rpm.output.infrastructure.entity.OutputReviewEntity;
import cn.edu.rpm.output.infrastructure.mapper.OutputReviewMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * {@code output_review} 成果审核仓储实现（T-OUT-004）。
 *
 * <p>基于 MyBatis-Plus {@link OutputReviewMapper}；只提供追加与查询，
 * 不提供 UPDATE / DELETE（历史意见不可改写）。</p>
 */
@Repository
public class OutputReviewRepositoryImpl implements OutputReviewRepository {

    private final OutputReviewMapper outputReviewMapper;

    public OutputReviewRepositoryImpl(OutputReviewMapper outputReviewMapper) {
        this.outputReviewMapper = outputReviewMapper;
    }

    @Override
    public void insert(OutputReviewEntity review) {
        outputReviewMapper.insert(review);
    }

    @Override
    public List<OutputReviewEntity> findByOutputVersionId(String outputVersionId) {
        return outputReviewMapper.selectList(
                new LambdaQueryWrapper<OutputReviewEntity>()
                        .eq(OutputReviewEntity::getOutputVersionId, outputVersionId)
                        .orderByDesc(OutputReviewEntity::getReviewedAt));
    }

    @Override
    public List<OutputReviewEntity> findByOutputVersionIds(Collection<String> outputVersionIds) {
        if (outputVersionIds == null || outputVersionIds.isEmpty()) {
            return List.of();
        }
        return outputReviewMapper.selectList(
                new LambdaQueryWrapper<OutputReviewEntity>()
                        .in(OutputReviewEntity::getOutputVersionId, outputVersionIds)
                        .orderByDesc(OutputReviewEntity::getReviewedAt));
    }
}
