package cn.edu.rpm.funding.application.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 支出视图（T-FUND-019；TC-FUND-039、TC-FUND-069）。
 *
 * <p>银行账号只返回脱敏值，加密值与明文永不出现在响应中。</p>
 *
 * @param expenseId      支出 ID
 * @param requestNo      支出单号
 * @param projectId      项目 ID
 * @param budgetVersionId 预算版本 ID
 * @param budgetLineId   预算科目 ID
 * @param amount         申请金额
 * @param purpose        用途说明
 * @param payeeName      收款人
 * @param accountMasked  脱敏银行账号
 * @param status         状态
 * @param currentVersionId 当前版本 ID
 * @param version        乐观锁版本
 * @param submittedAt    提交时间
 * @param createdAt      创建时间
 */
public record ExpenseView(
        String expenseId,
        String requestNo,
        String projectId,
        String budgetVersionId,
        String budgetLineId,
        BigDecimal amount,
        String purpose,
        String payeeName,
        String accountMasked,
        String status,
        String currentVersionId,
        Integer version,
        OffsetDateTime submittedAt,
        OffsetDateTime createdAt
) {
}
