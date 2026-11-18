package cn.edu.rpm.output.infrastructure.repository;

import cn.edu.rpm.output.infrastructure.entity.OutputDuplicateCandidateEntity;
import cn.edu.rpm.output.infrastructure.mapper.OutputDuplicateCandidateMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * {@code output_duplicate_candidate} 候选仓储实现（T-OUT-006）。
 *
 * <p>幂等插入依赖 {@code uk_output_duplicate}：并发重复检测时后写入方收到
 * {@link DuplicateKeyException}，此处降级为「已存在」并记录 debug 日志后返回
 * {@code false}（非空 catch；不吞业务异常，仅处理唯一键幂等语义）。</p>
 */
@Repository
public class OutputDuplicateCandidateRepositoryImpl implements OutputDuplicateCandidateRepository {

    private final OutputDuplicateCandidateMapper outputDuplicateCandidateMapper;

    public OutputDuplicateCandidateRepositoryImpl(
            OutputDuplicateCandidateMapper outputDuplicateCandidateMapper) {
        this.outputDuplicateCandidateMapper = outputDuplicateCandidateMapper;
    }

    @Override
    public boolean insertIfAbsent(OutputDuplicateCandidateEntity candidate) {
        try {
            outputDuplicateCandidateMapper.insert(candidate);
            return true;
        } catch (DuplicateKeyException ex) {
            LoggerFactory.getLogger(OutputDuplicateCandidateRepositoryImpl.class)
                    .debug("疑似重复候选已存在，按幂等跳过 outputId={} candidateId={} matchType={}",
                            candidate.getOutputId(), candidate.getCandidateOutputId(),
                            candidate.getMatchType());
            return false;
        }
    }

    @Override
    public List<OutputDuplicateCandidateEntity> findByOutputIdAndStatus(String outputId, String status) {
        return outputDuplicateCandidateMapper.selectList(
                new LambdaQueryWrapper<OutputDuplicateCandidateEntity>()
                        .eq(OutputDuplicateCandidateEntity::getOutputId, outputId)
                        .eq(OutputDuplicateCandidateEntity::getStatus, status)
                        .orderByDesc(OutputDuplicateCandidateEntity::getScore));
    }

    @Override
    public List<OutputDuplicateCandidateEntity> findByOutputId(String outputId) {
        return outputDuplicateCandidateMapper.selectList(
                new LambdaQueryWrapper<OutputDuplicateCandidateEntity>()
                        .eq(OutputDuplicateCandidateEntity::getOutputId, outputId)
                        .orderByDesc(OutputDuplicateCandidateEntity::getScore));
    }
}
