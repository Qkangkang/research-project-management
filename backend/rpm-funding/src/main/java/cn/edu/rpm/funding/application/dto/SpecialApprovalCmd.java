package cn.edu.rpm.funding.application.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 特批申请命令（T-FUND-024；技术文档 08 §7.1）。
 *
 * @param specialType    OVER_BUDGET / NO_BUDGET / OVER_LIMIT
 * @param overAmount     超出金额（正数）
 * @param reason         特批理由（至少 20 字）
 * @param evidenceFileIds 证据文件 ID 列表
 * @param idempotencyKey 申请幂等键
 */
public record SpecialApprovalCmd(
        String specialType,
        BigDecimal overAmount,
        String reason,
        List<String> evidenceFileIds,
        String idempotencyKey
) {
}
