package cn.edu.rpm.funding.api;

import cn.edu.rpm.common.api.Result;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.funding.api.dto.FundingRequests;
import cn.edu.rpm.funding.application.dto.CloseReconciliationDiffCmd;
import cn.edu.rpm.funding.application.dto.FinancePaymentEntry;
import cn.edu.rpm.funding.application.dto.PaymentCallbackCmd;
import cn.edu.rpm.funding.application.dto.PaymentCallbackResult;
import cn.edu.rpm.funding.application.dto.PaymentImportBatchView;
import cn.edu.rpm.funding.application.dto.ReconciliationBatchView;
import cn.edu.rpm.funding.application.dto.ReconciliationDiffView;
import cn.edu.rpm.funding.application.dto.ReconciliationRunCmd;
import cn.edu.rpm.funding.application.service.PaymentCallbackAppService;
import cn.edu.rpm.funding.application.service.PaymentImportAppService;
import cn.edu.rpm.funding.application.service.ReconciliationAppService;
import cn.edu.rpm.funding.application.service.ReconciliationDiffService;
import cn.edu.rpm.funding.domain.model.FundingPermissions;
import cn.edu.rpm.iam.application.annotation.RequirePermission;
import cn.edu.rpm.iam.application.context.AuthContextHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 财务接口（T-FUND-032；技术文档 05 §2、§3.3～§3.6）。
 *
 * <p>支付回调无用户身份，仅验签；上传/校验/应用/对账/差异关闭使用财务权限码。
 * 回调进入差异时返回 HTTP 202，正常入账返回 200。</p>
 */
@RestController
@RequestMapping("/api/v1")
public class FinanceController {

    private final PaymentCallbackAppService callbackAppService;

    private final PaymentImportAppService importAppService;

    private final ReconciliationAppService reconciliationAppService;

    private final ReconciliationDiffService diffService;

    private final ObjectMapper objectMapper;

    /**
     * 构造财务控制器。
     *
     * @param callbackAppService        支付回调服务
     * @param importAppService          支付导入服务
     * @param reconciliationAppService  对账服务
     * @param diffService               差异关闭服务
     * @param objectMapper              报文解析器
     */
    public FinanceController(PaymentCallbackAppService callbackAppService,
                             PaymentImportAppService importAppService,
                             ReconciliationAppService reconciliationAppService,
                             ReconciliationDiffService diffService,
                             ObjectMapper objectMapper) {
        this.callbackAppService = callbackAppService;
        this.importAppService = importAppService;
        this.reconciliationAppService = reconciliationAppService;
        this.diffService = diffService;
        this.objectMapper = objectMapper;
    }

    /**
     * 财务支付回调。
     *
     * @param rawBody   原始报文
     * @param signature HMAC-SHA256 签名
     * @return 200 正常受理；202 进入对账差异
     */
    @PostMapping("/integration/finance/payments/callback")
    public ResponseEntity<Result<PaymentCallbackResult>> callback(
            @RequestBody String rawBody,
            @RequestHeader(name = "X-Signature", required = false) String signature) {
        FundingRequests.FinanceCallbackRequest request;
        try {
            request = objectMapper.readValue(rawBody,
                    FundingRequests.FinanceCallbackRequest.class);
        } catch (Exception ex) {
            throw new BizException(ErrorCode.INT_003, "回调报文无法解析");
        }
        PaymentCallbackResult result = callbackAppService.handle(new PaymentCallbackCmd(
                rawBody, signature, request.externalEventId(), request.eventType(),
                request.externalPaymentNo(), request.externalExpenseNo(), request.projectNo(),
                request.amount(), request.currency(), request.paidAt(), request.voucherNo()));
        HttpStatus status = result.acceptedWithDiff() ? HttpStatus.ACCEPTED : HttpStatus.OK;
        return ResponseEntity.status(status).body(Result.ok(result));
    }

    /**
     * 上传支付导入批次。
     *
     * @param fileId     文件对象 ID
     * @param sourceType CSV/EXCEL
     * @param file       上传文件
     * @return 批次视图
     */
    @PostMapping(value = "/finance/payments/import",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequirePermission(FundingPermissions.PAYMENT_IMPORT)
    public Result<PaymentImportBatchView> upload(
            @RequestParam("fileId") String fileId,
            @RequestParam(value = "sourceType", defaultValue = "CSV") String sourceType,
            @RequestParam("file") MultipartFile file) {
        return Result.ok(importAppService.upload(fileId, sourceType, bytes(file),
                AuthContextHolder.require()));
    }

    /**
     * 复核校验导入批次。
     *
     * @param batchId 批次 ID
     * @return 批次视图
     */
    @PostMapping("/finance/payments/import/{batchId}/validate")
    @RequirePermission(FundingPermissions.PAYMENT_IMPORT)
    public Result<PaymentImportBatchView> validate(@PathVariable("batchId") String batchId) {
        return Result.ok(importAppService.validate(batchId, AuthContextHolder.require()));
    }

    /**
     * 应用导入批次（复核后）。
     *
     * @param batchId 批次 ID
     * @return 批次视图
     */
    @PostMapping("/finance/payments/import/{batchId}/apply")
    @RequirePermission(FundingPermissions.PAYMENT_APPLY)
    public Result<PaymentImportBatchView> apply(@PathVariable("batchId") String batchId) {
        return Result.ok(importAppService.apply(batchId, AuthContextHolder.require()));
    }

    /**
     * 对账批次列表。
     *
     * @return 批次列表
     */
    @GetMapping("/finance/reconciliations")
    @RequirePermission(FundingPermissions.RECONCILE_VIEW)
    public Result<List<ReconciliationBatchView>> listReconciliations() {
        return Result.ok(reconciliationAppService.list(AuthContextHolder.require()));
    }

    /**
     * 创建并执行对账批次。
     *
     * @param request 请求体
     * @return 对账结果
     */
    @PostMapping("/finance/reconciliations")
    @RequirePermission(FundingPermissions.RECONCILE_RUN)
    public Result<ReconciliationBatchView> run(
            @RequestBody FundingRequests.ReconciliationRunRequest request) {
        List<FinancePaymentEntry> rows = request.payments() == null ? List.of()
                : request.payments().stream()
                .map(entry -> new FinancePaymentEntry(entry.externalPaymentNo(),
                        entry.externalExpenseNo(), entry.amount()))
                .toList();
        return Result.ok(reconciliationAppService.run(new ReconciliationRunCmd(
                request.periodStart(), request.periodEnd(), request.financeBatchNo(), rows),
                AuthContextHolder.require()));
    }

    /**
     * 关闭对账差异。
     *
     * @param batchId 批次 ID
     * @param diffId  差异 ID
     * @param request 请求体
     * @return 关闭后的差异
     */
    @PostMapping("/finance/reconciliations/{batchId}/diffs/{diffId}/close")
    @RequirePermission(FundingPermissions.RECONCILE_CLOSE)
    public Result<ReconciliationDiffView> closeDiff(
            @PathVariable("batchId") String batchId,
            @PathVariable("diffId") String diffId,
            @RequestBody FundingRequests.CloseDiffRequest request) {
        return Result.ok(diffService.close(batchId, diffId,
                new CloseReconciliationDiffCmd(request.resolution(), request.ownerId(),
                        request.evidenceFileIds()), AuthContextHolder.require()));
    }

    private static byte[] bytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new BizException(ErrorCode.COMMON_400, "导入文件读取失败");
        }
    }
}
