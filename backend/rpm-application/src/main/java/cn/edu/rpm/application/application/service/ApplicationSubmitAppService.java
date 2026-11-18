package cn.edu.rpm.application.application.service;

import cn.edu.rpm.application.application.dto.ApplicationSubmission;
import cn.edu.rpm.application.application.dto.SubmitApplicationCmd;
import cn.edu.rpm.application.domain.model.ApplicationPermissions;
import cn.edu.rpm.application.domain.model.FormValidationError;
import cn.edu.rpm.application.domain.model.ResolvedFormTemplate;
import cn.edu.rpm.application.domain.port.ApplicationWorkflowLauncher;
import cn.edu.rpm.application.domain.port.ApplicationWorkflowStart;
import cn.edu.rpm.application.domain.port.WorkflowLaunch;
import cn.edu.rpm.application.domain.service.ApplyFormTemplateResolver;
import cn.edu.rpm.application.domain.service.ApplicationContentHasher;
import cn.edu.rpm.application.infrastructure.entity.ApplicationEntity;
import cn.edu.rpm.application.infrastructure.entity.ApplicationMemberEntity;
import cn.edu.rpm.application.infrastructure.entity.ApplicationVersionEntity;
import cn.edu.rpm.application.infrastructure.repository.ApplicationMemberRepository;
import cn.edu.rpm.application.infrastructure.repository.ApplicationRepository;
import cn.edu.rpm.application.infrastructure.repository.ApplicationVersionRepository;
import cn.edu.rpm.audit.domain.model.AuditEvent;
import cn.edu.rpm.audit.domain.service.AuditRecorder;
import cn.edu.rpm.common.domain.model.OutboxEvent;
import cn.edu.rpm.common.domain.port.OutboxAppendPort;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.common.util.TraceIds;
import cn.edu.rpm.common.util.UuidUtils;
import cn.edu.rpm.file.domain.model.FileObject;
import cn.edu.rpm.file.infrastructure.repository.FileObjectRepository;
import cn.edu.rpm.iam.application.dto.Action;
import cn.edu.rpm.iam.application.dto.AuthContext;
import cn.edu.rpm.iam.application.dto.AuthDecision;
import cn.edu.rpm.iam.application.dto.ResourceRef;
import cn.edu.rpm.iam.domain.model.ResourceTypes;
import cn.edu.rpm.iam.domain.service.AuthorizationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 申报提交应用服务（T-APP-014；技术文档 04 §3.2 步骤 1～9）。
 *
 * <p>提交链路把当前草稿固化为不可变版本并启动审批：</p>
 * <ol>
 *   <li>输入校验与幂等键预检（命中即返回首次结果）；</li>
 *   <li>事务内 {@code SELECT ... FOR UPDATE} 锁定主档，串行化并发提交（TC-APP-009）；</li>
 *   <li>锁内复查幂等键，授权校验、状态、{@code expected_version}；</li>
 *   <li>表单（模板必填/类型/长度/金额/日期）与附件（必填用途 + 文件可用）校验；</li>
 *   <li>成员快照结构校验（PI 唯一、角色不重复）；</li>
 *   <li>固化 {@code application_version}（规范 JSON + SHA-256 + 幂等键，SUBMITTED）；</li>
 *   <li>写成员快照并启动审批实例；</li>
 *   <li>主档迁移 {@code IN_REVIEW} 并写 outbox（RULE-E2：写成功后才发布）与关键审计。</li>
 * </ol>
 *
 * <p>幂等：同一 {@code Idempotency-Key} 重复请求返回首次 {@code version_id} 与
 * {@code workflow_instance_id}；锁内复查保证并发同键请求不会产生双实例
 * （PostgreSQL 事务一旦因唯一冲突报错即中止，故不在事务内捕获唯一冲突后继续查询）。</p>
 */
@Service
public class ApplicationSubmitAppService {

    private static final int IDEMPOTENCY_KEY_MAX_LENGTH = 191;

    private static final int CONTRIBUTION_DESC_MAX_LENGTH = 1024;

    private static final String PI_ROLE = "PI";

    private static final String BIZ_TYPE_APPLICATION = "APPLICATION";

    private static final String EVENT_APPLICATION_SUBMITTED = "APPLICATION_SUBMITTED";

    private static final int MAX_ERROR_MESSAGE_ITEMS = 3;

    private final ApplicationRepository applicationRepository;

    private final ApplicationVersionRepository applicationVersionRepository;

    private final ApplicationMemberRepository applicationMemberRepository;

    private final ApplyFormTemplateResolver formTemplateResolver;

    private final ApplicationContentHasher contentHasher;

    private final FileObjectRepository fileObjectRepository;

    private final ApplicationWorkflowLauncher workflowLauncher;

    private final OutboxAppendPort outboxAppendPort;

    private final AuthorizationService authorizationService;

    private final AuditRecorder auditRecorder;

    private final Clock clock;

    /**
     * 构造提交服务。
     *
     * @param applicationRepository        申报主档仓储
     * @param applicationVersionRepository 申报版本仓储
     * @param applicationMemberRepository  成员快照仓储
     * @param formTemplateResolver         表单模板解析与校验
     * @param contentHasher                内容规范化与摘要
     * @param fileObjectRepository         文件元数据仓储（附件可用性校验）
     * @param workflowLauncher             审批启动端口
     * @param outboxAppendPort             Outbox 追加端口
     * @param authorizationService         统一授权服务
     * @param auditRecorder                审计记录器
     * @param clock                        时间基准
     */
    public ApplicationSubmitAppService(ApplicationRepository applicationRepository,
                                       ApplicationVersionRepository applicationVersionRepository,
                                       ApplicationMemberRepository applicationMemberRepository,
                                       ApplyFormTemplateResolver formTemplateResolver,
                                       ApplicationContentHasher contentHasher,
                                       FileObjectRepository fileObjectRepository,
                                       ApplicationWorkflowLauncher workflowLauncher,
                                       OutboxAppendPort outboxAppendPort,
                                       AuthorizationService authorizationService,
                                       AuditRecorder auditRecorder,
                                       Clock clock) {
        this.applicationRepository = Objects.requireNonNull(applicationRepository,
                "applicationRepository 不能为空");
        this.applicationVersionRepository = Objects.requireNonNull(applicationVersionRepository,
                "applicationVersionRepository 不能为空");
        this.applicationMemberRepository = Objects.requireNonNull(applicationMemberRepository,
                "applicationMemberRepository 不能为空");
        this.formTemplateResolver = Objects.requireNonNull(formTemplateResolver,
                "formTemplateResolver 不能为空");
        this.contentHasher = Objects.requireNonNull(contentHasher, "contentHasher 不能为空");
        this.fileObjectRepository = Objects.requireNonNull(fileObjectRepository,
                "fileObjectRepository 不能为空");
        this.workflowLauncher = Objects.requireNonNull(workflowLauncher, "workflowLauncher 不能为空");
        this.outboxAppendPort = Objects.requireNonNull(outboxAppendPort, "outboxAppendPort 不能为空");
        this.authorizationService = Objects.requireNonNull(authorizationService,
                "authorizationService 不能为空");
        this.auditRecorder = Objects.requireNonNull(auditRecorder, "auditRecorder 不能为空");
        this.clock = Objects.requireNonNull(clock, "clock 不能为空");
    }

    /**
     * 提交申报。
     *
     * <p>触发场景：草稿/补正状态申请人（或有提交权限的成员）提交进入审批。
     * 失败语义：参数非法 {@code RPM-COMMON-400}；无权限 {@code RPM-COMMON-403}；
     * 不存在 {@code RPM-COMMON-404}；状态不允许 {@code RPM-APPLY-004}；
     * 版本冲突/并发提交 {@code RPM-APPLY-002}；表单或必填附件缺失
     * {@code RPM-APPLY-001}；附件未就绪 {@code RPM-FILE-001}；
     * 审批模板缺失 {@code RPM-WF-001}。</p>
     *
     * @param applicationIdText 申报 ID（标准或 32 位 uuid 文本）
     * @param cmd               提交命令
     * @param idempotencyKey    {@code Idempotency-Key} 请求头
     * @param auth              当前请求授权上下文
     * @return 版本与审批实例信息
     */
    @Transactional
    public ApplicationSubmission submit(String applicationIdText, SubmitApplicationCmd cmd,
                                        String idempotencyKey, AuthContext auth) {
        Objects.requireNonNull(cmd, "cmd 不能为空");
        Objects.requireNonNull(auth, "auth 不能为空");
        String applicationId = UuidUtils.formatCompact(parseUuid(applicationIdText, "applicationId"));
        requireIdempotencyKey(idempotencyKey);
        if (cmd.formData() == null) {
            throw new BizException(ErrorCode.APPLY_001, "表单数据不能为空");
        }

        Optional<ApplicationVersionEntity> replayed =
                applicationVersionRepository.findByIdempotencyKey(idempotencyKey);
        if (replayed.isPresent()) {
            return replay(replayed.get());
        }

        ApplicationEntity application = applicationRepository.selectForUpdate(applicationId)
                .orElseThrow(() -> new BizException(ErrorCode.COMMON_404, "申报不存在"));

        Optional<ApplicationVersionEntity> lockedReplay =
                applicationVersionRepository.findByIdempotencyKey(idempotencyKey);
        if (lockedReplay.isPresent()) {
            return replay(lockedReplay.get());
        }

        requireSubmitPermission(application, auth);
        // 先校验 expected_version 再校验状态：并发提交的失败方必须得到
        // RPM-APPLY-002（TC-APP-009），而不是因首个请求已推进状态而得到 APPLY-004。
        if (!Objects.equals(application.getVersion(), cmd.expectedVersion())) {
            throw new BizException(ErrorCode.APPLY_002, "申报版本已变化，请刷新后重试");
        }
        requireSubmittableStatus(application);

        ResolvedFormTemplate template = formTemplateResolver.resolve(application.getProjectType());
        requireValidForm(template, cmd.formData());
        requireReadyAttachments(template, cmd.attachments());
        List<ApplicationMemberEntity> members = buildMembers(cmd.collaborators(), application);

        OffsetDateTime now = OffsetDateTime.now(clock);
        String canonicalJson = contentHasher.canonicalJson(cmd.formData());
        String contentHash = contentHasher.sha256(canonicalJson);
        String actorId = UuidUtils.formatCompact(auth.userId());
        ApplicationVersionEntity version = persistVersion(application, canonicalJson, contentHash,
                actorId, now, idempotencyKey);
        String versionId = version.getId();

        for (ApplicationMemberEntity member : members) {
            member.setApplicationVersionId(versionId);
        }
        applicationMemberRepository.insertBatch(members);

        WorkflowLaunch launch = workflowLauncher.start(new ApplicationWorkflowStart(
                UuidUtils.parse(applicationId), UuidUtils.parse(versionId),
                application.getProjectType(), parseOptionalUuid(application.getDeptId()),
                UuidUtils.parse(application.getApplicantId()), now));

        application.markInReview(versionId);
        if (applicationRepository.update(application) == 0) {
            throw new BizException(ErrorCode.APPLY_002, "并发提交冲突，请刷新后重试");
        }

        outboxAppendPort.append(new OutboxEvent(
                UUID.randomUUID(),
                BIZ_TYPE_APPLICATION,
                UuidUtils.parse(applicationId),
                EVENT_APPLICATION_SUBMITTED,
                outboxPayload(application, version, launch),
                TraceIds.currentOrUntracked(),
                EVENT_APPLICATION_SUBMITTED + ":" + idempotencyKey));

        auditRecorder.record(new AuditEvent(
                TraceIds.currentOrUntracked(),
                auth.userId(),
                null,
                "APPLICATION_SUBMITTED",
                BIZ_TYPE_APPLICATION,
                UuidUtils.parse(applicationId),
                version.getVersionNo(),
                null,
                version.getContentHash(),
                "提交申报",
                null,
                null,
                clock.instant()));

        return new ApplicationSubmission(applicationId, versionId,
                version.getVersionNo(), ApplicationEntity.STATUS_IN_REVIEW,
                launch.instanceId().toString());
    }

    private ApplicationSubmission replay(ApplicationVersionEntity version) {
        UUID applicationId = UuidUtils.parse(version.getApplicationId());
        UUID versionId = UuidUtils.parse(version.getId());
        WorkflowLaunch launch = workflowLauncher.findLatest(applicationId, versionId)
                .orElseThrow(() -> new BizException(ErrorCode.SYS_002, "幂等回放缺少审批实例"));
        return new ApplicationSubmission(version.getApplicationId(), version.getId(),
                version.getVersionNo() == null ? 0 : version.getVersionNo(),
                ApplicationEntity.STATUS_IN_REVIEW, launch.instanceId().toString());
    }

    private ApplicationVersionEntity persistVersion(ApplicationEntity application, String canonicalJson,
                                                    String contentHash, String actorId,
                                                    OffsetDateTime now, String idempotencyKey) {
        Optional<ApplicationVersionEntity> draft =
                applicationVersionRepository.findDraftByApplicationId(application.getId());
        if (draft.isPresent()) {
            ApplicationVersionEntity version = draft.get();
            version.promoteToSubmitted(canonicalJson, contentHash, actorId, now, idempotencyKey);
            if (applicationVersionRepository.update(version) == 0) {
                throw new BizException(ErrorCode.APPLY_002, "申报草稿已被并发修改，请刷新后重试");
            }
            return version;
        }
        int nextVersionNo = applicationVersionRepository.findMaxVersionNo(application.getId()) + 1;
        ApplicationVersionEntity version = ApplicationVersionEntity.newSubmitted(
                application.getId(), nextVersionNo, canonicalJson, contentHash, actorId, now,
                idempotencyKey);
        applicationVersionRepository.insert(version);
        return version;
    }

    private void requireSubmitPermission(ApplicationEntity application, AuthContext auth) {
        AuthDecision decision = authorizationService.check(auth,
                Action.of(ApplicationPermissions.SUBMIT),
                new ResourceRef(ResourceTypes.PROJECT, UuidUtils.parse(application.getId()),
                        parseOptionalUuid(application.getApplicantId()),
                        parseOptionalUuid(application.getDeptId()), Set.of()));
        if (!decision.allowed()) {
            throw new BizException(ErrorCode.COMMON_403);
        }
    }

    private static void requireSubmittableStatus(ApplicationEntity application) {
        if (!ApplicationEntity.STATUS_DRAFT.equals(application.getStatus())
                && !ApplicationEntity.STATUS_SUPPLEMENT.equals(application.getStatus())) {
            throw new BizException(ErrorCode.APPLY_004, "当前状态不允许提交");
        }
    }

    private void requireValidForm(ResolvedFormTemplate template, Map<String, Object> formData) {
        List<FormValidationError> errors = formTemplateResolver.validate(template, formData);
        if (!errors.isEmpty()) {
            String detail = errors.stream()
                    .limit(MAX_ERROR_MESSAGE_ITEMS)
                    .map(error -> error.field() + ": " + error.message())
                    .collect(java.util.stream.Collectors.joining("；"));
            throw new BizException(ErrorCode.APPLY_001, "表单校验失败：" + detail);
        }
    }

    private void requireReadyAttachments(ResolvedFormTemplate template,
                                         List<SubmitApplicationCmd.AttachmentCmd> attachments) {
        List<SubmitApplicationCmd.AttachmentCmd> items =
                attachments == null ? List.of() : attachments;
        Set<String> providedPurposes = new HashSet<>();
        for (SubmitApplicationCmd.AttachmentCmd attachment : items) {
            if (attachment == null || attachment.purpose() == null || attachment.purpose().isBlank()) {
                throw new BizException(ErrorCode.APPLY_001, "附件用途不能为空");
            }
            providedPurposes.add(attachment.purpose().trim().toUpperCase(Locale.ROOT));
        }
        for (String required : template.requiredAttachments()) {
            if (!providedPurposes.contains(required.toUpperCase(Locale.ROOT))) {
                throw new BizException(ErrorCode.APPLY_001, "缺少必填附件: " + required);
            }
        }
        if (items.isEmpty()) {
            return;
        }
        Map<UUID, FileObject> files = loadFiles(items);
        for (SubmitApplicationCmd.AttachmentCmd attachment : items) {
            UUID fileId = parseUuid(attachment.fileId(), "attachment.file_id");
            FileObject file = files.get(fileId);
            if (file == null) {
                throw new BizException(ErrorCode.FILE_001, "附件不存在或不可用");
            }
            file.requireAvailable();
        }
    }

    private Map<UUID, FileObject> loadFiles(List<SubmitApplicationCmd.AttachmentCmd> attachments) {
        List<UUID> fileIds = new ArrayList<>(attachments.size());
        for (SubmitApplicationCmd.AttachmentCmd attachment : attachments) {
            fileIds.add(parseUuid(attachment.fileId(), "attachment.file_id"));
        }
        Map<UUID, FileObject> files = new HashMap<>();
        for (FileObject file : fileObjectRepository.findByIds(fileIds)) {
            files.put(file.getId(), file);
        }
        return files;
    }

    private static List<ApplicationMemberEntity> buildMembers(
            List<SubmitApplicationCmd.CollaboratorCmd> collaborators, ApplicationEntity application) {
        List<ApplicationMemberEntity> members = new ArrayList<>();
        String applicantId = application.getApplicantId();
        members.add(member(applicantId, PI_ROLE, null, 0));
        Set<String> identities = new HashSet<>();
        identities.add(applicantId + "|" + PI_ROLE);

        List<SubmitApplicationCmd.CollaboratorCmd> items =
                collaborators == null ? List.of() : collaborators;
        int sortOrder = 1;
        for (SubmitApplicationCmd.CollaboratorCmd collaborator : items) {
            if (collaborator == null) {
                throw new BizException(ErrorCode.APPLY_001, "协作成员不能为空");
            }
            UUID userId = parseUuid(collaborator.userId(), "collaborator.user_id");
            String userIdText = UuidUtils.formatCompact(userId);
            String role = collaborator.role() == null ? "" : collaborator.role().trim().toUpperCase(Locale.ROOT);
            if (role.isEmpty()) {
                throw new BizException(ErrorCode.APPLY_001, "协作成员角色不能为空");
            }
            if (PI_ROLE.equals(role)) {
                throw new BizException(ErrorCode.APPLY_001, "PI 只能为申请人本人");
            }
            if (UuidUtils.sameId(applicantId, userId)) {
                throw new BizException(ErrorCode.APPLY_001, "申请人已作为 PI 存在于成员快照");
            }
            if (!identities.add(userIdText + "|" + role)) {
                throw new BizException(ErrorCode.APPLY_001, "协作成员角色重复");
            }
            String contribution = collaborator.contributionDesc();
            if (contribution != null
                    && contribution.codePointCount(0, contribution.length()) > CONTRIBUTION_DESC_MAX_LENGTH) {
                throw new BizException(ErrorCode.APPLY_001, "贡献说明长度不能超过 1024 字符");
            }
            members.add(member(userIdText, role, contribution, sortOrder++));
        }
        return members;
    }

    private static ApplicationMemberEntity member(String userId, String role, String contribution,
                                                 int sortOrder) {
        ApplicationMemberEntity entity = new ApplicationMemberEntity();
        entity.setUserId(userId);
        entity.setMemberRole(role);
        entity.setContributionDesc(contribution);
        entity.setSortOrder(sortOrder);
        entity.setVersion(0);
        return entity;
    }

    private static Map<String, Object> outboxPayload(ApplicationEntity application,
                                                     ApplicationVersionEntity version,
                                                     WorkflowLaunch launch) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("application_id", application.getId());
        payload.put("application_no", application.getApplicationNo());
        payload.put("version_id", version.getId());
        payload.put("version_no", version.getVersionNo());
        payload.put("workflow_instance_id", launch.instanceId().toString());
        payload.put("submitted_by", version.getSubmittedBy());
        payload.put("submitted_at", version.getSubmittedAt() == null
                ? null : version.getSubmittedAt().toString());
        return payload;
    }

    private static void requireIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BizException(ErrorCode.COMMON_400, "缺少 Idempotency-Key 请求头");
        }
        if (idempotencyKey.length() > IDEMPOTENCY_KEY_MAX_LENGTH) {
            throw new BizException(ErrorCode.COMMON_400, "Idempotency-Key 长度不能超过 191");
        }
    }

    private static UUID parseUuid(String value, String field) {
        try {
            return UuidUtils.parse(value);
        } catch (IllegalArgumentException ex) {
            throw new BizException(ErrorCode.COMMON_400, field + " 非法");
        }
    }

    private static UUID parseOptionalUuid(String value) {
        return value == null || value.isBlank() ? null : parseUuid(value, "uuid");
    }
}
