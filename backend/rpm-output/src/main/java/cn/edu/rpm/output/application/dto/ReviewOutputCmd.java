package cn.edu.rpm.output.application.dto;

/**
 * 成果审核命令（T-OUT-011；技术文档 05 §4.4）。
 *
 * @param versionId 审核的版本 ID
 * @param decision  审核结论：APPROVED / RETURNED / REJECTED
 * @param comment   审核意见（退回/拒绝必填）
 */
public record ReviewOutputCmd(
        String versionId,
        String decision,
        String comment
) {
}
