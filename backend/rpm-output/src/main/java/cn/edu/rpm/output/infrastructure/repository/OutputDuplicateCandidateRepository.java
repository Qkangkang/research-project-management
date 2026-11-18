package cn.edu.rpm.output.infrastructure.repository;

import cn.edu.rpm.output.infrastructure.entity.OutputDuplicateCandidateEntity;

import java.util.List;

/**
 * {@code output_duplicate_candidate} 疑似重复候选仓储（T-OUT-006）。
 *
 * <p>候选只提示、不合并（BR-21）；同一对成果同一匹配类型以唯一键幂等去重。</p>
 */
public interface OutputDuplicateCandidateRepository {

    /**
     * 幂等插入候选：唯一键冲突时视为已存在，不抛异常、不覆盖。
     *
     * @param candidate 待插入候选
     * @return 实际插入返回 {@code true}；已存在返回 {@code false}
     */
    boolean insertIfAbsent(OutputDuplicateCandidateEntity candidate);

    /**
     * 按成果与状态查询候选。
     *
     * @param outputId 成果 ID
     * @param status   候选状态
     * @return 候选列表；无命中返回空列表
     */
    List<OutputDuplicateCandidateEntity> findByOutputIdAndStatus(String outputId, String status);

    /**
     * 查询成果全部候选。
     *
     * @param outputId 成果 ID
     * @return 候选列表；无命中返回空列表
     */
    List<OutputDuplicateCandidateEntity> findByOutputId(String outputId);
}
