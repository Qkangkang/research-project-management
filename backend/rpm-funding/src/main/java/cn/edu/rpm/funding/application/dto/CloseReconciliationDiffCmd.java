package cn.edu.rpm.funding.application.dto;

import java.util.List;

/**
 * 关闭对账差异命令（T-FUND-030；技术文档 05 §3.6）。
 *
 * @param resolution      处置说明（必填）
 * @param ownerId         责任人（必填）
 * @param evidenceFileIds 证据文件 ID（至少 1 个）
 */
public record CloseReconciliationDiffCmd(
        String resolution,
        String ownerId,
        List<String> evidenceFileIds
) {
}
