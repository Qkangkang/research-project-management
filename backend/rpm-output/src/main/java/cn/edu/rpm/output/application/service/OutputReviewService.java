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
import cn.edu.rpm.output.application.dto.OutputReviewResult;
import cn.edu.rpm.output.application.dto.ReviewOutputCmd;
import cn.edu.rpm.output.domain.model.OutputPermissions;
import cn.edu.rpm.output.domain.model.OutputResourceTypes;
import cn.edu.rpm.output.infrastructure.entity.OutputReviewEntity;
import cn.edu.rpm.output.infrastructure.entity.OutputVersionEntity;
import cn.edu.rpm.output.infrastructure.entity.ResearchOutputEntity;
import cn.edu.rpm.output.infrastructure.repository.OutputReviewRepository;
import cn.edu.rpm.output.infrastructure.repository.OutputVersionRepository;
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
 * 成果审核应用服务（T-OUT-011；技术文档 05 §4.4、07 §5.1）。
 *
 * <p>科研管理按 {@code version_id} 审核当前提交版本：通过后成果
 * {@code APPROVED} 并绑定固定版本；退回/拒绝必须填写意见。审核记录只追加，
 * 不修改任何历史版本（TC-OUT-009 / TC-OUT-010）。</p>
 */
@Service
public class OutputReviewService {

    private static final Logger LOG = LoggerFactory.getLogger(OutputReviewService.class);

    /** 审计动作：审核成果。 */
    static final String AUDIT_OUTPUT_REVIEWED = "OUTPUT_REVIEWED";

    private final ResearchOutputRepository researchOutputRepository;

    private final OutputVersionRepository outputVersionRepository;

    private final OutputReviewRepository outputReviewRepository;

    private final AuthorizationService authorizationService;

    private final AuditRecorder auditRecorder;

    private final Clock clock;

    /**
     * 构造审核服务。
     *
     * @param researchOutputRepository 成果仓储
     * @param outputVersionRepository  成果版本仓储
     * @param outputReviewRepository   成果审核仓储
     * @param authorizationService     统一授权服务
     * @param auditRecorder            审计记录器
     * @param clock                    时间基准
     */
    public OutputReviewService(ResearchOutputRepository researchOutputRepository,
                               OutputVersionRepository outputVersionRepository,
                               OutputReviewRepository outputReviewRepository,
                               AuthorizationService authorizationService,
                               AuditRecorder auditRecorder,
                               Clock clock) {
        this.researchOutputRepository = Objects.requireNonNull(researchOutputRepository,
                "researchOutputRepository 不能为空");
        this.outputVersionRepository = Objects.requireNonNull(outputVersionRepository,
                "outputVersionRepository 不能为空");
        this.outputReviewRepository = Objects.requireNonNull(outputReviewRepository,
                "outputReviewRepository 不能为空");
        this.authorizationService = Objects.requireNonNull(authorizationService,
                "authorizationService 不能为空");
        this.auditRecorder = Objects.requireNonNull(auditRecorder, "auditRecorder 不能为空");
        this.clock = Objects.requireNonNull(clock, "clock 不能为空");
    }

    /**
     * 审核成果当前提交版本。
     *
     * <p>触发场景：科研管理审核成果。失败语义：成果或版本不存在
     * {@code RPM-COMMON-404}；无权限 {@code RPM-COMMON-403}；状态不是已提交
     * {@code RPM-OUT-004}；结论/意见非法 {@code RPM-COMMON-400}；并发冲突
     * {@code RPM-COMMON-409}。</p>
     *
     * @param outputIdText 成果 ID
     * @param cmd          审核命令
     * @param auth         当前请求授权上下文
     * @return 审核后的状态、绑定版本与审核记录
     */
    @Transactional
    public OutputReviewResult review(String outputIdText, ReviewOutputCmd cmd, AuthContext auth) {
        Objects.requireNonNull(cmd, "cmd 不能为空");
        Objects.requireNonNull(auth, "auth 不能为空");
        String outputId = UuidUtils.formatCompact(parseUuid(outputIdText, "outputId"));
        ResearchOutputEntity output = researchOutputRepository.findById(outputId)
                .orElseThrow(() -> new BizException(ErrorCode.COMMON_404, "成果不存在"));
        authorize(auth, output);

        if (!ResearchOutputEntity.STATUS_SUBMITTED.equals(output.getStatus())) {
            throw new BizException(ErrorCode.OUT_004, "仅已提交成果允许审核");
        }
        String versionId = requireVersionId(cmd.versionId());
        OutputVersionEntity version = outputVersionRepository.findById(versionId)
                .orElseThrow(() -> new BizException(ErrorCode.COMMON_404, "成果版本不存在"));
        if (!UuidUtils.sameId(version.getOutputId(), outputId)) {
            throw new BizException(ErrorCode.COMMON_400, "审核版本不属于该成果");
        }
        if (!UuidUtils.sameId(output.getCurrentVersionId(), versionId)) {
            throw new BizException(ErrorCode.OUT_004, "仅允许审核当前提交版本");
        }

        String decision = normalizeDecision(cmd.decision());
        String comment = trimToNull(cmd.comment());
        if (!OutputReviewEntity.DECISION_APPROVED.equals(decision) && comment == null) {
            throw new BizException(ErrorCode.COMMON_400, "退回或拒绝必须填写审核意见");
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        String actorId = UuidUtils.formatCompact(auth.userId());
        OutputReviewEntity review = OutputReviewEntity.create(
                versionId, actorId, decision, comment, now);
        outputReviewRepository.insert(review);

        switch (decision) {
            case OutputReviewEntity.DECISION_APPROVED -> output.approve(versionId);
            case OutputReviewEntity.DECISION_RETURNED -> output.returnForRevision();
            case OutputReviewEntity.DECISION_REJECTED -> output.reject();
            default -> throw new BizException(ErrorCode.COMMON_400, "不支持的审核结论");
        }
        output.setUpdatedBy(actorId);
        if (researchOutputRepository.update(output) == 0) {
            throw new BizException(ErrorCode.COMMON_409, "成果已被并发修改，请刷新后重试");
        }

        auditRecorder.record(new AuditEvent(
                TraceIds.currentOrUntracked(),
                auth.userId(),
                null,
                AUDIT_OUTPUT_REVIEWED,
                OutputAppService.OBJECT_OUTPUT,
                UuidUtils.parse(outputId),
                output.getVersion(),
                null,
                version.getContentHash(),
                "成果审核：" + decision,
                null,
                null,
                now.toInstant()));

        LOG.debug("成果审核完成 outputId={} versionId={} decision={}", outputId, versionId, decision);
        return new OutputReviewResult(outputId, output.getStatus(), versionId,
                review.getId(), decision);
    }

    private static String normalizeDecision(String decision) {
        String normalized = decision == null ? "" : decision.trim().toUpperCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case OutputReviewEntity.DECISION_APPROVED,
                 OutputReviewEntity.DECISION_RETURNED,
                 OutputReviewEntity.DECISION_REJECTED -> normalized;
            default -> throw new BizException(ErrorCode.COMMON_400,
                    "审核结论必须为 APPROVED / RETURNED / REJECTED");
        };
    }

    private static String requireVersionId(String versionIdText) {
        if (versionIdText == null || versionIdText.isBlank()) {
            throw new BizException(ErrorCode.COMMON_400, "versionId 不能为空");
        }
        return UuidUtils.formatCompact(parseUuid(versionIdText, "versionId"));
    }

    private void authorize(AuthContext auth, ResearchOutputEntity output) {
        AuthDecision decision = authorizationService.check(auth, Action.of(OutputPermissions.REVIEW),
                new ResourceRef(OutputResourceTypes.OUTPUT,
                        UuidUtils.parse(output.getId()),
                        UuidUtils.parse(output.getOwnerId()),
                        UuidUtils.parse(output.getDeptId()),
                        Set.of(UuidUtils.parse(output.getOwnerId()))));
        if (!decision.allowed()) {
            throw new BizException(ErrorCode.COMMON_403);
        }
        if (Objects.equals(UuidUtils.parse(output.getOwnerId()), auth.userId())) {
            throw new BizException(ErrorCode.COMMON_403);
        }
    }

    private static UUID parseUuid(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BizException(ErrorCode.COMMON_400, field + " 不能为空");
        }
        try {
            return UuidUtils.parse(value);
        } catch (IllegalArgumentException ex) {
            throw new BizException(ErrorCode.COMMON_400, field + " 非法");
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
