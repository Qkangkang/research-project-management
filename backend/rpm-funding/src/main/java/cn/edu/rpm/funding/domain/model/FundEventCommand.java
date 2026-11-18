package cn.edu.rpm.funding.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 资金事件追加命令（T-FUND-016；技术文档 05 §1.2、08 §10）。
 *
 * <p>{@code amount} 对普通事件必须为正数；仅 {@code ADJUST} 允许带符号
 * （正增负减），落库时保存绝对值，方向由符号表达。{@code reversalOfEventId}
 * 仅在 {@code REVERSE} 时必填。</p>
 *
 * @param eventType        九类事件之一
 * @param projectId        项目 ID
 * @param expenseRequestId 关联支出 ID（可空）
 * @param budgetVersionId  预算版本 ID
 * @param budgetLineId     预算科目 ID
 * @param amount           事件金额（ADJUST 可带符号，其余必须为正）
 * @param externalNo       外部单号（可空）
 * @param idempotencyKey   幂等键（非空）
 * @param reversalOfEventId 被冲正事件 ID（仅 REVERSE）
 * @param payload          事件载荷 JSON（可空）
 * @param actorId          操作人用户 ID（可空，系统事件）
 */
public record FundEventCommand(
        String eventType,
        String projectId,
        String expenseRequestId,
        String budgetVersionId,
        String budgetLineId,
        BigDecimal amount,
        String externalNo,
        String idempotencyKey,
        String reversalOfEventId,
        String payload,
        UUID actorId
) {
}
