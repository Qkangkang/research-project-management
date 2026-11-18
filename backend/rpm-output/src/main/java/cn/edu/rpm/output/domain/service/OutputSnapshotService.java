package cn.edu.rpm.output.domain.service;

import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.common.util.UuidUtils;
import cn.edu.rpm.output.domain.model.OutputSnapshot;
import cn.edu.rpm.output.infrastructure.entity.OutputEvidenceEntity;
import cn.edu.rpm.output.infrastructure.entity.OutputVersionEntity;
import cn.edu.rpm.output.infrastructure.entity.ResearchOutputEntity;
import cn.edu.rpm.output.infrastructure.repository.OutputEvidenceRepository;
import cn.edu.rpm.output.infrastructure.repository.OutputVersionRepository;
import cn.edu.rpm.output.infrastructure.repository.ResearchOutputRepository;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 成果快照冻结领域服务（T-OUT-015；技术文档 07 §5.2、05 §5）。
 *
 * <p>按验收引用的成果版本生成不可变快照：复制 {@code version_id}、内容摘要与
 * 证据哈希。成果后续修订生成新版本，不改变已冻结快照（TC-OUT-021 / RES-06）。
 * 快照冻结不写业务表（验收模块在 Batch-22～25 组合持久化）。</p>
 */
public class OutputSnapshotService {

    private final ResearchOutputRepository researchOutputRepository;

    private final OutputVersionRepository outputVersionRepository;

    private final OutputEvidenceRepository outputEvidenceRepository;

    /**
     * 构造快照服务。
     *
     * @param researchOutputRepository 成果仓储
     * @param outputVersionRepository  成果版本仓储
     * @param outputEvidenceRepository 成果证据仓储
     */
    public OutputSnapshotService(ResearchOutputRepository researchOutputRepository,
                                 OutputVersionRepository outputVersionRepository,
                                 OutputEvidenceRepository outputEvidenceRepository) {
        this.researchOutputRepository = Objects.requireNonNull(researchOutputRepository,
                "researchOutputRepository 不能为空");
        this.outputVersionRepository = Objects.requireNonNull(outputVersionRepository,
                "outputVersionRepository 不能为空");
        this.outputEvidenceRepository = Objects.requireNonNull(outputEvidenceRepository,
                "outputEvidenceRepository 不能为空");
    }

    /**
     * 冻结指定成果版本。
     *
     * <p>触发场景：验收提交引用成果版本时。失败语义：成果或版本不存在
     * {@code RPM-COMMON-404}；版本不属于该成果 {@code RPM-COMMON-400}。</p>
     *
     * @param outputId 成果 ID
     * @param versionId 版本 ID
     * @param frozenAt 冻结时刻
     * @return 不可变快照
     */
    public OutputSnapshot freeze(String outputId, String versionId, Instant frozenAt) {
        Objects.requireNonNull(frozenAt, "frozenAt 不能为空");
        ResearchOutputEntity output = researchOutputRepository.findById(outputId)
                .orElseThrow(() -> new BizException(ErrorCode.COMMON_404, "成果不存在"));
        OutputVersionEntity version = outputVersionRepository.findById(versionId)
                .orElseThrow(() -> new BizException(ErrorCode.COMMON_404, "成果版本不存在"));
        if (!UuidUtils.sameId(version.getOutputId(), outputId)) {
            throw new BizException(ErrorCode.COMMON_400, "版本不属于该成果");
        }

        List<OutputSnapshot.EvidenceHash> evidenceHashes =
                outputEvidenceRepository.findByOutputVersionId(versionId).stream()
                        .sorted(Comparator.comparing(OutputEvidenceEntity::getFileId))
                        .map(OutputSnapshotService::toEvidenceHash)
                        .toList();

        return new OutputSnapshot(
                UuidUtils.parse(output.getId()),
                UuidUtils.parse(version.getId()),
                version.getVersionNo(),
                version.getContentHash(),
                evidenceHashes,
                frozenAt);
    }

    private static OutputSnapshot.EvidenceHash toEvidenceHash(OutputEvidenceEntity evidence) {
        return new OutputSnapshot.EvidenceHash(
                UuidUtils.parse(evidence.getFileId()), evidence.getSha256());
    }
}
