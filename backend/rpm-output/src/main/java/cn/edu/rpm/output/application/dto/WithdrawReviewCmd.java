package cn.edu.rpm.output.application.dto;

/**
 * 成果撤回审核命令（T-OUT-013；技术文档 08 §14.1）。
 *
 * @param approved 是否同意撤回
 * @param comment  审核意见，可空
 */
public record WithdrawReviewCmd(
        boolean approved,
        String comment
) {
}
