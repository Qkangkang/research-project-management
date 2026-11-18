package cn.edu.rpm.funding.application.dto;

import java.util.List;

/**
 * 支付导入批次视图（T-FUND-028；技术文档 05 §3.4）。
 *
 * @param batchId     批次 ID
 * @param batchNo     批次号
 * @param status      批次状态
 * @param totalRows   总行数
 * @param successRows 有效行数
 * @param errorRows   错误行数
 * @param appliedBy   应用人（可空）
 * @param idempotent  本次调用是否命中重复应用
 * @param rows        行明细（应用结果查询时返回）
 */
public record PaymentImportBatchView(
        String batchId,
        String batchNo,
        String status,
        int totalRows,
        int successRows,
        int errorRows,
        String appliedBy,
        boolean idempotent,
        List<PaymentImportRowView> rows
) {
}
