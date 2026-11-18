package cn.edu.rpm.output.application.service;

import cn.edu.rpm.audit.domain.model.AuditEvent;
import cn.edu.rpm.audit.domain.service.AuditRecorder;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.common.util.CanonicalJsonHasher;
import cn.edu.rpm.common.util.TraceIds;
import cn.edu.rpm.common.util.UuidUtils;
import cn.edu.rpm.file.domain.model.FileObject;
import cn.edu.rpm.file.domain.port.FileObjectQueryPort;
import cn.edu.rpm.iam.application.dto.Action;
import cn.edu.rpm.iam.application.dto.AuthContext;
import cn.edu.rpm.iam.application.dto.AuthDecision;
import cn.edu.rpm.iam.application.dto.ResourceRef;
import cn.edu.rpm.iam.domain.service.AuthorizationService;
import cn.edu.rpm.output.application.dto.CreateOutputCmd;
import cn.edu.rpm.output.application.dto.OutputCreated;
import cn.edu.rpm.output.application.dto.OutputDraftSaved;
import cn.edu.rpm.output.application.dto.SaveOutputDraftCmd;
import cn.edu.rpm.output.domain.model.OutputDraftData;
import cn.edu.rpm.output.domain.model.OutputPermissions;
import cn.edu.rpm.output.domain.model.OutputResourceTypes;
import cn.edu.rpm.output.domain.model.OutputTypeTemplate;
import cn.edu.rpm.output.domain.service.OutputDuplicateService;
import cn.edu.rpm.output.domain.service.OutputTypeTemplateResolver;
import cn.edu.rpm.output.infrastructure.entity.OutputDuplicateCandidateEntity;
import cn.edu.rpm.output.infrastructure.entity.OutputEvidenceEntity;
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
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 成果登记与修订应用服务（T-OUT-008；技术文档 05 §4.2、07 §5.1）。
 *
 * <p>登记链路：按类型模板校验元数据 → 生成 {@code research_output(DRAFT)} 与
 * {@code output_version(version_no=1)} → 保存证据与文件哈希 → 检测疑似重复
 * （提示但不阻断，{@code RPM-OUT-001}）→ 写关键审计。修订链路生成新版本，
 * 旧版本与旧审核意见保留（TC-OUT-011）。</p>
 *
 * <p>授权：登记用 {@code output:create}、保存/修订用 {@code output:edit}，
 * 均在应用服务入口通过统一授权服务判定，禁止只依赖 Controller 注解。</p>
 */
@Service
public class OutputAppService {

    private static final Logger LOG = LoggerFactory.getLogger(OutputAppService.class);

    /** 证据默认类型（请求只携带 file_id 列表；院方证据分类未定前统一写 PROOF）。 */
    static final String DEFAULT_EVIDENCE_TYPE = "PROOF";

    /** 审计对象类型。 */
    static final String OBJECT_OUTPUT = "OUTPUT";

    /** 审计动作：登记成果。 */
    static final String AUDIT_OUTPUT_CREATED = "OUTPUT_CREATED";

    /** 审计动作：保存草稿。 */
    static final String AUDIT_OUTPUT_DRAFT_SAVED = "OUTPUT_DRAFT_SAVED";

    /** 审计动作：修订成果。 */
    static final String AUDIT_OUTPUT_REVISED = "OUTPUT_REVISED";

    private final ResearchOutputRepository researchOutputRepository;

    private final OutputVersionRepository outputVersionRepository;

    private final OutputEvidenceRepository outputEvidenceRepository;

    private final OutputTypeTemplateResolver typeTemplateResolver;

    private final OutputDuplicateService duplicateService;

    private final FileObjectQueryPort fileObjectQueryPort;

    private final AuthorizationService authorizationService;

    private final AuditRecorder auditRecorder;

    private final CanonicalJsonHasher canonicalJsonHasher;

    private final Clock clock;

    /**
     * 构造成果登记服务。
     *
     * @param researchOutputRepository  成果仓储
     * @param outputVersionRepository   成果版本仓储
     * @param outputEvidenceRepository  成果证据仓储
     * @param typeTemplateResolver      类型模板解析与校验
     * @param duplicateService          查重领域服务
     * @param fileObjectQueryPort       文件对象查询端口（证据可用性校验）
     * @param authorizationService      统一授权服务
     * @param auditRecorder             审计记录器
     * @param canonicalJsonHasher       规范化 JSON 与摘要工具
     * @param clock                     时间基准
     */
    public OutputAppService(ResearchOutputRepository researchOutputRepository,
                            OutputVersionRepository outputVersionRepository,
                            OutputEvidenceRepository outputEvidenceRepository,
                            OutputTypeTemplateResolver typeTemplateResolver,
                            OutputDuplicateService duplicateService,
                            FileObjectQueryPort fileObjectQueryPort,
                            AuthorizationService authorizationService,
                            AuditRecorder auditRecorder,
                            CanonicalJsonHasher canonicalJsonHasher,
                            Clock clock) {
        this.researchOutputRepository = Objects.requireNonNull(researchOutputRepository,
                "researchOutputRepository 不能为空");
        this.outputVersionRepository = Objects.requireNonNull(outputVersionRepository,
                "outputVersionRepository 不能为空");
        this.outputEvidenceRepository = Objects.requireNonNull(outputEvidenceRepository,
                "outputEvidenceRepository 不能为空");
        this.typeTemplateResolver = Objects.requireNonNull(typeTemplateResolver,
                "typeTemplateResolver 不能为空");
        this.duplicateService = Objects.requireNonNull(duplicateService, "duplicateService 不能为空");
        this.fileObjectQueryPort = Objects.requireNonNull(fileObjectQueryPort,
                "fileObjectQueryPort 不能为空");
        this.authorizationService = Objects.requireNonNull(authorizationService,
                "authorizationService 不能为空");
        this.auditRecorder = Objects.requireNonNull(auditRecorder, "auditRecorder 不能为空");
        this.canonicalJsonHasher = Objects.requireNonNull(canonicalJsonHasher,
                "canonicalJsonHasher 不能为空");
        this.clock = Objects.requireNonNull(clock, "clock 不能为空");
    }

    /**
     * 登记成果草稿。
     *
     * <p>触发场景：完成人登记论文/专利等成果。失败语义：参数格式非法
     * {@code RPM-COMMON-400}；必填缺失 {@code RPM-COMMON-422}；无权限
     * {@code RPM-COMMON-403}；证据文件未就绪 {@code RPM-FILE-001}。
     * DOI 重复仅返回 {@code RPM-OUT-001} 提示，成果仍保存且不自动合并。</p>
     *
     * @param cmd  登记命令
     * @param auth 当前请求授权上下文
     * @return 成果 ID、状态、初始版本与重复提示
     */
    @Transactional
    public OutputCreated createDraft(CreateOutputCmd cmd, AuthContext auth) {
        Objects.requireNonNull(cmd, "cmd 不能为空");
        Objects.requireNonNull(auth, "auth 不能为空");

        LocalDate today = LocalDate.now(clock);
        OutputTypeTemplate template = typeTemplateResolver.resolve(cmd.outputType());
        String title = requireTitle(cmd.title());
        String doi = trimToNull(cmd.doi());
        String patentNo = trimToNull(cmd.patentNo());
        Map<String, Object> metadata = cmd.metadata() == null ? Map.of() : cmd.metadata();
        typeTemplateResolver.validate(new OutputDraftData(
                template.outputType(), title, doi, patentNo, cmd.completedAt(), metadata), today);

        String ownerId = trimToNull(cmd.ownerId()) == null
                ? UuidUtils.formatCompact(auth.userId())
                : UuidUtils.formatCompact(parseUuid(cmd.ownerId(), "owner_id"));
        String deptId = UuidUtils.formatCompact(parseUuid(cmd.deptId(), "dept_id"));
        String outputId = UuidUtils.formatCompact(UUID.randomUUID());
        authorize(auth, OutputPermissions.CREATE, outputId, ownerId, deptId);

        OffsetDateTime now = OffsetDateTime.now(clock);
        String actorId = UuidUtils.formatCompact(auth.userId());

        String metadataJson = canonicalJsonHasher.canonicalJson(metadata);
        String contentHash = canonicalJsonHasher.sha256(metadataJson);
        OutputVersionEntity version = OutputVersionEntity.create(
                outputId, 1, metadataJson, contentHash, actorId, now);

        ResearchOutputEntity output = ResearchOutputEntity.newDraft(
                template.outputType(), title, doi, patentNo, ownerId, deptId, cmd.completedAt());
        output.setId(outputId);
        output.setCurrentVersionId(version.getId());
        output.setCreatedBy(actorId);
        output.setUpdatedBy(actorId);
        researchOutputRepository.insert(output);
        outputVersionRepository.insert(version);
        outputEvidenceRepository.insertBatch(
                loadEvidence(cmd.evidenceFileIds(), version.getId(), now));

        List<OutputDuplicateCandidateEntity> duplicates =
                duplicateService.findCandidates(outputId, now);
        String warningCode = duplicates.isEmpty() ? null : ErrorCode.OUT_001.getCode();
        String warningMessage = duplicates.isEmpty()
                ? null : "检测到疑似重复成果，已保存草稿，请人工确认";

        auditRecorder.record(new AuditEvent(
                TraceIds.currentOrUntracked(),
                auth.userId(),
                null,
                AUDIT_OUTPUT_CREATED,
                OBJECT_OUTPUT,
                UuidUtils.parse(outputId),
                0,
                null,
                contentHash,
                "登记成果草稿",
                null,
                null,
                now.toInstant()));

        LOG.debug("成果登记完成 outputId={} type={} duplicates={}",
                outputId, template.outputType(), duplicates.size());
        return new OutputCreated(outputId, ResearchOutputEntity.STATUS_DRAFT,
                version.getId(), version.getVersionNo(), warningCode, warningMessage);
    }

    /**
     * 保存成果草稿（草稿原地更新；已审核/退回成果自动进入修订）。
     *
     * <p>触发场景：登记后编辑草稿，或对已审核/退回成果发起修订。
     * 失败语义：成果不存在 {@code RPM-COMMON-404}；状态不允许
     * {@code RPM-OUT-004}；无权限 {@code RPM-COMMON-403}；并发冲突
     * {@code RPM-COMMON-409}。</p>
     *
     * @param outputIdText 成果 ID
     * @param cmd          保存命令
     * @param auth         当前请求授权上下文
     * @return 保存后的状态与版本
     */
    @Transactional
    public OutputDraftSaved saveDraft(String outputIdText, SaveOutputDraftCmd cmd, AuthContext auth) {
        Objects.requireNonNull(cmd, "cmd 不能为空");
        Objects.requireNonNull(auth, "auth 不能为空");
        String outputId = normalizeId(outputIdText);
        ResearchOutputEntity output = requireOutput(outputId);
        authorize(auth, OutputPermissions.EDIT, outputId, output.getOwnerId(), output.getDeptId());
        if (ResearchOutputEntity.STATUS_SUBMITTED.equals(output.getStatus())
                || ResearchOutputEntity.STATUS_REVOKED.equals(output.getStatus())) {
            throw new BizException(ErrorCode.OUT_004, "当前成果状态不允许保存草稿");
        }
        if (!ResearchOutputEntity.STATUS_DRAFT.equals(output.getStatus())) {
            return reviseInternal(output, cmd, auth);
        }

        String title = requireTitle(cmd.title());
        String doi = trimToNull(cmd.doi());
        String patentNo = trimToNull(cmd.patentNo());
        Map<String, Object> metadata = cmd.metadata() == null ? Map.of() : cmd.metadata();
        typeTemplateResolver.validate(new OutputDraftData(
                output.getOutputType(), title, doi, patentNo, cmd.completedAt(), metadata),
                LocalDate.now(clock));

        OffsetDateTime now = OffsetDateTime.now(clock);
        String actorId = UuidUtils.formatCompact(auth.userId());
        int maxVersionNo = outputVersionRepository.findMaxVersionNo(outputId);
        OutputVersionEntity version = outputVersionRepository
                .findByOutputIdAndVersionNo(outputId, maxVersionNo)
                .orElseThrow(() -> new BizException(ErrorCode.COMMON_422, "成果草稿版本缺失"));
        String metadataJson = canonicalJsonHasher.canonicalJson(metadata);
        String contentHash = canonicalJsonHasher.sha256(metadataJson);
        version.setMetadata(metadataJson);
        version.setContentHash(contentHash);
        outputVersionRepository.update(version);

        output.applyMetadata(title, doi, patentNo, cmd.completedAt());
        output.setUpdatedBy(actorId);
        if (researchOutputRepository.update(output) == 0) {
            throw new BizException(ErrorCode.COMMON_409, "成果已被并发修改，请刷新后重试");
        }

        outputEvidenceRepository.deleteByOutputVersionId(version.getId());
        outputEvidenceRepository.insertBatch(
                loadEvidence(cmd.evidenceFileIds(), version.getId(), now));
        audit(output, auth, AUDIT_OUTPUT_DRAFT_SAVED, contentHash, "保存成果草稿", now);

        return new OutputDraftSaved(outputId, ResearchOutputEntity.STATUS_DRAFT,
                version.getId(), version.getVersionNo());
    }

    /**
     * 修订成果（生成新版本，旧版本与旧审核意见保留）。
     *
     * <p>触发场景：已审核/退回/拒绝成果修订后重新提交（TC-OUT-011）。
     * 失败语义同 {@link #saveDraft(String, SaveOutputDraftCmd, AuthContext)}；
     * 提交审核中的成果不可修订。</p>
     *
     * @param outputIdText 成果 ID
     * @param cmd          修订命令
     * @param auth         当前请求授权上下文
     * @return 修订后的草稿版本
     */
    @Transactional
    public OutputDraftSaved revise(String outputIdText, SaveOutputDraftCmd cmd, AuthContext auth) {
        Objects.requireNonNull(cmd, "cmd 不能为空");
        Objects.requireNonNull(auth, "auth 不能为空");
        String outputId = normalizeId(outputIdText);
        ResearchOutputEntity output = requireOutput(outputId);
        authorize(auth, OutputPermissions.EDIT, outputId, output.getOwnerId(), output.getDeptId());
        if (ResearchOutputEntity.STATUS_SUBMITTED.equals(output.getStatus())
                || ResearchOutputEntity.STATUS_REVOKED.equals(output.getStatus())) {
            throw new BizException(ErrorCode.OUT_004, "当前成果状态不允许修订");
        }
        return reviseInternal(output, cmd, auth);
    }

    private OutputDraftSaved reviseInternal(ResearchOutputEntity output, SaveOutputDraftCmd cmd,
                                            AuthContext auth) {
        String title = requireTitle(cmd.title());
        String doi = trimToNull(cmd.doi());
        String patentNo = trimToNull(cmd.patentNo());
        Map<String, Object> metadata = cmd.metadata() == null ? Map.of() : cmd.metadata();
        typeTemplateResolver.validate(new OutputDraftData(
                output.getOutputType(), title, doi, patentNo, cmd.completedAt(), metadata),
                LocalDate.now(clock));

        OffsetDateTime now = OffsetDateTime.now(clock);
        String actorId = UuidUtils.formatCompact(auth.userId());
        int nextVersionNo = outputVersionRepository.findMaxVersionNo(output.getId()) + 1;
        String metadataJson = canonicalJsonHasher.canonicalJson(metadata);
        String contentHash = canonicalJsonHasher.sha256(metadataJson);
        OutputVersionEntity version = OutputVersionEntity.create(
                output.getId(), nextVersionNo, metadataJson, contentHash, actorId, now);
        outputVersionRepository.insert(version);
        outputEvidenceRepository.insertBatch(loadEvidence(cmd.evidenceFileIds(), version.getId(), now));

        output.applyMetadata(title, doi, patentNo, cmd.completedAt());
        output.startRevision();
        output.setCurrentVersionId(version.getId());
        output.setUpdatedBy(actorId);
        if (researchOutputRepository.update(output) == 0) {
            throw new BizException(ErrorCode.COMMON_409, "成果已被并发修改，请刷新后重试");
        }
        audit(output, auth, AUDIT_OUTPUT_REVISED, contentHash, "修订成果生成新版本", now);

        return new OutputDraftSaved(output.getId(), ResearchOutputEntity.STATUS_DRAFT,
                version.getId(), version.getVersionNo());
    }

    private List<OutputEvidenceEntity> loadEvidence(List<String> evidenceFileIds,
                                                    String versionId, OffsetDateTime now) {
        if (evidenceFileIds == null || evidenceFileIds.isEmpty()) {
            return List.of();
        }
        Set<String> distinct = new LinkedHashSet<>();
        for (String fileIdText : evidenceFileIds) {
            distinct.add(UuidUtils.formatCompact(parseUuid(fileIdText, "evidence_file_ids")));
        }
        List<OutputEvidenceEntity> evidences = new ArrayList<>(distinct.size());
        for (String fileId : distinct) {
            FileObject file = fileObjectQueryPort.findById(UuidUtils.parse(fileId))
                    .orElseThrow(() -> new BizException(ErrorCode.FILE_001, "证据文件不存在或未就绪"));
            file.requireAvailable();
            evidences.add(OutputEvidenceEntity.create(
                    versionId, fileId, DEFAULT_EVIDENCE_TYPE, file.getSha256(), now));
        }
        return evidences;
    }

    private ResearchOutputEntity requireOutput(String outputId) {
        return researchOutputRepository.findById(outputId)
                .orElseThrow(() -> new BizException(ErrorCode.COMMON_404, "成果不存在"));
    }

    private void authorize(AuthContext auth, String permission,
                           String outputId, String ownerId, String deptId) {
        AuthDecision decision = authorizationService.check(auth, Action.of(permission),
                new ResourceRef(OutputResourceTypes.OUTPUT,
                        UuidUtils.parse(outputId),
                        UuidUtils.parse(ownerId),
                        UuidUtils.parse(deptId),
                        Set.of(UuidUtils.parse(ownerId))));
        if (!decision.allowed()) {
            throw new BizException(ErrorCode.COMMON_403);
        }
    }

    private void audit(ResearchOutputEntity output, AuthContext auth, String action,
                       String afterDigest, String reason, OffsetDateTime now) {
        auditRecorder.record(new AuditEvent(
                TraceIds.currentOrUntracked(),
                auth.userId(),
                null,
                action,
                OBJECT_OUTPUT,
                UuidUtils.parse(output.getId()),
                output.getVersion(),
                null,
                afterDigest,
                reason,
                null,
                null,
                now.toInstant()));
    }

    private static String normalizeId(String outputIdText) {
        return UuidUtils.formatCompact(parseUuid(outputIdText, "outputId"));
    }

    private static UUID parseUuid(String value, String field) {
        try {
            return UuidUtils.parse(value);
        } catch (IllegalArgumentException ex) {
            throw new BizException(ErrorCode.COMMON_400, field + " 非法");
        }
    }

    private static String requireTitle(String title) {
        String normalized = trimToNull(title);
        if (normalized == null) {
            throw new BizException(ErrorCode.COMMON_422, "成果标题不能为空");
        }
        return normalized;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
