package cn.edu.rpm.output.application.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 成果详情视图（T-OUT-014；TC-OUT-012 / TC-OUT-014）。
 *
 * <p>按字段权限脱敏后返回；证据只返回文件 ID/哈希/类型，下载走 rpm-file 鉴权
 * （不暴露可猜测 URL，09 §5.2）。</p>
 *
 * @param outputId        成果 ID
 * @param outputType      成果类型
 * @param title           标题
 * @param doi             DOI，可空
 * @param patentNo        专利号，可空
 * @param ownerId         完成人用户 ID
 * @param deptId          所属部门 ID
 * @param completedAt     完成日期
 * @param status          成果状态
 * @param currentVersionId 当前版本 ID
 * @param versionNo       当前版本号
 * @param contentHash     版本内容 SHA-256
 * @param metadata        扩展元数据
 * @param evidence        证据列表
 * @param reviews         审核意见列表（历史保留）
 * @param relations       项目关联列表（含已撤销历史）
 * @param relationCount   有效关联项目数
 */
public record OutputDetailVO(
        String outputId,
        String outputType,
        String title,
        String doi,
        String patentNo,
        String ownerId,
        String deptId,
        LocalDate completedAt,
        String status,
        String currentVersionId,
        Integer versionNo,
        String contentHash,
        Map<String, Object> metadata,
        List<EvidenceVO> evidence,
        List<ReviewVO> reviews,
        List<RelationVO> relations,
        long relationCount
) {

    /** 证据视图（不含下载地址）。 */
    public record EvidenceVO(String fileId, String evidenceType, String sha256) {
    }

    /** 审核意见视图（历史意见保留）。 */
    public record ReviewVO(String reviewId, String reviewerId, String decision, String comment,
                           OffsetDateTime reviewedAt) {
    }

    /** 关联视图（含已撤销历史）。 */
    public record RelationVO(String relationId, String projectId, String contributionDesc,
                             java.math.BigDecimal attributionRatio, String status) {
    }
}
