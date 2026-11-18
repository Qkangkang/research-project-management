package cn.edu.rpm.output.application.dto;

import java.math.BigDecimal;

/**
 * 成果-项目关联视图（T-OUT-012；TC-OUT-015～020）。
 *
 * @param relationId       关联 ID
 * @param outputId         成果 ID
 * @param projectId        项目 ID
 * @param contributionDesc 贡献/用途说明
 * @param attributionRatio 归属比例（仅指定统计口径）
 * @param status           关联状态：ACTIVE / REVOKED
 */
public record OutputRelationVO(
        String relationId,
        String outputId,
        String projectId,
        String contributionDesc,
        BigDecimal attributionRatio,
        String status
) {
}
