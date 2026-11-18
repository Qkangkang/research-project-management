package cn.edu.rpm.funding.domain.service;

import cn.edu.rpm.funding.infrastructure.entity.BudgetBalanceEntity;
import cn.edu.rpm.funding.infrastructure.entity.FundEventEntity;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Set;

/**
 * 资金事件投影矩阵的纯函数实现（T-FUND-016 / T-FUND-018；技术文档 08 §10）。
 *
 * <p>在线追加与全量重放共用同一矩阵，保证「投影可由事件全量重建」且误差为 0。
 * 矩阵之外的事件类型或非法冲正目标抛 {@link IllegalArgumentException}。</p>
 */
public final class FundEventProjection {

    /** 允许的九类事件。 */
    public static final Set<String> EVENT_TYPES = Set.of(
            FundEventEntity.TYPE_RESERVE,
            FundEventEntity.TYPE_RELEASE,
            FundEventEntity.TYPE_APPROVE,
            FundEventEntity.TYPE_PAY,
            FundEventEntity.TYPE_PARTIAL_PAY,
            FundEventEntity.TYPE_CANCEL,
            FundEventEntity.TYPE_REVERSE,
            FundEventEntity.TYPE_REFUND,
            FundEventEntity.TYPE_ADJUST);

    private FundEventProjection() {
    }

    /**
     * 按矩阵把单个事件应用到余额投影。
     *
     * @param balance      待更新的余额投影
     * @param eventType    事件类型
     * @param amount       事件金额（ADJUST 可带符号，其余必须为正）
     * @param originalType 被冲正事件类型（仅 {@code REVERSE} 使用）
     * @throws IllegalArgumentException 事件类型非法或冲正目标非法
     * @throws IllegalStateException    投影将出现负数
     */
    public static void apply(BudgetBalanceEntity balance, String eventType,
                             BigDecimal amount, String originalType) {
        if (balance == null) {
            throw new IllegalArgumentException("balance 不能为空");
        }
        String type = normalize(eventType);
        if (!EVENT_TYPES.contains(type)) {
            throw new IllegalArgumentException("未知资金事件类型: " + eventType);
        }
        if (amount == null) {
            throw new IllegalArgumentException("事件金额不能为空");
        }

        switch (type) {
            case FundEventEntity.TYPE_RESERVE -> balance.reserve(amount);
            case FundEventEntity.TYPE_RELEASE -> balance.release(amount);
            case FundEventEntity.TYPE_APPROVE -> balance.approve(amount);
            case FundEventEntity.TYPE_PAY, FundEventEntity.TYPE_PARTIAL_PAY ->
                    balance.settlePayment(amount);
            case FundEventEntity.TYPE_CANCEL -> balance.cancelApprovedUnpaid(amount);
            case FundEventEntity.TYPE_REFUND -> balance.refund(amount);
            case FundEventEntity.TYPE_ADJUST -> balance.adjustApprovedAmount(amount);
            case FundEventEntity.TYPE_REVERSE -> applyReverse(balance, amount, originalType);
            default -> throw new IllegalArgumentException("未知资金事件类型: " + eventType);
        }
    }

    private static void applyReverse(BudgetBalanceEntity balance, BigDecimal amount, String originalType) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("冲正金额必须为正数");
        }
        String original = normalize(originalType);
        if (original.isEmpty()) {
            throw new IllegalArgumentException("REVERSE 必须指定被冲正事件类型");
        }
        switch (original) {
            case FundEventEntity.TYPE_RESERVE -> balance.release(amount);
            case FundEventEntity.TYPE_RELEASE -> balance.reserve(amount);
            case FundEventEntity.TYPE_APPROVE -> balance.reverseApprove(amount);
            case FundEventEntity.TYPE_PAY, FundEventEntity.TYPE_PARTIAL_PAY ->
                    balance.reversePayment(amount);
            case FundEventEntity.TYPE_CANCEL -> balance.restoreApprovedUnpaid(amount);
            case FundEventEntity.TYPE_REFUND -> balance.restorePaid(amount);
            case FundEventEntity.TYPE_ADJUST -> balance.adjustApprovedAmount(amount.negate());
            default -> throw new IllegalArgumentException("不支持冲正的事件类型: " + originalType);
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
