package cn.edu.rpm.funding.application.dto;

import java.util.UUID;

/**
 * 支出撤回/取消命令（T-FUND-021；技术文档 05 §1.4）。
 *
 * @param expectedVersion 期望的支出乐观锁版本
 * @param reason          撤回/取消原因
 * @param requestId       请求幂等键（用于工作流取消幂等）
 */
public record ExpenseActionCmd(
        Integer expectedVersion,
        String reason,
        UUID requestId
) {
}
