package cn.edu.rpm.application.application.dto;

import java.util.UUID;

/**
 * 申报撤回命令（T-APP-018；技术文档 04 §2.2）。
 *
 * @param reason    撤回原因（审计必填）
 * @param requestId 请求幂等键（取消审批实例幂等）
 */
public record WithdrawApplicationCmd(String reason, UUID requestId) {
}
