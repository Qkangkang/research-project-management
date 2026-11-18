package cn.edu.rpm.output.application.dto;

import java.math.BigDecimal;

/**
 * 成果关联项目命令（T-OUT-012；技术文档 05 §4.3）。
 *
 * @param projectId        项目 ID
 * @param contributionDesc 贡献/用途说明（必填）
 * @param attributionRatio 归属比例（0～1，仅指定统计口径）
 */
public record RelateOutputCmd(
        String projectId,
        String contributionDesc,
        BigDecimal attributionRatio
) {
}
