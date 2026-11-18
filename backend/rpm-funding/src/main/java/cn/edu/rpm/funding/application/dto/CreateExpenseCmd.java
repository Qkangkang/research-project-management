package cn.edu.rpm.funding.application.dto;

import java.math.BigDecimal;

/**
 * 创建支出草稿命令（T-FUND-019；技术文档 05 §3.2）。
 *
 * @param budgetVersionId           预算版本 ID
 * @param budgetLineId              预算科目 ID
 * @param amount                    申请金额（正数，最多两位小数）
 * @param purpose                   用途说明（必填）
 * @param payeeName                 收款人名称
 * @param payeeBankAccountMasked    脱敏银行账号
 * @param payeeBankAccountEncrypted 加密银行账号
 * @param payeeBankName             开户行
 * @param idempotencyKey            创建幂等键
 */
public record CreateExpenseCmd(
        String budgetVersionId,
        String budgetLineId,
        BigDecimal amount,
        String purpose,
        String payeeName,
        String payeeBankAccountMasked,
        String payeeBankAccountEncrypted,
        String payeeBankName,
        String idempotencyKey
) {
}
