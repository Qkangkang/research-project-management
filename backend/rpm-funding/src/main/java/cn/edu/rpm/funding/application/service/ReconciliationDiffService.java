package cn.edu.rpm.funding.application.service;

import cn.edu.rpm.audit.domain.model.AuditEvent;
import cn.edu.rpm.audit.domain.service.AuditRecorder;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.common.util.TraceIds;
import cn.edu.rpm.common.util.UuidUtils;
import cn.edu.rpm.funding.application.dto.CloseReconciliationDiffCmd;
import cn.edu.rpm.funding.application.dto.ReconciliationDiffView;
import cn.edu.rpm.funding.domain.model.FundingPermissions;
import cn.edu.rpm.funding.infrastructure.entity.ReconciliationDiffEntity;
import cn.edu.rpm.funding.infrastructure.repository.ReconciliationBatchRepository;
import cn.edu.rpm.funding.infrastructure.repository.ReconciliationDiffRepository;
import cn.edu.rpm.iam.application.dto.AuthContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 对账差异关闭服务（T-FUND-030；技术文档 05 §3.6）。
 *
 * <p>关闭必须填写责任人、处置说明和证据；只更新 {@code reconciliation_diff}，
 * 不提供修改/删除 {@code payment_record} 的入口。已关闭/已忽略的差异不可再次关闭。</p>
 */
@Service
public class ReconciliationDiffService {

    /** 审计动作：差异关闭。 */
    public static final String AUDIT_DIFF_CLOSED = "RECONCILIATION_DIFF_CLOSED";

    /** 审计对象类型。 */
    static final String OBJECT_DIFF = "RECONCILIATION_DIFF";

    private final ReconciliationBatchRepository batchRepository;

    private final ReconciliationDiffRepository diffRepository;

    private final AuditRecorder auditRecorder;

    private final ObjectMapper objectMapper;

    private final Clock clock;

    /**
     * 构造差异关闭服务。
     *
     * @param batchRepository 对账批次仓储
     * @param diffRepository  差异仓储
     * @param auditRecorder   审计记录器
     * @param objectMapper    JSON 序列化器
     * @param clock           时间基准
     */
    public ReconciliationDiffService(ReconciliationBatchRepository batchRepository,
                                     ReconciliationDiffRepository diffRepository,
                                     AuditRecorder auditRecorder,
                                     ObjectMapper objectMapper,
                                     Clock clock) {
        this.batchRepository = batchRepository;
        this.diffRepository = diffRepository;
        this.auditRecorder = auditRecorder;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * 关闭差异。
     *
     * @param batchId 对账批次 ID
     * @param diffId  差异 ID
     * @param cmd     关闭命令
     * @param auth    当前用户
     * @return 关闭后的差异
     * @throws BizException 无权限 403；必填缺失 400；批次/差异不存在 404；
     *                      状态不允许 409
     */
    @Transactional
    public ReconciliationDiffView close(String batchId, String diffId,
                                        CloseReconciliationDiffCmd cmd, AuthContext auth) {
        requirePermission(auth, FundingPermissions.RECONCILE_CLOSE);
        validate(cmd);
        batchRepository.findById(batchId)
                .orElseThrow(() -> new BizException(ErrorCode.COMMON_404, "对账批次不存在"));
        ReconciliationDiffEntity diff = diffRepository.findById(diffId)
                .orElseThrow(() -> new BizException(ErrorCode.COMMON_404, "对账差异不存在"));
        if (!UuidUtils.sameId(diff.getBatchId(), batchId)) {
            throw new BizException(ErrorCode.COMMON_404, "对账差异不属于该批次");
        }
        if (!ReconciliationDiffEntity.STATUS_OPEN.equals(diff.getStatus())
                && !ReconciliationDiffEntity.STATUS_PROCESSING.equals(diff.getStatus())) {
            throw new BizException(ErrorCode.COMMON_409, "对账差异已关闭或已忽略");
        }
        diff.setOwnerId(cmd.ownerId());
        diff.setEvidenceFileIds(writeEvidence(cmd.evidenceFileIds()));
        String actorId = UuidUtils.formatCompact(auth.userId());
        diff.close(cmd.resolution(), actorId, java.time.OffsetDateTime.now(clock));
        if (diffRepository.update(diff) == 0) {
            throw new BizException(ErrorCode.COMMON_409, "对账差异并发更新冲突");
        }
        auditRecorder.record(new AuditEvent(TraceIds.currentOrUntracked(), auth.userId(), null,
                AUDIT_DIFF_CLOSED, OBJECT_DIFF, UuidUtils.parse(diffId), diff.getVersion(),
                null, null, cmd.resolution(), null, null, Instant.now(clock)));
        return toView(diff);
    }

    private ReconciliationDiffView toView(ReconciliationDiffEntity diff) {
        return new ReconciliationDiffView(diff.getId(), diff.getBatchId(), diff.getProjectId(),
                diff.getDiffType(), diff.getExternalPaymentNo(), diff.getSystemAmount(),
                diff.getFinanceAmount(), diff.getOwnerId(), diff.getStatus(),
                diff.getResolution(), readEvidence(diff.getEvidenceFileIds()),
                diff.getClosedBy(), diff.getClosedAt());
    }

    private static void validate(CloseReconciliationDiffCmd cmd) {
        if (cmd == null || isBlank(cmd.resolution())) {
            throw new BizException(ErrorCode.COMMON_400, "处置说明不能为空");
        }
        if (isBlank(cmd.ownerId())) {
            throw new BizException(ErrorCode.COMMON_400, "责任人不能为空");
        }
        if (cmd.evidenceFileIds() == null || cmd.evidenceFileIds().isEmpty()
                || cmd.evidenceFileIds().stream().anyMatch(ReconciliationDiffService::isBlank)) {
            throw new BizException(ErrorCode.COMMON_400, "关闭差异必须提供证据文件");
        }
    }

    private static void requirePermission(AuthContext auth, String permission) {
        if (auth == null || auth.permissionCodes() == null
                || !auth.permissionCodes().contains(permission)) {
            throw new BizException(ErrorCode.COMMON_403, "缺少权限：" + permission);
        }
    }

    private String writeEvidence(List<String> evidenceFileIds) {
        try {
            return objectMapper.writeValueAsString(evidenceFileIds);
        } catch (Exception ex) {
            throw new BizException(ErrorCode.COMMON_400, "证据文件序列化失败");
        }
    }

    private List<String> readEvidence(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value,
                    objectMapper.getTypeFactory()
                            .constructCollectionType(List.class, String.class));
        } catch (Exception ex) {
            return List.of();
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
