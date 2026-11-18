package cn.edu.rpm.funding.application.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 提交支出命令（T-FUND-020；技术文档 05 §3.2）。
 *
 * @param expectedVersion    期望的支出乐观锁版本
 * @param budgetVersionId    预算版本 ID（必须为当前生效版本）
 * @param budgetLineId       预算科目 ID（必须属于该预算版本）
 * @param amount             申请金额（正数，最多两位小数）
 * @param purpose            用途说明
 * @param attachmentFileIds  必填附件文件 ID（合同/说明等）
 * @param specialReason      特批原因说明；无特批为 {@code null}
 */
public record SubmitExpenseCmd(
        Integer expectedVersion,
        String budgetVersionId,
        String budgetLineId,
        BigDecimal amount,
        String purpose,
        List<String> attachmentFileIds,
        String specialReason
) {
}
