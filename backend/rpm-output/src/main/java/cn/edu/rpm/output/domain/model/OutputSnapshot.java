package cn.edu.rpm.output.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 成果版本冻结快照（T-OUT-015；技术文档 07 §5.2、05 §5）。
 *
 * <p>验收引用成果时固化 {@code version_id}、内容摘要与证据哈希；后续成果修订
 * 生成新版本，不改变已冻结快照（RES-06 / BR-22）。本对象只承载快照内容，
 * 持久化由验收快照（{@code acceptance_version}）在后续批次组合完成。</p>
 *
 * @param outputId       成果 ID
 * @param versionId      冻结的版本 ID
 * @param versionNo      冻结的版本号
 * @param contentHash    版本内容 SHA-256
 * @param evidenceHashes 证据哈希（按文件 ID 升序，保证可复算）
 * @param frozenAt       冻结时刻
 */
public record OutputSnapshot(
        UUID outputId,
        UUID versionId,
        int versionNo,
        String contentHash,
        List<EvidenceHash> evidenceHashes,
        Instant frozenAt
) {

    /** 规范化证据列表。 */
    public OutputSnapshot {
        Objects.requireNonNull(outputId, "outputId 不能为空");
        Objects.requireNonNull(versionId, "versionId 不能为空");
        Objects.requireNonNull(contentHash, "contentHash 不能为空");
        Objects.requireNonNull(frozenAt, "frozenAt 不能为空");
        evidenceHashes = evidenceHashes == null ? List.of() : List.copyOf(evidenceHashes);
    }

    /**
     * 证据文件哈希。
     *
     * @param fileId 文件对象 ID
     * @param sha256 文件内容 SHA-256（可空）
     */
    public record EvidenceHash(UUID fileId, String sha256) {

        /** 校验文件 ID。 */
        public EvidenceHash {
            Objects.requireNonNull(fileId, "fileId 不能为空");
        }
    }
}
