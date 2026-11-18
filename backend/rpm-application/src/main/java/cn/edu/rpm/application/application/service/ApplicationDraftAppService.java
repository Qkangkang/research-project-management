package cn.edu.rpm.application.application.service;

import cn.edu.rpm.application.application.dto.ApplicationDraftSaved;
import cn.edu.rpm.application.application.dto.SaveDraftCmd;
import cn.edu.rpm.application.domain.model.ApplicationPermissions;
import cn.edu.rpm.application.domain.service.ApplicationContentHasher;
import cn.edu.rpm.application.infrastructure.entity.ApplicationEntity;
import cn.edu.rpm.application.infrastructure.entity.ApplicationVersionEntity;
import cn.edu.rpm.application.infrastructure.repository.ApplicationRepository;
import cn.edu.rpm.application.infrastructure.repository.ApplicationVersionRepository;
import cn.edu.rpm.audit.domain.model.AuditEvent;
import cn.edu.rpm.audit.domain.service.AuditRecorder;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.common.util.TraceIds;
import cn.edu.rpm.common.util.UuidUtils;
import cn.edu.rpm.iam.application.dto.Action;
import cn.edu.rpm.iam.application.dto.AuthContext;
import cn.edu.rpm.iam.application.dto.ResourceRef;
import cn.edu.rpm.iam.domain.model.ResourceTypes;
import cn.edu.rpm.iam.domain.service.AuthorizationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 申报草稿保存应用服务（TC-APP-003；技术文档 04 §3.1 保存草稿）。
 *
 * <p>任务拆解 Batch-9 未单列草稿服务，但 {@code PUT /applications/{id}/draft}
 * 与 TC-APP-003 需要落地，按「缺失补齐」在本批实现：</p>
 * <ol>
 *   <li>事务内锁定主档，授权校验（{@code application:edit}）；</li>
 *   <li>仅 {@code DRAFT}/{@code SUPPLEMENT} 可保存，其他状态 {@code RPM-APPLY-004}；</li>
 *   <li>{@code expected_version} 与主档乐观锁版本不一致返回 {@code RPM-APPLY-002}；</li>
 *   <li>命中原草稿版本则原地更新内容，否则新建草稿行（{@code version_no = max+1}）；</li>
 *   <li>主档标题（可选）与乐观锁版本递增，写非关键审计 {@code APPLICATION_DRAFT_SAVED}。</li>
 * </ol>
 */
@Service
public class ApplicationDraftAppService {

    private static final int TITLE_MAX_LENGTH = 512;

    private final ApplicationRepository applicationRepository;

    private final ApplicationVersionRepository applicationVersionRepository;

    private final ApplicationContentHasher contentHasher;

    private final AuthorizationService authorizationService;

    private final AuditRecorder auditRecorder;

    private final Clock clock;

    /**
     * 构造草稿保存服务。
     *
     * @param applicationRepository        申报主档仓储
     * @param applicationVersionRepository 申报版本仓储
     * @param contentHasher                内容规范化与摘要
     * @param authorizationService         统一授权服务
     * @param auditRecorder                审计记录器
     * @param clock                        时间基准
     */
    public ApplicationDraftAppService(ApplicationRepository applicationRepository,
                                      ApplicationVersionRepository applicationVersionRepository,
                                      ApplicationContentHasher contentHasher,
                                      AuthorizationService authorizationService,
                                      AuditRecorder auditRecorder,
                                      Clock clock) {
        this.applicationRepository = Objects.requireNonNull(applicationRepository,
                "applicationRepository 不能为空");
        this.applicationVersionRepository = Objects.requireNonNull(applicationVersionRepository,
                "applicationVersionRepository 不能为空");
        this.contentHasher = Objects.requireNonNull(contentHasher, "contentHasher 不能为空");
        this.authorizationService = Objects.requireNonNull(authorizationService,
                "authorizationService 不能为空");
        this.auditRecorder = Objects.requireNonNull(auditRecorder, "auditRecorder 不能为空");
        this.clock = Objects.requireNonNull(clock, "clock 不能为空");
    }

    /**
     * 保存申报草稿。
     *
     * <p>失败语义：参数非法 {@code RPM-COMMON-400}；无权限 {@code RPM-COMMON-403}；
     * 不存在 {@code RPM-COMMON-404}；状态不允许 {@code RPM-APPLY-004}；
     * 版本冲突 {@code RPM-APPLY-002}；标题非法 {@code RPM-APPLY-001}。
     * 版本冲突时草稿内容保持不变（TC-APP-003）。</p>
     *
     * @param applicationIdText 申报 ID
     * @param cmd               保存命令
     * @param auth              当前请求授权上下文
     * @return 保存后的状态、主档版本号与草稿版本信息
     */
    @Transactional
    public ApplicationDraftSaved save(String applicationIdText, SaveDraftCmd cmd, AuthContext auth) {
        Objects.requireNonNull(cmd, "cmd 不能为空");
        Objects.requireNonNull(auth, "auth 不能为空");
        if (cmd.formData() == null) {
            throw new BizException(ErrorCode.APPLY_001, "表单数据不能为空");
        }
        String applicationId = UuidUtils.formatCompact(parseUuid(applicationIdText, "applicationId"));

        ApplicationEntity application = applicationRepository.selectForUpdate(applicationId)
                .orElseThrow(() -> new BizException(ErrorCode.COMMON_404, "申报不存在"));

        if (!authorizationService.check(auth, Action.of(ApplicationPermissions.EDIT),
                new ResourceRef(ResourceTypes.PROJECT, UuidUtils.parse(applicationId),
                        parseOptionalUuid(application.getApplicantId()),
                        parseOptionalUuid(application.getDeptId()), Set.of())).allowed()) {
            throw new BizException(ErrorCode.COMMON_403);
        }
        if (!ApplicationEntity.STATUS_DRAFT.equals(application.getStatus())
                && !ApplicationEntity.STATUS_SUPPLEMENT.equals(application.getStatus())) {
            throw new BizException(ErrorCode.APPLY_004, "当前状态不允许保存草稿");
        }
        if (!Objects.equals(application.getVersion(), cmd.expectedVersion())) {
            throw new BizException(ErrorCode.APPLY_002, "申报版本已变化，请刷新后重试");
        }
        String title = cmd.title() == null ? null : normalizeTitle(cmd.title());

        String canonicalJson = contentHasher.canonicalJson(cmd.formData());
        String contentHash = contentHasher.sha256(canonicalJson);
        ApplicationVersionEntity version = persistDraft(applicationId, canonicalJson, contentHash);

        int nextVersion = application.getVersion() + 1;
        if (title != null) {
            application.setTitle(title);
        }
        if (applicationRepository.update(application) == 0) {
            throw new BizException(ErrorCode.APPLY_002, "并发保存冲突，请刷新后重试");
        }

        auditRecorder.recordNonCritical(new AuditEvent(
                TraceIds.currentOrUntracked(),
                auth.userId(),
                null,
                "APPLICATION_DRAFT_SAVED",
                "APPLICATION",
                UuidUtils.parse(applicationId),
                nextVersion,
                null,
                contentHash,
                "保存申报草稿",
                null,
                null,
                clock.instant()));

        return new ApplicationDraftSaved(applicationId, application.getStatus(), nextVersion,
                version.getId(), version.getVersionNo());
    }

    private ApplicationVersionEntity persistDraft(String applicationId, String canonicalJson,
                                                  String contentHash) {
        Optional<ApplicationVersionEntity> existing =
                applicationVersionRepository.findDraftByApplicationId(applicationId);
        if (existing.isPresent()) {
            ApplicationVersionEntity version = existing.get();
            version.applyDraftContent(canonicalJson, contentHash);
            if (applicationVersionRepository.update(version) == 0) {
                throw new BizException(ErrorCode.APPLY_002, "草稿已被并发修改，请刷新后重试");
            }
            return version;
        }
        int nextVersionNo = applicationVersionRepository.findMaxVersionNo(applicationId) + 1;
        ApplicationVersionEntity version = ApplicationVersionEntity.newDraft(
                applicationId, nextVersionNo, canonicalJson, contentHash);
        applicationVersionRepository.insert(version);
        return version;
    }

    private static String normalizeTitle(String title) {
        String normalized = title.trim();
        if (normalized.isEmpty()) {
            throw new BizException(ErrorCode.APPLY_001, "申报标题不能为空");
        }
        if (normalized.length() > TITLE_MAX_LENGTH) {
            throw new BizException(ErrorCode.APPLY_001, "申报标题长度不能超过 512 字符");
        }
        return normalized;
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
