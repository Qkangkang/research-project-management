package cn.edu.rpm.funding.application.service;

import cn.edu.rpm.audit.domain.model.AuditEvent;
import cn.edu.rpm.audit.domain.service.AuditRecorder;
import cn.edu.rpm.common.domain.service.NumberGenerator;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.common.util.TraceIds;
import cn.edu.rpm.common.util.UuidUtils;
import cn.edu.rpm.funding.application.dto.PaymentImportBatchView;
import cn.edu.rpm.funding.application.dto.PaymentImportRowView;
import cn.edu.rpm.funding.domain.model.FundingPermissions;
import cn.edu.rpm.funding.domain.model.PaymentImportRow;
import cn.edu.rpm.funding.domain.service.PaymentImportFileParser;
import cn.edu.rpm.funding.infrastructure.entity.ExpenseRequestEntity;
import cn.edu.rpm.funding.infrastructure.entity.PaymentImportBatchEntity;
import cn.edu.rpm.funding.infrastructure.entity.PaymentImportRowEntity;
import cn.edu.rpm.funding.infrastructure.repository.ExpenseRequestRepository;
import cn.edu.rpm.funding.infrastructure.repository.PaymentImportBatchRepository;
import cn.edu.rpm.funding.infrastructure.repository.PaymentImportRowRepository;
import cn.edu.rpm.funding.infrastructure.repository.PaymentRecordRepository;
import cn.edu.rpm.iam.application.dto.AuthContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 支付导入应用服务（T-FUND-028；技术文档 05 §3.4）。
 *
 * <p>上传创建 {@code UPLOADED} 批次并落行快照；校验由上传人与复核人分离，
 * 全部有效转 {@code READY}，有错误保持 {@code UPLOADED} 并写行级错误码；
 * 应用按批次幂等，重复应用返回首次结果，不重复写支付记录与资金事件。</p>
 */
@Service
public class PaymentImportAppService {

    /** 批次号规则编码。 */
    public static final String RULE_IMPORT_BATCH_NO = "PAYMENT_IMPORT_BATCH_NO";

    /** 错误码：外部支付单号已存在。 */
    public static final String ERROR_ALREADY_EXISTS = "IMPORT_ALREADY_EXISTS";

    /** 审计动作：上传。 */
    public static final String AUDIT_IMPORT_UPLOADED = "PAYMENT_IMPORT_UPLOADED";

    /** 审计动作：校验。 */
    public static final String AUDIT_IMPORT_VALIDATED = "PAYMENT_IMPORT_VALIDATED";

    /** 审计动作：应用。 */
    public static final String AUDIT_IMPORT_APPLIED = "PAYMENT_IMPORT_APPLIED";

    /** 审计对象类型。 */
    static final String OBJECT_IMPORT_BATCH = "PAYMENT_IMPORT_BATCH";

    private static final Set<String> SOURCE_TYPES = Set.of("CSV", "EXCEL");

    private final PaymentImportBatchRepository batchRepository;

    private final PaymentImportRowRepository rowRepository;

    private final PaymentImportFileParser fileParser;

    private final PaymentRecordRepository paymentRecordRepository;

    private final ExpenseRequestRepository expenseRequestRepository;

    private final PaymentPostingService paymentPostingService;

    private final NumberGenerator numberGenerator;

    private final AuditRecorder auditRecorder;

    private final ObjectMapper objectMapper;

    private final Clock clock;

    /**
     * 构造支付导入服务。
     *
     * @param batchRepository          批次仓储
     * @param rowRepository            行仓储
     * @param fileParser               文件解析器
     * @param paymentRecordRepository  支付记录仓储
     * @param expenseRequestRepository 支出仓储
     * @param paymentPostingService    支付入账服务
     * @param numberGenerator          业务编号生成器
     * @param auditRecorder            审计记录器
     * @param objectMapper             JSON 解析器
     * @param clock                    时间基准
     */
    public PaymentImportAppService(PaymentImportBatchRepository batchRepository,
                                   PaymentImportRowRepository rowRepository,
                                   PaymentImportFileParser fileParser,
                                   PaymentRecordRepository paymentRecordRepository,
                                   ExpenseRequestRepository expenseRequestRepository,
                                   PaymentPostingService paymentPostingService,
                                   NumberGenerator numberGenerator,
                                   AuditRecorder auditRecorder,
                                   ObjectMapper objectMapper,
                                   Clock clock) {
        this.batchRepository = batchRepository;
        this.rowRepository = rowRepository;
        this.fileParser = fileParser;
        this.paymentRecordRepository = paymentRecordRepository;
        this.expenseRequestRepository = expenseRequestRepository;
        this.paymentPostingService = paymentPostingService;
        this.numberGenerator = numberGenerator;
        this.auditRecorder = auditRecorder;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * 上传支付导入文件并创建批次。
     *
     * @param fileId     文件对象 ID
     * @param sourceType 来源类型 {@code CSV}/{@code EXCEL}
     * @param content    文件内容
     * @param auth       当前用户
     * @return 批次视图（状态 {@code UPLOADED}）
     * @throws BizException 无权限 {@code RPM-COMMON-403}；参数或文件非法
     *                      {@code RPM-COMMON-400}/{@code RPM-COMMON-422}
     */
    public PaymentImportBatchView upload(String fileId, String sourceType,
                                         byte[] content, AuthContext auth) {
        requirePermission(auth, FundingPermissions.PAYMENT_IMPORT);
        if (fileId == null || fileId.isBlank()) {
            throw new BizException(ErrorCode.COMMON_400, "导入文件 ID 不能为空");
        }
        String type = sourceType == null ? "" : sourceType.trim().toUpperCase();
        if (!SOURCE_TYPES.contains(type)) {
            throw new BizException(ErrorCode.COMMON_400, "导入来源仅支持 CSV/EXCEL");
        }
        List<PaymentImportRow> parsed = fileParser.parse(content, type);

        PaymentImportBatchEntity batch = new PaymentImportBatchEntity();
        batch.setId(UuidUtils.formatCompact(UUID.randomUUID()));
        batch.setBatchNo(numberGenerator.next(RULE_IMPORT_BATCH_NO));
        batch.setSourceType(type);
        batch.setFileId(fileId);
        batch.setStatus(PaymentImportBatchEntity.STATUS_UPLOADED);
        batch.setTotalRows(parsed.size());
        batch.setUploadedBy(UuidUtils.formatCompact(auth.userId()));
        batchRepository.insert(batch);

        for (PaymentImportRow parsedRow : parsed) {
            PaymentImportRowEntity row = new PaymentImportRowEntity();
            row.setId(UuidUtils.formatCompact(UUID.randomUUID()));
            row.setBatchId(batch.getId());
            row.setRowNo(parsedRow.rowNo());
            row.setRawData(parsedRow.rawData());
            row.setExternalPaymentNo(parsedRow.externalPaymentNo());
            row.setAmount(parsedRow.amount());
            row.setStatus(PaymentImportRowEntity.STATUS_PENDING);
            row.setErrorCode(parsedRow.errorCode());
            row.setErrorMessage(parsedRow.errorMessage());
            rowRepository.insert(row);
        }
        audit(AUDIT_IMPORT_UPLOADED, batch.getId(), sha256(content), parsed.size(), auth);
        return toView(batch, List.of(), false);
    }

    /**
     * 复核校验批次（上传人与复核人不得为同一人）。
     *
     * @param batchId 批次 ID
     * @param auth    当前用户
     * @return 批次视图
     * @throws BizException 批次不存在 {@code RPM-COMMON-404}；非复核人 403；
     *                      状态不允许 {@code RPM-COMMON-409}
     */
    public PaymentImportBatchView validate(String batchId, AuthContext auth) {
        requirePermission(auth, FundingPermissions.PAYMENT_IMPORT);
        PaymentImportBatchEntity batch = requireBatch(batchId);
        if (PaymentImportBatchEntity.STATUS_APPLIED.equals(batch.getStatus())) {
            return toView(batch, rowRepository.findByBatchId(batchId), true);
        }
        String actorId = UuidUtils.formatCompact(auth.userId());
        if (UuidUtils.sameId(batch.getUploadedBy(), actorId)) {
            throw new BizException(ErrorCode.COMMON_403, "上传人不得校验本人导入批次");
        }
        List<PaymentImportRowEntity> rows = rowRepository.findByBatchId(batchId);
        int valid = 0;
        int invalid = 0;
        for (PaymentImportRowEntity row : rows) {
            if (PaymentImportRowEntity.STATUS_PENDING.equals(row.getStatus())) {
                if (row.getErrorCode() == null || row.getErrorCode().isBlank()) {
                    row.markValid();
                } else {
                    row.markInvalid(row.getErrorCode(), row.getErrorMessage());
                }
                rowRepository.update(row);
            }
            if (PaymentImportRowEntity.STATUS_VALID.equals(row.getStatus())) {
                valid++;
            } else if (PaymentImportRowEntity.STATUS_INVALID.equals(row.getStatus())) {
                invalid++;
            }
        }
        batch.recordValidation(rows.size(), valid, invalid, actorId);
        if (batchRepository.update(batch) == 0) {
            throw new BizException(ErrorCode.COMMON_409, "导入批次并发更新冲突");
        }
        audit(AUDIT_IMPORT_VALIDATED, batchId, null, valid, auth);
        return toView(batch, rows, false);
    }

    /**
     * 复核后应用批次（按批次幂等）。
     *
     * @param batchId 批次 ID
     * @param auth    当前用户
     * @return 批次视图；重复应用 {@code idempotent=true}
     * @throws BizException 批次不存在 404；无权限 403；状态不允许 409
     */
    public PaymentImportBatchView apply(String batchId, AuthContext auth) {
        requirePermission(auth, FundingPermissions.PAYMENT_APPLY);
        PaymentImportBatchEntity batch = requireBatch(batchId);
        if (PaymentImportBatchEntity.STATUS_APPLIED.equals(batch.getStatus())) {
            return toView(batch, rowRepository.findByBatchId(batchId), true);
        }
        if (!PaymentImportBatchEntity.STATUS_READY.equals(batch.getStatus())) {
            throw new BizException(ErrorCode.COMMON_409, "导入批次未就绪，不能应用");
        }
        String actorId = UuidUtils.formatCompact(auth.userId());
        if (UuidUtils.sameId(batch.getUploadedBy(), actorId)) {
            throw new BizException(ErrorCode.COMMON_403, "上传人不得应用本人导入批次");
        }
        batch.startApplying();
        if (batchRepository.update(batch) == 0) {
            throw new BizException(ErrorCode.COMMON_409, "导入批次并发更新冲突");
        }
        List<PaymentImportRowEntity> rows = rowRepository.findByBatchId(batchId);
        for (PaymentImportRowEntity row : rows) {
            if (!PaymentImportRowEntity.STATUS_VALID.equals(row.getStatus())) {
                continue;
            }
            applyRow(row, auth);
            rowRepository.update(row);
        }
        batch.markApplied(actorId);
        if (batchRepository.update(batch) == 0) {
            throw new BizException(ErrorCode.COMMON_409, "导入批次并发更新冲突");
        }
        audit(AUDIT_IMPORT_APPLIED, batchId, null, batch.getSuccessRows(), auth);
        return toView(batch, rows, false);
    }

    private void applyRow(PaymentImportRowEntity row, AuthContext auth) {
        if (paymentRecordRepository.findByExternalPaymentNo(row.getExternalPaymentNo()).isPresent()) {
            row.markSkipped(ERROR_ALREADY_EXISTS, "外部支付单号已存在，跳过");
            return;
        }
        JsonNode raw;
        try {
            raw = objectMapper.readTree(row.getRawData());
        } catch (Exception ex) {
            row.markInvalidOnApply(PaymentImportFileParser.ERROR_FIELD_MISSING, "原始行无法解析");
            return;
        }
        String expenseNo = text(raw, "external_expense_no");
        OffsetDateTime paidAt = parsePaidAt(text(raw, "paid_at"));
        String voucherNo = text(raw, "voucher_no");
        ExpenseRequestEntity expense = expenseRequestRepository.findByRequestNo(expenseNo)
                .orElse(null);
        if (expense == null) {
            row.markInvalidOnApply(ErrorCode.FUND_009.name(), "支出单号无法匹配");
            return;
        }
        try {
            paymentPostingService.post(expense, row.getAmount(), row.getExternalPaymentNo(),
                    row.getId(), paidAt, voucherNo, row.getRawData());
            row.markApplied(expense.getId());
        } catch (BizException ex) {
            row.markInvalidOnApply(ex.getErrorCode().name(), ex.getMessage());
        } catch (IllegalStateException ex) {
            row.markInvalidOnApply(ErrorCode.FUND_009.name(), ex.getMessage());
        }
    }

    private PaymentImportBatchEntity requireBatch(String batchId) {
        return batchRepository.findById(batchId)
                .orElseThrow(() -> new BizException(ErrorCode.COMMON_404, "导入批次不存在"));
    }

    private static void requirePermission(AuthContext auth, String permission) {
        if (auth == null || auth.permissionCodes() == null
                || !auth.permissionCodes().contains(permission)) {
            throw new BizException(ErrorCode.COMMON_403, "缺少权限：" + permission);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static OffsetDateTime parsePaidAt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private PaymentImportBatchView toView(PaymentImportBatchEntity batch,
                                          List<PaymentImportRowEntity> rows, boolean idempotent) {
        List<PaymentImportRowView> rowViews = rows.stream()
                .sorted((left, right) -> Integer.compare(left.getRowNo(), right.getRowNo()))
                .map(row -> new PaymentImportRowView(row.getRowNo(), row.getExternalPaymentNo(),
                        row.getMatchedExpenseId(), row.getAmount(), row.getStatus(),
                        row.getErrorCode(), row.getErrorMessage()))
                .toList();
        return new PaymentImportBatchView(batch.getId(), batch.getBatchNo(), batch.getStatus(),
                batch.getTotalRows(), batch.getSuccessRows(), batch.getErrorRows(),
                batch.getAppliedBy(), idempotent, rowViews);
    }

    private void audit(String action, String batchId, String digest, int count, AuthContext auth) {
        auditRecorder.record(new AuditEvent(TraceIds.currentOrUntracked(), auth.userId(), null,
                action, OBJECT_IMPORT_BATCH, UuidUtils.parse(batchId), null, null, digest,
                "rows=" + count, null, null, java.time.Instant.now(clock)));
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("JDK 缺少 SHA-256", ex);
        }
    }
}
