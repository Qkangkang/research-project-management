package cn.edu.rpm.application.application.service;

import cn.edu.rpm.application.application.dto.ApplicationCreated;
import cn.edu.rpm.application.application.dto.CreateApplicationCmd;
import cn.edu.rpm.application.domain.model.ApplicationPermissions;
import cn.edu.rpm.application.infrastructure.entity.ApplicationEntity;
import cn.edu.rpm.application.infrastructure.entity.ApplyBatchEntity;
import cn.edu.rpm.application.infrastructure.repository.ApplicationRepository;
import cn.edu.rpm.application.infrastructure.repository.ApplyBatchRepository;
import cn.edu.rpm.application.infrastructure.repository.ApplyFormTemplateRepository;
import cn.edu.rpm.audit.domain.model.AuditEvent;
import cn.edu.rpm.audit.domain.service.AuditRecorder;
import cn.edu.rpm.common.domain.service.NumberGenerator;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.common.util.TraceIds;
import cn.edu.rpm.common.util.UuidUtils;
import cn.edu.rpm.iam.application.dto.Action;
import cn.edu.rpm.iam.application.dto.AuthContext;
import cn.edu.rpm.iam.application.dto.AuthDecision;
import cn.edu.rpm.iam.application.dto.ResourceRef;
import cn.edu.rpm.iam.domain.model.ResourceTypes;
import cn.edu.rpm.iam.domain.service.AuthorizationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 申报创建应用服务（T-APP-011；技术文档 04 §3.1）。
 *
 * <p>创建链路的业务规则：批次存在且 {@code OPEN}、项目类型存在当前有效表单模板、
 * 当前用户对申报部门有数据范围权限；通过后以编号规则生成 {@code application_no}
 * 并插入 {@code DRAFT} 草稿，写关键审计 {@code APPLICATION_CREATED}。</p>
 *
 * <p>授权口径：本方法在应用服务入口调用 {@link AuthorizationService#check}，
 * 不只依赖 Controller 的 {@code @RequirePermission}。申报尚无项目上下文，
 * 以批次 ID 作为数据范围资源引用、申请部门作为部门维度（技术文档 03 §2.1）。</p>
 */
@Service
public class ApplicationCreateAppService {

    private static final Logger LOG = LoggerFactory.getLogger(ApplicationCreateAppService.class);

    private static final int TITLE_MAX_LENGTH = 512;

    private final ApplyBatchRepository batchRepository;

    private final ApplyFormTemplateRepository templateRepository;

    private final ApplicationRepository applicationRepository;

    private final NumberGenerator numberGenerator;

    private final AuthorizationService authorizationService;

    private final AuditRecorder auditRecorder;

    private final Clock clock;

    /**
     * 构造创建服务。
     *
     * @param batchRepository      申报批次仓储
     * @param templateRepository   表单模板仓储
     * @param applicationRepository 申报主档仓储
     * @param numberGenerator       编号生成器
     * @param authorizationService  统一授权服务
     * @param auditRecorder         审计记录器
     * @param clock                 时间基准
     */
    public ApplicationCreateAppService(ApplyBatchRepository batchRepository,
                                       ApplyFormTemplateRepository templateRepository,
                                       ApplicationRepository applicationRepository,
                                       NumberGenerator numberGenerator,
                                       AuthorizationService authorizationService,
                                       AuditRecorder auditRecorder,
                                       Clock clock) {
        this.batchRepository = Objects.requireNonNull(batchRepository, "batchRepository 不能为空");
        this.templateRepository = Objects.requireNonNull(templateRepository, "templateRepository 不能为空");
        this.applicationRepository = Objects.requireNonNull(applicationRepository,
                "applicationRepository 不能为空");
        this.numberGenerator = Objects.requireNonNull(numberGenerator, "numberGenerator 不能为空");
        this.authorizationService = Objects.requireNonNull(authorizationService,
                "authorizationService 不能为空");
        this.auditRecorder = Objects.requireNonNull(auditRecorder, "auditRecorder 不能为空");
        this.clock = Objects.requireNonNull(clock, "clock 不能为空");
    }

    /**
     * 创建申报草稿。
     *
     * <p>触发场景：申请人选择开放批次与项目类型后新建申报。失败语义：
     * 批次不存在 {@code RPM-COMMON-404}；批次非 {@code OPEN} {@code RPM-APPLY-003}；
     * 项目类型无有效模板或标题非法 {@code RPM-APPLY-001}；无权限 {@code RPM-COMMON-403}。
     * 编号冲突重试耗尽由 {@code RPM-COMMON-409} 提示重新发起。</p>
     *
     * @param cmd  创建命令
     * @param auth 当前请求授权上下文
     * @return 草稿 ID、编号、状态与乐观锁版本号
     */
    @Transactional
    public ApplicationCreated create(CreateApplicationCmd cmd, AuthContext auth) {
        Objects.requireNonNull(cmd, "cmd 不能为空");
        Objects.requireNonNull(auth, "auth 不能为空");

        String title = normalizeTitle(cmd.title());
        UUID batchId = parseUuid(cmd.batchId(), "batch_id");
        UUID deptId = parseUuid(cmd.deptId(), "dept_id");

        ApplyBatchEntity batch = batchRepository.findById(batchId.toString().replace("-", ""))
                .orElseThrow(() -> new BizException(ErrorCode.COMMON_404, "申报批次不存在"));
        if (!ApplyBatchEntity.STATUS_OPEN.equals(batch.getStatus())) {
            throw new BizException(ErrorCode.APPLY_003, "申报批次已关闭");
        }
        templateRepository.findEffectiveByProjectType(cmd.projectType())
                .orElseThrow(() -> new BizException(ErrorCode.APPLY_001,
                        "项目类型无有效表单模板: " + cmd.projectType()));

        AuthDecision decision = authorizationService.check(auth,
                Action.of(ApplicationPermissions.CREATE),
                new ResourceRef(ResourceTypes.PROJECT, batchId, auth.userId(), deptId, Set.of()));
        if (!decision.allowed()) {
            throw new BizException(ErrorCode.COMMON_403);
        }

        String applicationNo = numberGenerator.next(NumberGenerator.RULE_APPLICATION_NO);
        String actorId = UuidUtils.formatCompact(auth.userId());

        ApplicationEntity application = new ApplicationEntity();
        application.setId(UuidUtils.formatCompact(UUID.randomUUID()));
        application.setApplicationNo(applicationNo);
        application.setBatchId(UuidUtils.formatCompact(batchId));
        application.setProjectType(cmd.projectType());
        application.setApplicantId(actorId);
        application.setDeptId(UuidUtils.formatCompact(deptId));
        application.setTitle(title);
        application.setSourceType(ApplicationEntity.SOURCE_INTERNAL);
        application.setStatus(ApplicationEntity.STATUS_DRAFT);
        application.setVersion(0);
        application.setCreatedBy(actorId);
        application.setUpdatedBy(actorId);

        try {
            applicationRepository.insert(application);
        } catch (DuplicateKeyException ex) {
            LOG.warn("申报编号冲突 applicationNo={} traceId={}", applicationNo,
                    TraceIds.currentOrUntracked());
            throw new BizException(ErrorCode.COMMON_409, "申报编号生成冲突，请重试");
        }

        auditRecorder.record(new AuditEvent(
                TraceIds.currentOrUntracked(),
                auth.userId(),
                null,
                "APPLICATION_CREATED",
                "APPLICATION",
                UuidUtils.parse(application.getId()),
                0,
                null,
                null,
                "创建申报草稿",
                null,
                null,
                clock.instant()));

        return new ApplicationCreated(application.getId(), applicationNo,
                ApplicationEntity.STATUS_DRAFT, 0);
    }

    private static String normalizeTitle(String title) {
        if (title == null) {
            throw new BizException(ErrorCode.APPLY_001, "申报标题不能为空");
        }
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

}
