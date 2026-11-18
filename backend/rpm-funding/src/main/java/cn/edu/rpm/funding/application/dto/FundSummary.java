package cn.edu.rpm.funding.application.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 项目经费汇总（T-FUND-023；TC-FUND-037、TC-FUND-038）。
 *
 * @param dataAsOf           数据时点
 * @param totalApprovedAmount 批准额合计
 * @param totalReservedAmount 在审占用合计
 * @param totalApprovedUnpaidAmount 已批未付合计
 * @param totalPaidAmount    已付合计
 * @param totalAvailableAmount 可用余额合计
 * @param lines              科目明细（无预算时为空列表）
 */
public record FundSummary(
        OffsetDateTime dataAsOf,
        BigDecimal totalApprovedAmount,
        BigDecimal totalReservedAmount,
        BigDecimal totalApprovedUnpaidAmount,
        BigDecimal totalPaidAmount,
        BigDecimal totalAvailableAmount,
        List<FundLineSummary> lines
) {
}
