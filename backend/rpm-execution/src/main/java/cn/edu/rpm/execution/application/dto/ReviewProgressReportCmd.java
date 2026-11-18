package cn.edu.rpm.execution.application.dto;

import java.util.UUID;

/**
 * 审核周期报告命令（T-EXEC-007；技术文档 04 §3.8）。
 *
 * @param comment   审核意见（退回时必填）
 * @param requestId 请求幂等键
 */
public record ReviewProgressReportCmd(
        String comment,
        UUID requestId
) {
}
