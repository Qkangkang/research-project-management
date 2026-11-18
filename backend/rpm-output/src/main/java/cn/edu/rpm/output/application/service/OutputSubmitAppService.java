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
import cn.edu.rpm.output.application.dto.OutputSubmission;
import cn.edu.rpm.output.domain.model.OutputPermissions;
import cn.edu.rpm.output.domain.model.OutputResourceTypes;
import cn.edu.rpm.output.infrastructure.entity.OutputVersionEntity;
import cn.edu.rpm.output.infrastructure.entity.ResearchOutputEntity;
import cn.edu.rpm.output.infrastructure.repository.OutputEvidenceRepository;
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
 * 成果提交审核应用服务（T-OUT-009；技术文档 07 §5.1）。
 *
 * <p>提交前置：成果为草稿、至少一份证据；提交时固定当前版本
 * （{@code research_output.current_version_id}），之后版本不可原地修改。
 * 无证据返回 {@code RPM-OUT-002} 且成果保持 {@code DRAFT}（TC-OUT-005）。</p>
 *
 * <p>审核人通知由 rpm-notification 的待办链路在后续批次接入；本批次按任务
 * 约束先落审计 {@code OUTPUT_SUBMITTED}，通知装配留待通知模块提供端口后补齐。</p>
 */
@Service
public class OutputSubmitAppService {

    private static final Logger LOG = LoggerFactory.getLogger(OutputSubmitAppService.class);

    /** 审计动作：提交成果审核。 */
    static final String AUDIT_OUTPUT_SUBMITTED = "OUTPUT_SUBMITTED";

    private final ResearchOutputRepository researchOutputRepository;

    private final OutputVersionRepository outputVersionRepository;

    private final OutputEvidenceRepository outputEvidenceRepository;

    private final AuthorizationService authorizationService;

    private final AuditRecorder auditRecorder;

    private final Clock clock;

    /**
     * 构造提交服务。
     *
     * @param researchOutputRepository 成果仓储
     * @param outputVersionRepository  成果版本仓储
     * @param outputEvidenceRepository 成果证据仓储
     * @param authorizationService     统一授权服务
     * @param auditRecorder            审计记录器
     * @param clock                    时间基准
     */
    public OutputSubmitAppService(ResearchOutputRepository researchOutputRepository,
                                  OutputVersionRepository outputVersionRepository,
                                  OutputEvidenceRepository outputEvidenceRepository,
                                  AuthorizationService authorizationService,
                                  AuditRecorder auditRecorder,
                                  Clock clock) {
        this.researchOutputRepository = Objects.requireNonNull(researchOutputRepository,
                "researchOutputRepository 不能为空");
        this.outputVersionRepository = Objects.requireNonNull(outputVersionRepository,
                "outputVersionRepository 不能为空");
        this.outputEvidenceRepository = Objects.requireNonNull(outputEvidenceRepository,
                "outputEvidenceRepository 不能为空");
        this.authorizationService = Objects.requireNonNull(authorizationService,
                "authorizationService 不能为空");
        this.auditRecorder = Objects.requireNonNull(auditRecorder, "auditRecorder 不能为空");
        this.clock = Objects.requireNonNull(clock, "clock 不能为空");
    }

    /**
     * 提交成果审核。
     *
     * <p>触发场景：完成人提交草稿成果。失败语义：成果不存在
     * {@code RPM-COMMON-404}；无权限 {@code RPM-COMMON-403}；状态不是草稿
     * {@code RPM-OUT-004}；无证据 {@code RPM-OUT-002}；并发冲突
     * {@code RPM-COMMON-409}。</p>
     *
     * @param outputIdText 成果 ID
     * @param auth         当前请求授权上下文
     * @return 提交后的状态与固定版本
     */
    @Transactional
    public OutputSubmission submit(String outputIdText, AuthContext auth) {
        Objects.requireNonNull(auth, "auth 不能为空");
        String outputId = UuidUtils.formatCompact(parseUuid(outputIdText));
        ResearchOutputEntity output = researchOutputRepository.findById(outputId)
                .orElseThrow(() -> new BizException(ErrorCode.COMMON_404, "成果不存在"));
        authorize(auth, output, OutputPermissions.SUBMIT);

        if (!ResearchOutputEntity.STATUS_DRAFT.equals(output.getStatus())) {
            throw new BizException(ErrorCode.OUT_004, "仅草稿成果允许提交审核");
        }

        OutputVersionEntity version = resolveCurrentVersion(output);
        if (outputEvidenceRepository.countByOutputVersionId(version.getId()) <= 0) {
            throw new BizException(ErrorCode.OUT_002, "提交审核前必须至少上传一份证据材料");
        }

        output.submit(version.getId());
        output.setUpdatedBy(UuidUtils.formatCompact(auth.userId()));
        if (researchOutputRepository.update(output) == 0) {
            throw new BizException(ErrorCode.COMMON_409, "成果已被并发修改，请刷新后重试");
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        auditRecorder.record(new AuditEvent(
                TraceIds.currentOrUntracked(),
                auth.userId(),
                null,
                AUDIT_OUTPUT_SUBMITTED,
                OutputAppService.OBJECT_OUTPUT,
                UuidUtils.parse(outputId),
                output.getVersion(),
                null,
                version.getContentHash(),
                "提交成果审核",
                null,
                null,
                now.toInstant()));

        LOG.debug("成果提交审核 outputId={} versionNo={}", outputId, version.getVersionNo());
        return new OutputSubmission(outputId, ResearchOutputEntity.STATUS_SUBMITTED,
                version.getId(), version.getVersionNo());
    }

    private OutputVersionEntity resolveCurrentVersion(ResearchOutputEntity output) {
        if (output.getCurrentVersionId() != null && !output.getCurrentVersionId().isBlank()) {
            return outputVersionRepository.findById(output.getCurrentVersionId())
                    .orElseThrow(() -> new BizException(ErrorCode.COMMON_422, "成果当前版本缺失"));
        }
        int maxVersionNo = outputVersionRepository.findMaxVersionNo(output.getId());
        return outputVersionRepository.findByOutputIdAndVersionNo(output.getId(), maxVersionNo)
                .orElseThrow(() -> new BizException(ErrorCode.COMMON_422, "成果版本缺失"));
    }

    private void authorize(AuthContext auth, ResearchOutputEntity output, String permission) {
        AuthDecision decision = authorizationService.check(auth, Action.of(permission),
                new ResourceRef(OutputResourceTypes.OUTPUT,
                        UuidUtils.parse(output.getId()),
                        UuidUtils.parse(output.getOwnerId()),
                        UuidUtils.parse(output.getDeptId()),
                        Set.of(UuidUtils.parse(output.getOwnerId()))));
        if (!decision.allowed()) {
            throw new BizException(ErrorCode.COMMON_403);
        }
    }

    private static UUID parseUuid(String value) {
        try {
            return UuidUtils.parse(value);
        } catch (IllegalArgumentException ex) {
            throw new BizException(ErrorCode.COMMON_400, "outputId 非法");
        }
    }
}
