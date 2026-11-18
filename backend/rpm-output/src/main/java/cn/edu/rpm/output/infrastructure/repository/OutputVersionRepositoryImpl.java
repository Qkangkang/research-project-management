package cn.edu.rpm.output.infrastructure.repository;

import cn.edu.rpm.output.infrastructure.entity.OutputVersionEntity;
import cn.edu.rpm.output.infrastructure.mapper.OutputVersionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * {@code output_version} 成果版本仓储实现（T-OUT-002）。
 *
 * <p>基于 MyBatis-Plus {@link OutputVersionMapper}；版本号计算走 Mapper XML，
 * 避免分两次查询或内存聚合。本类不提供更新方法，版本不可变。</p>
 */
@Repository
public class OutputVersionRepositoryImpl implements OutputVersionRepository {

    private final OutputVersionMapper outputVersionMapper;

    public OutputVersionRepositoryImpl(OutputVersionMapper outputVersionMapper) {
        this.outputVersionMapper = outputVersionMapper;
    }

    @Override
    public void insert(OutputVersionEntity version) {
        outputVersionMapper.insert(version);
    }

    @Override
    public Optional<OutputVersionEntity> findById(String id) {
        return Optional.ofNullable(outputVersionMapper.selectById(id));
    }

    @Override
    public Optional<OutputVersionEntity> findByOutputIdAndVersionNo(String outputId, int versionNo) {
        return Optional.ofNullable(outputVersionMapper.selectOne(
                new LambdaQueryWrapper<OutputVersionEntity>()
                        .eq(OutputVersionEntity::getOutputId, outputId)
                        .eq(OutputVersionEntity::getVersionNo, versionNo)));
    }

    @Override
    public List<OutputVersionEntity> findByOutputId(String outputId) {
        return outputVersionMapper.selectList(
                new LambdaQueryWrapper<OutputVersionEntity>()
                        .eq(OutputVersionEntity::getOutputId, outputId)
                        .orderByDesc(OutputVersionEntity::getVersionNo));
    }

    @Override
    public int findMaxVersionNo(String outputId) {
        return outputVersionMapper.selectMaxVersionNo(outputId);
    }

    @Override
    public int update(OutputVersionEntity version) {
        return outputVersionMapper.updateById(version);
    }
}
