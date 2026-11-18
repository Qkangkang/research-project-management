package cn.edu.rpm.output.domain.service;

import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.output.domain.model.DuplicateMatchPolicy;
import cn.edu.rpm.output.infrastructure.entity.OutputDuplicateCandidateEntity;
import cn.edu.rpm.output.infrastructure.entity.ResearchOutputEntity;
import cn.edu.rpm.output.infrastructure.repository.OutputDuplicateCandidateRepository;
import cn.edu.rpm.output.infrastructure.repository.ResearchOutputRepository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 成果查重领域服务（T-OUT-010；技术文档 05 §4.2、07 §5.1）。
 *
 * <p>匹配口径按 PRD RES-05（V1 精确匹配）：</p>
 * <ul>
 *   <li>DOI 完全相同 → {@code match_type = DOI}；</li>
 *   <li>专利号完全相同 → {@code match_type = PATENT}；</li>
 *   <li>名称相同且完成人相同 → {@code match_type = OWNER}；</li>
 *   <li>名称相同但完成人/DOI 不同 → {@code match_type = TITLE}（低分提示）。</li>
 * </ul>
 *
 * <p>候选写入 {@code output_duplicate_candidate} 仅为提示；本类不删除、不合并、
 * 不修改任何成果（BR-21）。重复运行按唯一键幂等，不产生重复候选。</p>
 */
public class OutputDuplicateService {

    private final ResearchOutputRepository researchOutputRepository;

    private final OutputDuplicateCandidateRepository duplicateCandidateRepository;

    private final DuplicateMatchPolicy matchPolicy;

    /**
     * 构造查重服务。
     *
     * @param researchOutputRepository     成果仓储
     * @param duplicateCandidateRepository 疑似重复候选仓储
     * @param matchPolicy                  查重打分策略（外部化配置）
     */
    public OutputDuplicateService(ResearchOutputRepository researchOutputRepository,
                                  OutputDuplicateCandidateRepository duplicateCandidateRepository,
                                  DuplicateMatchPolicy matchPolicy) {
        this.researchOutputRepository = Objects.requireNonNull(researchOutputRepository,
                "researchOutputRepository 不能为空");
        this.duplicateCandidateRepository = Objects.requireNonNull(duplicateCandidateRepository,
                "duplicateCandidateRepository 不能为空");
        this.matchPolicy = Objects.requireNonNull(matchPolicy, "matchPolicy 不能为空");
    }

    /**
     * 检测并记录疑似重复候选，返回当前待确认（OPEN）候选列表。
     *
     * <p>触发场景：成果登记成功后与提交审核前。失败语义：成果不存在
     * {@code RPM-COMMON-404}。方法幂等：重复调用不新增重复候选。</p>
     *
     * @param outputId 成果 ID
     * @param detectedAt 检测时刻
     * @return 待确认候选列表；无候选返回空列表
     */
    public List<OutputDuplicateCandidateEntity> findCandidates(String outputId, OffsetDateTime detectedAt) {
        Objects.requireNonNull(detectedAt, "detectedAt 不能为空");
        ResearchOutputEntity output = researchOutputRepository.findById(outputId)
                .orElseThrow(() -> new BizException(ErrorCode.COMMON_404, "成果不存在"));

        List<OutputDuplicateCandidateEntity> detected = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        for (ResearchOutputEntity candidate : researchOutputRepository.findByDoi(output.getDoi())) {
            if (!candidate.getId().equals(output.getId())) {
                add(detected, seen, output, candidate, OutputDuplicateCandidateEntity.MATCH_DOI,
                        matchPolicy.doiScore(), detectedAt);
            }
        }
        for (ResearchOutputEntity candidate : researchOutputRepository.findByPatentNo(output.getPatentNo())) {
            if (!candidate.getId().equals(output.getId())) {
                add(detected, seen, output, candidate, OutputDuplicateCandidateEntity.MATCH_PATENT,
                        matchPolicy.patentScore(), detectedAt);
            }
        }
        for (ResearchOutputEntity candidate : researchOutputRepository.findByTitle(output.getTitle())) {
            if (candidate.getId().equals(output.getId())) {
                continue;
            }
            if (Objects.equals(candidate.getOwnerId(), output.getOwnerId())) {
                add(detected, seen, output, candidate, OutputDuplicateCandidateEntity.MATCH_OWNER,
                        matchPolicy.ownerScore(), detectedAt);
            } else {
                add(detected, seen, output, candidate, OutputDuplicateCandidateEntity.MATCH_TITLE,
                        matchPolicy.titleScore(), detectedAt);
            }
        }

        for (OutputDuplicateCandidateEntity candidate : detected) {
            duplicateCandidateRepository.insertIfAbsent(candidate);
        }
        return duplicateCandidateRepository.findByOutputIdAndStatus(
                outputId, OutputDuplicateCandidateEntity.STATUS_OPEN);
    }

    /**
     * 查询待确认候选（不触发新检测）。
     *
     * @param outputId 成果 ID
     * @return 待确认候选列表；无候选返回空列表
     */
    public List<OutputDuplicateCandidateEntity> pending(String outputId) {
        return duplicateCandidateRepository.findByOutputIdAndStatus(
                outputId, OutputDuplicateCandidateEntity.STATUS_OPEN);
    }

    /**
     * 判断候选是否达到人工确认阈值（低于阈值仅为低分提示）。
     *
     * @param candidate 候选
     * @return 达到阈值返回 {@code true}
     */
    public boolean requiresManualConfirmation(OutputDuplicateCandidateEntity candidate) {
        Objects.requireNonNull(candidate, "candidate 不能为空");
        return matchPolicy.reachesConfirmThreshold(candidate.getScore());
    }

    private static void add(List<OutputDuplicateCandidateEntity> detected, Set<String> seen,
                            ResearchOutputEntity output, ResearchOutputEntity candidate,
                            String matchType, java.math.BigDecimal score, OffsetDateTime detectedAt) {
        String key = candidate.getId() + ":" + matchType;
        if (!seen.add(key)) {
            return;
        }
        detected.add(OutputDuplicateCandidateEntity.create(
                output.getId(), candidate.getId(), matchType, score, detectedAt));
    }
}
