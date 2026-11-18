package cn.edu.rpm.output.application.dto;

import java.math.BigDecimal;

/**
 * 疑似重复候选视图（T-OUT-010；TC-OUT-007 / TC-OUT-008 / TC-OUT-013）。
 *
 * @param candidateId           候选记录 ID
 * @param candidateOutputId     候选成果 ID
 * @param matchType             匹配类型：DOI / PATENT / TITLE / OWNER
 * @param score                 匹配得分（0～1）
 * @param status                候选状态：OPEN / CONFIRMED / DISMISSED
 * @param manualConfirmRequired 是否达到人工确认阈值（低分仅提示）
 */
public record DuplicateCandidateVO(
        String candidateId,
        String candidateOutputId,
        String matchType,
        BigDecimal score,
        String status,
        boolean manualConfirmRequired
) {
}
