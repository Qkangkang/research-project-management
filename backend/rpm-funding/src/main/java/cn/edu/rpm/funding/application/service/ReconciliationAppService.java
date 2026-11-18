package cn.edu.rpm.funding.application.service;

import cn.edu.rpm.audit.domain.model.AuditEvent;
import cn.edu.rpm.audit.domain.service.AuditRecorder;
import cn.edu.rpm.common.domain.service.NumberGenerator;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.common.util.TraceIds;
import cn.edu.rpm.common.util.UuidUtils;
import cn.edu.rpm.funding.application.dto.FinancePaymentEntry;
import cn.edu.rpm.funding.application.dto.ReconciliationBatchView;
import cn.edu.rpm.funding.application.dto.ReconciliationDiffView;
import cn.edu.rpm.funding.application.dto.ReconciliationRunCmd;
import cn.edu.rpm.funding.domain.model.FundingPermissions;
import cn.edu.rpm.funding.infrastructure.entity.ExpenseRequestEntity;
import cn.edu.rpm.funding.infrastructure.entity.PaymentRecordEntity;
import cn.edu.rpm.funding.infrastructure.entity.ReconciliationBatchEntity;
import cn.edu.rpm.funding.infrastructure.entity.ReconciliationDiffEntity;
import cn.edu.rpm.funding.infrastructure.repository.ExpenseRequestRepository;
import cn.edu.rpm.funding.infrastructure.repository.PaymentRecordRepository;
import cn.edu.rpm.funding.infrastructure.repository.ReconciliationBatchRepository;
import cn.edu.rpm.funding.infrastructure.repository.ReconciliationDiffRepository;
import cn.edu.rpm.iam.application.dto.AuthContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 对账应用服务（T-FUND-029；技术文档 05 §3.6）。
 *
 * <p>按外部支付单号匹配财务侧与系统侧支付，生成
 * {@code MISSING_PAYMENT}/{@code AMOUNT_MISMATCH}/{@code DUPLICATE}/{@code STATUS_LAG}
 * 四类差异；对账过程只读支付记录，不修改支出状态或金额。</p>
 */
@Service
public class ReconciliationAppService {

    /** 对账批次号规则编码。 */
    public static final String RULE_RECONCILIATION_BATCH_NO = "RECONCILIATION_BATCH_NO";

    /** 审计动作：对账完成。 */
    public static final String AUDIT_RECONCILIATION_COMPLETED = "RECONCILIATION_COMPLETED";

    /** 审计对象类型。 */
    static final String OBJECT_RECONCILIATION_BATCH = "RECONCILIATION_BATCH";

    private static final ZoneOffset CHINA_OFFSET = ZoneOffset.ofHours(8);

    private final ReconciliationBatchRepository batchRepository;

    private final ReconciliationDiffRepository diffRepository;

    private final PaymentRecordRepository paymentRecordRepository;

    private final ExpenseRequestRepository expenseRequestRepository;

    private final NumberGenerator numberGenerator;

    private final AuditRecorder auditRecorder;

    private final ObjectMapper objectMapper;

    private final Clock clock;

    /**
     * 构造对账服务。
     *
     * @param batchRepository          对账批次仓储
     * @param diffRepository           差异仓储
     * @param paymentRecordRepository  支付记录仓储
     * @param expenseRequestRepository 支出仓储
     * @param numberGenerator          业务编号生成器
     * @param auditRecorder            审计记录器
     * @param objectMapper             JSON 解析器
     * @param clock                    时间基准
     */
    public ReconciliationAppService(ReconciliationBatchRepository batchRepository,
                                    ReconciliationDiffRepository diffRepository,
                                    PaymentRecordRepository paymentRecordRepository,
                                    ExpenseRequestRepository expenseRequestRepository,
                                    NumberGenerator numberGenerator,
                                    AuditRecorder auditRecorder,
                                    ObjectMapper objectMapper,
                                    Clock clock) {
        this.batchRepository = batchRepository;
        this.diffRepository = diffRepository;
        this.paymentRecordRepository = paymentRecordRepository;
        this.expenseRequestRepository = expenseRequestRepository;
        this.numberGenerator = numberGenerator;
        this.auditRecorder = auditRecorder;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * 创建并执行对账批次。
     *
     * @param cmd  对账命令
     * @param auth 当前用户
     * @return 对账批次与差异
     * @throws BizException 无权限 403；期间非法 400；同期间已存在对账批次 409
     */
    @Transactional
    public ReconciliationBatchView run(ReconciliationRunCmd cmd, AuthContext auth) {
        requirePermission(auth, FundingPermissions.RECONCILE_RUN);
        validate(cmd);
        boolean duplicated = batchRepository.findByPeriod(cmd.periodStart(), cmd.periodEnd())
                .stream()
                .anyMatch(batch -> !CallbackDiffRecorder.SOURCE_CALLBACK
                        .equals(batch.getSourceBatchNo()));
        if (duplicated) {
            throw new BizException(ErrorCode.COMMON_409, "同一期间已存在对账批次");
        }

        ReconciliationBatchEntity batch = new ReconciliationBatchEntity();
        batch.setId(UuidUtils.formatCompact(UUID.randomUUID()));
        batch.setBatchNo(numberGenerator.next(RULE_RECONCILIATION_BATCH_NO));
        batch.setPeriodStart(cmd.periodStart());
        batch.setPeriodEnd(cmd.periodEnd());
        batch.setSourceBatchNo(cmd.financeBatchNo());
        batch.setStatus(ReconciliationBatchEntity.STATUS_RUNNING);
        batch.setStartedAt(OffsetDateTime.now(clock));
        batch.setCreatedBy(UuidUtils.formatCompact(auth.userId()));
        batchRepository.insert(batch);

        List<FinancePaymentEntry> financeRows = cmd.financeRows() == null
                ? List.of() : cmd.financeRows();
        List<PaymentRecordEntity> systemPayments = paymentRecordRepository.findByPaidAtBetween(
                cmd.periodStart().atStartOfDay().atOffset(CHINA_OFFSET),
                cmd.periodEnd().plusDays(1).atStartOfDay().atOffset(CHINA_OFFSET));
        List<ReconciliationDiffEntity> diffs = compare(batch.getId(), financeRows, systemPayments);
        for (ReconciliationDiffEntity diff : diffs) {
            diffRepository.insert(diff);
        }

        BigDecimal financeTotal = financeRows.stream()
                .map(FinancePaymentEntry::amount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal systemTotal = systemPayments.stream()
                .filter(payment -> PaymentRecordEntity.STATUS_APPLIED.equals(payment.getStatus()))
                .map(PaymentRecordEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        batch.complete(financeTotal, systemTotal, diffs.size(), OffsetDateTime.now(clock));
        if (batchRepository.update(batch) == 0) {
            throw new BizException(ErrorCode.COMMON_409, "对账批次并发更新冲突");
        }
        audit(batch.getId(), auth);
        return toView(batch, diffs);
    }

    /**
     * 查询对账批次列表。
     *
     * @param auth 当前用户
     * @return 批次列表（含差异）
     * @throws BizException 无权限 403
     */
    public List<ReconciliationBatchView> list(AuthContext auth) {
        requirePermission(auth, FundingPermissions.RECONCILE_VIEW);
        return batchRepository.findAll().stream()
                .map(batch -> toView(batch, diffRepository.findByBatchId(batch.getId())))
                .toList();
    }

    private List<ReconciliationDiffEntity> compare(String batchId,
                                                   List<FinancePaymentEntry> financeRows,
                                                   List<PaymentRecordEntity> systemPayments) {
        Map<String, List<FinancePaymentEntry>> financeByNo = new LinkedHashMap<>();
        for (FinancePaymentEntry entry : financeRows) {
            if (entry.amount() == null || entry.amount().compareTo(BigDecimal.ZERO) <= 0
                    || entry.externalPaymentNo() == null
                    || entry.externalPaymentNo().isBlank()) {
                throw new BizException(ErrorCode.COMMON_400, "财务支付条目单号或金额非法");
            }
            financeByNo.computeIfAbsent(entry.externalPaymentNo(), key -> new ArrayList<>())
                    .add(entry);
        }
        Map<String, PaymentRecordEntity> systemByNo = new LinkedHashMap<>();
        for (PaymentRecordEntity payment : systemPayments) {
            if (PaymentRecordEntity.STATUS_APPLIED.equals(payment.getStatus())) {
                systemByNo.putIfAbsent(payment.getExternalPaymentNo(), payment);
            }
        }

        List<ReconciliationDiffEntity> diffs = new ArrayList<>();
        for (Map.Entry<String, List<FinancePaymentEntry>> entry : financeByNo.entrySet()) {
            String externalNo = entry.getKey();
            List<FinancePaymentEntry> group = entry.getValue();
            PaymentRecordEntity systemPayment = systemByNo.get(externalNo);
            if (group.size() > 1) {
                diffs.add(diff(batchId, ReconciliationDiffEntity.TYPE_DUPLICATE,
                        systemPayment, externalNo,
                        systemPayment == null ? null : systemPayment.getAmount(),
                        group.get(0).amount()));
                continue;
            }
            FinancePaymentEntry finance = group.get(0);
            if (systemPayment == null) {
                diffs.add(diff(batchId, ReconciliationDiffEntity.TYPE_MISSING_PAYMENT,
                        null, externalNo, null, finance.amount()));
            } else if (systemPayment.getAmount().compareTo(finance.amount()) != 0) {
                diffs.add(diff(batchId, ReconciliationDiffEntity.TYPE_AMOUNT_MISMATCH,
                        systemPayment, externalNo, systemPayment.getAmount(), finance.amount()));
            } else if (isStatusLag(systemPayment)) {
                diffs.add(diff(batchId, ReconciliationDiffEntity.TYPE_STATUS_LAG,
                        systemPayment, externalNo, systemPayment.getAmount(), finance.amount()));
            }
        }
        return diffs;
    }

    private boolean isStatusLag(PaymentRecordEntity payment) {
        Optional<ExpenseRequestEntity> expense = expenseRequestRepository
                .findById(payment.getExpenseRequestId());
        return expense.isPresent()
                && ExpenseRequestEntity.STATUS_APPROVED_UNPAID.equals(expense.get().getStatus());
    }

    private ReconciliationDiffEntity diff(String batchId, String type, PaymentRecordEntity payment,
                                          String externalNo, BigDecimal systemAmount,
                                          BigDecimal financeAmount) {
        ReconciliationDiffEntity diff = new ReconciliationDiffEntity();
        diff.setId(UuidUtils.formatCompact(UUID.randomUUID()));
        diff.setBatchId(batchId);
        diff.setProjectId(payment == null ? null : payment.getProjectId());
        diff.setPaymentRecordId(payment == null ? null : payment.getId());
        diff.setDiffType(type);
        diff.setExternalPaymentNo(externalNo);
        diff.setSystemAmount(systemAmount);
        diff.setFinanceAmount(financeAmount);
        diff.setStatus(ReconciliationDiffEntity.STATUS_OPEN);
        diff.setEvidenceFileIds("[]");
        return diff;
    }

    private ReconciliationBatchView toView(ReconciliationBatchEntity batch,
                                           List<ReconciliationDiffEntity> diffs) {
        List<ReconciliationDiffView> diffViews = diffs.stream()
                .map(diff -> new ReconciliationDiffView(diff.getId(), diff.getBatchId(),
                        diff.getProjectId(), diff.getDiffType(), diff.getExternalPaymentNo(),
                        diff.getSystemAmount(), diff.getFinanceAmount(), diff.getOwnerId(),
                        diff.getStatus(), diff.getResolution(),
                        readEvidence(diff.getEvidenceFileIds()), diff.getClosedBy(),
                        diff.getClosedAt()))
                .toList();
        return new ReconciliationBatchView(batch.getId(), batch.getBatchNo(),
                batch.getPeriodStart(), batch.getPeriodEnd(), batch.getStatus(),
                batch.getTotalFinanceAmount(), batch.getTotalSystemAmount(),
                batch.getDiffCount(), diffViews);
    }

    private List<String> readEvidence(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception ex) {
            return List.of();
        }
    }

    private static void validate(ReconciliationRunCmd cmd) {
        if (cmd == null || cmd.periodStart() == null || cmd.periodEnd() == null) {
            throw new BizException(ErrorCode.COMMON_400, "对账期间不能为空");
        }
        if (cmd.periodEnd().isBefore(cmd.periodStart())) {
            throw new BizException(ErrorCode.COMMON_400, "对账期间止不得早于起");
        }
    }

    private static void requirePermission(AuthContext auth, String permission) {
        if (auth == null || auth.permissionCodes() == null
                || !auth.permissionCodes().contains(permission)) {
            throw new BizException(ErrorCode.COMMON_403, "缺少权限：" + permission);
        }
    }

    private void audit(String batchId, AuthContext auth) {
        auditRecorder.record(new AuditEvent(TraceIds.currentOrUntracked(), auth.userId(), null,
                AUDIT_RECONCILIATION_COMPLETED, OBJECT_RECONCILIATION_BATCH,
                UuidUtils.parse(batchId), null, null, null, null, null, null,
                Instant.now(clock)));
    }
}
