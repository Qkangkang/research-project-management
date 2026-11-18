package cn.edu.rpm.funding.infrastructure.repository;

import cn.edu.rpm.funding.infrastructure.entity.PaymentRecordEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * {@code payment_record} 支付记录仓储（T-FUND-007）。
 */
public interface PaymentRecordRepository {

    /**
     * 插入支付记录。
     *
     * @param payment 待插入的支付 PO
     */
    void insert(PaymentRecordEntity payment);

    /**
     * 按主键查询。
     *
     * @param id 支付记录 ID
     * @return 命中的记录；不存在返回空
     */
    Optional<PaymentRecordEntity> findById(String id);

    /**
     * 按外部支付单号查询（uk_payment_external_no）。
     *
     * @param externalPaymentNo 外部支付单号
     * @return 命中的记录；不存在返回空
     */
    Optional<PaymentRecordEntity> findByExternalPaymentNo(String externalPaymentNo);

    /**
     * 按外部事件 ID 查询（报文级幂等）。
     *
     * @param externalEventId 外部事件 ID
     * @return 命中的记录；不存在返回空
     */
    Optional<PaymentRecordEntity> findByExternalEventId(String externalEventId);

    /**
     * 按支付时间区间查询（对账取系统侧支付）。
     *
     * @param from 起始时间（含）
     * @param to   结束时间（不含）
     * @return 支付记录列表，按支付时间升序
     */
    List<PaymentRecordEntity> findByPaidAtBetween(OffsetDateTime from, OffsetDateTime to);

    /**
     * 按支出查询支付记录（支付时间升序）。
     *
     * @param expenseRequestId 支出 ID
     * @return 支付记录列表；无命中返回空列表
     */
    List<PaymentRecordEntity> findByExpenseRequestId(String expenseRequestId);

    /**
     * 按乐观锁更新支付记录。
     *
     * @param payment 携带主键与旧版本的支付 PO
     * @return 受影响行数；0 表示版本冲突或记录不存在
     */
    int update(PaymentRecordEntity payment);
}
