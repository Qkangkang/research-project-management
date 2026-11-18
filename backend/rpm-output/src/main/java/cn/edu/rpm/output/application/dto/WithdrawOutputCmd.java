package cn.edu.rpm.output.application.dto;

/**
 * 成果撤回申请命令（T-OUT-013；技术文档 08 §14.1）。
 *
 * @param reason 撤回理由（必填，进入科研管理审核并写入审计）
 */
public record WithdrawOutputCmd(
        String reason
) {
}
