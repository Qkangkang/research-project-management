package cn.edu.rpm.output.infrastructure.repository;

import cn.edu.rpm.output.infrastructure.entity.OutputEvidenceEntity;
import cn.edu.rpm.output.infrastructure.mapper.OutputEvidenceMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * {@code output_evidence} 成果证据仓储实现（T-OUT-003）。
 *
 * <p>批量插入按 500 条分批（RULE-DB3），写入循环内无查询；
 * 计数与按版本查询均为单次数据库往返（RULE-DB2）。</p>
 */
@Repository
public class OutputEvidenceRepositoryImpl implements OutputEvidenceRepository {

    /** 单批写入上限（RULE-DB3）。 */
    private static final int BATCH_SIZE = 500;

    private final OutputEvidenceMapper outputEvidenceMapper;

    public OutputEvidenceRepositoryImpl(OutputEvidenceMapper outputEvidenceMapper) {
        this.outputEvidenceMapper = outputEvidenceMapper;
    }

    @Override
    public void insertBatch(List<OutputEvidenceEntity> evidences) {
        if (evidences == null || evidences.isEmpty()) {
            return;
        }
        for (int start = 0; start < evidences.size(); start += BATCH_SIZE) {
            int end = Math.min(start + BATCH_SIZE, evidences.size());
            for (OutputEvidenceEntity evidence : evidences.subList(start, end)) {
                outputEvidenceMapper.insert(evidence);
            }
        }
    }

    @Override
    public List<OutputEvidenceEntity> findByOutputVersionId(String outputVersionId) {
        return outputEvidenceMapper.selectList(
                new LambdaQueryWrapper<OutputEvidenceEntity>()
                        .eq(OutputEvidenceEntity::getOutputVersionId, outputVersionId)
                        .orderByAsc(OutputEvidenceEntity::getCreatedAt));
    }

    @Override
    public long countByOutputVersionId(String outputVersionId) {
        Long count = outputEvidenceMapper.selectCount(
                new LambdaQueryWrapper<OutputEvidenceEntity>()
                        .eq(OutputEvidenceEntity::getOutputVersionId, outputVersionId));
        return count == null ? 0L : count;
    }

    @Override
    public int deleteByOutputVersionId(String outputVersionId) {
        return outputEvidenceMapper.delete(
                new LambdaQueryWrapper<OutputEvidenceEntity>()
                        .eq(OutputEvidenceEntity::getOutputVersionId, outputVersionId));
    }
}
