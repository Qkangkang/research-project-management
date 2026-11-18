package cn.edu.rpm.funding.application.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 对账差异视图（T-FUND-030；技术文档 05 §3.6）。
 *
 * @param diffId            差异 ID
 * @param batchId           对账批次 ID
 * @param projectId         项目 ID
 * @param diffType          差异类型
 * @param externalPaymentNo 外部支付单号
 * @param systemAmount      系统侧金额
 * @param financeAmount     财务侧金额
 * @param ownerId           责任人
 * @param status            差异状态
 * @param resolution        处置说明
 * @param evidenceFileIds   证据文件 ID
 * @param closedBy          关闭人
 * @param closedAt          关闭时间
 */
public record ReconciliationDiffView(
        String diffId,
        String batchId,
        String projectId,
        String diffType,
        String externalPaymentNo,
        BigDecimal systemAmount,
        BigDecimal financeAmount,
        String ownerId,
        String status,
        String resolution,
        List<String> evidenceFileIds,
        String closedBy,
        OffsetDateTime closedAt
) {
}
