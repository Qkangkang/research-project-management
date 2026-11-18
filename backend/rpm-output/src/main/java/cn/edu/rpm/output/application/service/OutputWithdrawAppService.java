package cn.edu.rpm.output.application.service;

import cn.edu.rpm.audit.domain.model.AuditEvent;
import cn.edu.rpm.audit.domain.service.AuditRecorder;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.common.util.TraceIds;
import cn.edu.rpm.common.util.UuidUtils;
import cn.edu.rpm.iam.application.dto.Action;
import cn.edu.rpm.iam.application.dto.AuthContext;
import cn.edu.rpm.iam.application.dto.AuthDecision;
import cn.edu.rpm.iam.application.dto.ResourceRef;
import cn.edu.rpm.iam.domain.service.AuthorizationService;
import cn.edu.rpm.output.application.dto.OutputWithdrawResult;
import cn.edu.rpm.output.application.dto.WithdrawOutputCmd;
import cn.edu.rpm.output.application.dto.WithdrawReviewCmd;
import cn.edu.rpm.output.domain.model.OutputPermissions;
import cn.edu.rpm.output.domain.model.OutputResourceTypes;
import cn.edu.rpm.output.infrastructure.entity.ResearchOutputEntity;
import cn.edu.rpm.output.infrastructure.repository.ResearchOutputRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 成果撤回与撤回审核应用服务（T-OUT-013；技术文档 08 §14.1、07 §5.2）。
 *
 * <p>两段式流程：</p>
 * <ol>
 *   <li>完成人发起撤回申请（{@code withdraw}）：理由必填，写审计
 *       {@code OUTPUT_WITHDRAW_REQUESTED}，成果状态不变；</li>
 *   <li>科研管理审核（{@code reviewWithdraw}）：通过后成果置 {@code REVOKED}
 *       （DDL {@code ck_research_output_status} 无 {@code WITHDRAWN}，V02/09 均以
 *       {@code REVOKED} 表达撤回），写审计 {@code OUTPUT_WITHDRAWN}；不改变历史版本、
 *       证据、项目关联记录与已冻结的验收快照（RES-06）。</li>
 * </ol>
 *
 * <p>V01 基线无「撤回申请单」表，V1 以审计事件承载申请理由与审核意见；
 * 若院方要求严格两阶段持久化，需在后续迁移中新增申请表并回归本服务，
 * 已在 Batch-20 报告中列为已知偏差。</p>
 */
@Service
public class OutputWithdrawAppService {

    private static final Logger LOG = LoggerFactory.getLogger(OutputWithdrawAppService.class);

    /** 审计动作：撤回申请。 */
    static final String AUDIT_OUTPUT_WITHDRAW_REQUESTED = "OUTPUT_WITHDRAW_REQUESTED";

    /** 审计动作：撤回审核通过。 */
    static final String AUDIT_OUTPUT_WITHDRAWN = "OUTPUT_WITHDRAWN";

    /** 审计动作：撤回审核拒绝。 */
    static final String AUDIT_OUTPUT_WITHDRAW_REJECTED = "OUTPUT_WITHDRAW_REJECTED";

    private final ResearchOutputRepository researchOutputRepository;

    private final AuthorizationService authorizationService;

    private final AuditRecorder auditRecorder;

    private final Clock clock;

    /**
     * 构造撤回服务。
     *
     * @param researchOutputRepository 成果仓储
     * @param authorizationService     统一授权服务
     * @param auditRecorder            审计记录器
     * @param clock                    时间基准
     */
    public OutputWithdrawAppService(ResearchOutputRepository researchOutputRepository,
                                    AuthorizationService authorizationService,
                                    AuditRecorder auditRecorder,
                                    Clock clock) {
        this.researchOutputRepository = Objects.requireNonNull(researchOutputRepository,
                "researchOutputRepository 不能为空");
        this.authorizationService = Objects.requireNonNull(authorizationService,
                "authorizationService 不能为空");
        this.auditRecorder = Objects.requireNonNull(auditRecorder, "auditRecorder 不能为空");
        this.clock = Objects.requireNonNull(clock, "clock 不能为空");
    }

    /**
     * 发起成果撤回申请（成果所有者）。
     *
     * <p>失败语义：理由为空 {@code RPM-COMMON-400}；成果不存在
     * {@code RPM-COMMON-404}；非所有者/无编辑权限 {@code RPM-COMMON-403}；
     * 已撤回 {@code RPM-OUT-004}。</p>
     *
     * @param outputIdText 成果 ID
     * @param cmd          撤回申请命令
     * @param auth         当前请求授权上下文
     * @return 当前状态与是否需要科研管理审核
     */
    @Transactional
    public OutputWithdrawResult withdraw(String outputIdText, WithdrawOutputCmd cmd, AuthContext auth) {
        Objects.requireNonNull(cmd, "cmd 不能为空");
        Objects.requireNonNull(auth, "auth 不能为空");
        String reason = trimToNull(cmd.reason());
        if (reason == null) {
            throw new BizException(ErrorCode.COMMON_400, "撤回理由不能为空");
        }
        if (reason.length() > 1024) {
            throw new BizException(ErrorCode.COMMON_400, "撤回理由不能超过 1024 字符");
        }
        String outputId = UuidUtils.formatCompact(parseUuid(outputIdText));
        ResearchOutputEntity output = researchOutputRepository.findById(outputId)
                .orElseThrow(() -> new BizException(ErrorCode.COMMON_404, "成果不存在"));
        authorizeOwner(auth, output);
        if (ResearchOutputEntity.STATUS_REVOKED.equals(output.getStatus())) {
            throw new BizException(ErrorCode.OUT_004, "成果已撤回");
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        auditRecorder.record(new AuditEvent(
                TraceIds.currentOrUntracked(),
                auth.userId(),
                null,
                AUDIT_OUTPUT_WITHDRAW_REQUESTED,
                OutputAppService.OBJECT_OUTPUT,
                UuidUtils.parse(outputId),
                output.getVersion(),
                null,
                null,
                "撤回申请：" + reason,
                null,
                null,
                now.toInstant()));

        LOG.debug("成果撤回申请 outputId={} owner={}", outputId, output.getOwnerId());
        return new OutputWithdrawResult(outputId, output.getStatus(), true);
    }

    /**
     * 科研管理审核撤回申请。
     *
     * <p>通过后状态迁移为 {@code REVOKED}；拒绝时状态不变。历史版本、证据、
     * 关联记录与验收快照引用均不受影响。失败语义：成果不存在
     * {@code RPM-COMMON-404}；无审核权限或审核本人成果 {@code RPM-COMMON-403}；
     * 已撤回 {@code RPM-OUT-004}；并发冲突 {@code RPM-COMMON-409}。</p>
     *
     * @param outputIdText 成果 ID
     * @param cmd          撤回审核命令
     * @param auth         当前请求授权上下文
     * @return 审核后的状态
     */
    @Transactional
    public OutputWithdrawResult reviewWithdraw(String outputIdText, WithdrawReviewCmd cmd,
                                               AuthContext auth) {
        Objects.requireNonNull(cmd, "cmd 不能为空");
        Objects.requireNonNull(auth, "auth 不能为空");
        String outputId = UuidUtils.formatCompact(parseUuid(outputIdText));
        ResearchOutputEntity output = researchOutputRepository.findById(outputId)
                .orElseThrow(() -> new BizException(ErrorCode.COMMON_404, "成果不存在"));
        authorizeReviewer(auth, output);
        if (ResearchOutputEntity.STATUS_REVOKED.equals(output.getStatus())) {
            throw new BizException(ErrorCode.OUT_004, "成果已撤回");
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        String comment = trimToNull(cmd.comment());
        String action;
        if (cmd.approved()) {
            output.withdraw();
            output.setUpdatedBy(UuidUtils.formatCompact(auth.userId()));
            if (researchOutputRepository.update(output) == 0) {
                throw new BizException(ErrorCode.COMMON_409, "成果已被并发修改，请刷新后重试");
            }
            action = AUDIT_OUTPUT_WITHDRAWN;
        } else {
            action = AUDIT_OUTPUT_WITHDRAW_REJECTED;
        }

        String reason = comment == null ? "撤回审核" : "撤回审核：" + comment;
        auditRecorder.record(new AuditEvent(
                TraceIds.currentOrUntracked(),
                auth.userId(),
                null,
                action,
                OutputAppService.OBJECT_OUTPUT,
                UuidUtils.parse(outputId),
                output.getVersion(),
                null,
                null,
                reason,
                null,
                null,
                now.toInstant()));

        LOG.debug("成果撤回审核 outputId={} approved={} status={}",
                outputId, cmd.approved(), output.getStatus());
        return new OutputWithdrawResult(outputId, output.getStatus(), false);
    }

    private void authorizeOwner(AuthContext auth, ResearchOutputEntity output) {
        AuthDecision decision = authorizationService.check(auth, Action.of(OutputPermissions.EDIT),
                resource(output));
        if (!decision.allowed() || !Objects.equals(UuidUtils.parse(output.getOwnerId()), auth.userId())) {
            throw new BizException(ErrorCode.COMMON_403);
        }
    }

    private void authorizeReviewer(AuthContext auth, ResearchOutputEntity output) {
        AuthDecision decision = authorizationService.check(auth, Action.of(OutputPermissions.REVIEW),
                resource(output));
        if (!decision.allowed()) {
            throw new BizException(ErrorCode.COMMON_403);
        }
        if (Objects.equals(UuidUtils.parse(output.getOwnerId()), auth.userId())) {
            throw new BizException(ErrorCode.COMMON_403);
        }
    }

    private static ResourceRef resource(ResearchOutputEntity output) {
        return new ResourceRef(OutputResourceTypes.OUTPUT,
                UuidUtils.parse(output.getId()),
                UuidUtils.parse(output.getOwnerId()),
                UuidUtils.parse(output.getDeptId()),
                Set.of(UuidUtils.parse(output.getOwnerId())));
    }

    private static UUID parseUuid(String value) {
        try {
            return UuidUtils.parse(value);
        } catch (IllegalArgumentException ex) {
            throw new BizException(ErrorCode.COMMON_400, "outputId 非法");
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
