package cn.edu.rpm.funding.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 对账批次视图（T-FUND-029/030；技术文档 05 §3.6）。
 *
 * @param batchId            批次 ID
 * @param batchNo            批次号
 * @param periodStart        期间起
 * @param periodEnd          期间止
 * @param status             批次状态
 * @param totalFinanceAmount 财务侧合计
 * @param totalSystemAmount  系统侧合计
 * @param diffCount          差异数量
 * @param diffs              差异明细
 */
public record ReconciliationBatchView(
        String batchId,
        String batchNo,
        LocalDate periodStart,
        LocalDate periodEnd,
        String status,
        BigDecimal totalFinanceAmount,
        BigDecimal totalSystemAmount,
        int diffCount,
        List<ReconciliationDiffView> diffs
) {
}
