package cn.edu.rpm.funding.application.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 创建并执行对账批次命令（T-FUND-029；技术文档 05 §3.6）。
 *
 * @param periodStart    对账期间起
 * @param periodEnd      对账期间止
 * @param financeBatchNo 财务来源批次号
 * @param financeRows    财务侧支付条目
 */
public record ReconciliationRunCmd(
        LocalDate periodStart,
        LocalDate periodEnd,
        String financeBatchNo,
        List<FinancePaymentEntry> financeRows
) {
}
