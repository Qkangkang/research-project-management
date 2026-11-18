package cn.edu.rpm.funding.application.service;

import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.common.util.UuidUtils;
import cn.edu.rpm.funding.infrastructure.entity.ReconciliationBatchEntity;
import cn.edu.rpm.funding.infrastructure.entity.ReconciliationDiffEntity;
import cn.edu.rpm.funding.infrastructure.repository.ReconciliationBatchRepository;
import cn.edu.rpm.funding.infrastructure.repository.ReconciliationDiffRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 回调差异记录器（T-FUND-027 支撑；技术文档 05 §3.3）。
 *
 * <p>支付回调无法匹配支出、支出已取消、金额异常时，差异必须挂在对账批次下。
 * 本类按天维护一个 {@code CALLBACK} 来源的差异批次，供财务在统一差异队列处理；
 * 对账任务使用自己的批次，两者互不覆盖。</p>
 */
@Component
public class CallbackDiffRecorder {

    /** 回调差异批次来源标识。 */
    public static final String SOURCE_CALLBACK = "CALLBACK";

    private final ReconciliationBatchRepository batchRepository;

    private final ReconciliationDiffRepository diffRepository;

    private final Clock clock;

    /**
     * 构造回调差异记录器。
     *
     * @param batchRepository 对账批次仓储
     * @param diffRepository  差异仓储
     * @param clock           时间基准
     */
    public CallbackDiffRecorder(ReconciliationBatchRepository batchRepository,
                                ReconciliationDiffRepository diffRepository,
                                Clock clock) {
        this.batchRepository = batchRepository;
        this.diffRepository = diffRepository;
        this.clock = clock;
    }

    /**
     * 记录一条待处理差异。
     *
     * @param diffType          差异类型
     * @param projectId         项目 ID（可空）
     * @param paymentRecordId   支付记录 ID（可空）
     * @param externalPaymentNo 外部支付单号
     * @param systemAmount      系统侧金额（可空）
     * @param financeAmount     财务侧金额
     * @return 差异 ID
     */
    @org.springframework.transaction.annotation.Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public String record(String diffType, String projectId, String paymentRecordId,
                         String externalPaymentNo, BigDecimal systemAmount,
                         BigDecimal financeAmount) {
        ReconciliationBatchEntity batch = currentBatch();
        ReconciliationDiffEntity diff = new ReconciliationDiffEntity();
        diff.setId(UuidUtils.formatCompact(UUID.randomUUID()));
        diff.setBatchId(batch.getId());
        diff.setProjectId(projectId);
        diff.setPaymentRecordId(paymentRecordId);
        diff.setDiffType(diffType);
        diff.setExternalPaymentNo(externalPaymentNo);
        diff.setSystemAmount(systemAmount);
        diff.setFinanceAmount(financeAmount);
        diff.setStatus(ReconciliationDiffEntity.STATUS_OPEN);
        diff.setEvidenceFileIds("[]");
        diffRepository.insert(diff);

        batch.incrementDiffCount(1);
        if (batchRepository.update(batch) == 0) {
            throw new BizException(ErrorCode.COMMON_409, "回调差异批次并发更新冲突");
        }
        return diff.getId();
    }

    private ReconciliationBatchEntity currentBatch() {
        LocalDate today = LocalDate.now(clock);
        List<ReconciliationBatchEntity> batches = batchRepository.findByPeriod(today, today);
        Optional<ReconciliationBatchEntity> callbackBatch = batches.stream()
                .filter(batch -> SOURCE_CALLBACK.equals(batch.getSourceBatchNo()))
                .findFirst();
        if (callbackBatch.isPresent()) {
            return callbackBatch.get();
        }
        ReconciliationBatchEntity batch = new ReconciliationBatchEntity();
        batch.setId(UuidUtils.formatCompact(UUID.randomUUID()));
        batch.setBatchNo("CALLBACK-" + today + "-" + UUID.randomUUID().toString().substring(0, 8));
        batch.setPeriodStart(today);
        batch.setPeriodEnd(today);
        batch.setSourceBatchNo(SOURCE_CALLBACK);
        batch.setStatus(ReconciliationBatchEntity.STATUS_RUNNING);
        batch.setStartedAt(OffsetDateTime.now(clock));
        batch.complete(BigDecimal.ZERO, BigDecimal.ZERO, 0, OffsetDateTime.now(clock));
        batchRepository.insert(batch);
        return batch;
    }
}
