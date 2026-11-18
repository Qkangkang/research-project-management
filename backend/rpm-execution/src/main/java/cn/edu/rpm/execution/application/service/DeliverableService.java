package cn.edu.rpm.execution.application.service;

import cn.edu.rpm.audit.domain.model.AuditEvent;
import cn.edu.rpm.audit.domain.service.AuditRecorder;
import cn.edu.rpm.common.api.PageVO;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.common.util.TraceIds;
import cn.edu.rpm.common.util.UuidUtils;
import cn.edu.rpm.execution.application.dto.DeliverableCreated;
import cn.edu.rpm.execution.application.dto.DeliverableView;
import cn.edu.rpm.execution.application.dto.UploadDeliverableCmd;
import cn.edu.rpm.execution.domain.model.ExecutionPermissions;
import cn.edu.rpm.execution.infrastructure.entity.DeliverableEntity;
import cn.edu.rpm.execution.infrastructure.repository.DeliverableRepository;
import cn.edu.rpm.file.domain.model.FileObject;
import cn.edu.rpm.file.domain.port.FileObjectQueryPort;
import cn.edu.rpm.iam.application.dto.Action;
import cn.edu.rpm.iam.application.dto.AuthContext;
import cn.edu.rpm.iam.application.dto.AuthDecision;
import cn.edu.rpm.iam.application.dto.ResourceRef;
import cn.edu.rpm.iam.domain.model.ResourceTypes;
import cn.edu.rpm.iam.domain.service.AuthorizationService;
import cn.edu.rpm.project.infrastructure.entity.ProjectEntity;
import cn.edu.rpm.project.infrastructure.entity.ProjectMemberEntity;
import cn.edu.rpm.project.infrastructure.repository.ProjectMemberRepository;
import cn.edu.rpm.project.infrastructure.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 交付物服务（T-EXEC-008；技术文档 04 §2.3、07 §3.2）。
 *
 * <p>上传前校验文件对象已 {@code AVAILABLE}（病毒扫描钩子通过）且登记的
 * SHA-256 与提交声明一致；不一致/未就绪统一返回 {@code RPM-EXEC-004}，且不生成
 * 交付物引用（TC-EXEC-014）。列表按项目查询，{@code milestoneId} 非空时在项目范围内
 * 过滤（TC-EXEC-015）。</p>
 *
 * <p>Controller 只做 HTTP 形态校验；本服务入口完成数据范围授权。文件本体由
 * rpm-file 管理，本服务只保存 {@code file_id} 与摘要。</p>
 */
@Service
public class DeliverableService {

    /** 审计动作：上传交付物。 */
    public static final String AUDIT_DELIVERABLE_UPLOADED = "DELIVERABLE_UPLOADED";

    /** 审计对象类型：交付物。 */
    static final String OBJECT_DELIVERABLE = "DELIVERABLE";

    /** 标题最大长度（DDL varchar(512)）。 */
    static final int MAX_TITLE_LENGTH = 512;

    /** 默认分页大小。 */
    static final int DEFAULT_PAGE_SIZE = 20;

    /** 分页大小上限（PageVO 约定）。 */
    static final int MAX_PAGE_SIZE = 200;

    private static final Pattern SHA256_HEX = Pattern.compile("^[0-9a-fA-F]{64}$");

    private final DeliverableRepository deliverableRepository;

    private final ProjectRepository projectRepository;

    private final ProjectMemberRepository projectMemberRepository;

    private final FileObjectQueryPort fileObjectQueryPort;

    private final AuthorizationService authorizationService;

    private final AuditRecorder auditRecorder;

    private final Clock clock;

    /**
     * 构造交付物服务。
     *
     * @param deliverableRepository   交付物仓储
     * @param projectRepository       项目仓储
     * @param projectMemberRepository 成员仓储（授权装载）
     * @param fileObjectQueryPort     文件对象查询端口
     * @param authorizationService    统一授权服务
     * @param auditRecorder           审计记录器
     * @param clock                   时间基准
     */
    public DeliverableService(DeliverableRepository deliverableRepository,
                              ProjectRepository projectRepository,
                              ProjectMemberRepository projectMemberRepository,
                              FileObjectQueryPort fileObjectQueryPort,
                              AuthorizationService authorizationService,
                              AuditRecorder auditRecorder,
                              Clock clock) {
        this.deliverableRepository = Objects.requireNonNull(deliverableRepository,
                "deliverableRepository 不能为空");
        this.projectRepository = Objects.requireNonNull(projectRepository, "projectRepository 不能为空");
        this.projectMemberRepository = Objects.requireNonNull(projectMemberRepository,
                "projectMemberRepository 不能为空");
        this.fileObjectQueryPort = Objects.requireNonNull(fileObjectQueryPort,
                "fileObjectQueryPort 不能为空");
        this.authorizationService = Objects.requireNonNull(authorizationService,
                "authorizationService 不能为空");
        this.auditRecorder = Objects.requireNonNull(auditRecorder, "auditRecorder 不能为空");
        this.clock = Objects.requireNonNull(clock, "clock 不能为空");
    }

    /**
     * 上传交付物（TC-EXEC-013 / TC-EXEC-014）。
     *
     * @param projectId 项目 ID
     * @param cmd       上传命令
     * @param auth      当前请求授权上下文
     * @return 交付物 ID、文件 ID、摘要、状态与版本
     * @throws BizException 项目不存在 {@code RPM-PROJ-001}；无权限 {@code RPM-COMMON-403}；
     *                      参数缺失/非法 {@code RPM-COMMON-400}；
     *                      文件未就绪或摘要不一致 {@code RPM-EXEC-004}
     */
    @Transactional
    public DeliverableCreated upload(String projectId, UploadDeliverableCmd cmd, AuthContext auth) {
        Objects.requireNonNull(cmd, "cmd 不能为空");
        Objects.requireNonNull(auth, "auth 不能为空");
        ProjectEntity project = loadProject(projectId);
        authorize(project, auth, ExecutionPermissions.DELIVERABLE_UPLOAD);

        String title = requireTitle(cmd.title());
        String sha256 = requireSha256(cmd.sha256());
        UUID fileUuid = requireUuid(cmd.fileId(), "fileId");

        FileObject file = fileObjectQueryPort.findById(fileUuid)
                .orElseThrow(() -> new BizException(ErrorCode.EXEC_004, "交付物文件不存在"));
        if (!file.isAvailable()) {
            throw new BizException(ErrorCode.EXEC_004, "交付物文件未就绪，禁止业务引用");
        }
        if (file.getSha256() == null || !file.getSha256().equalsIgnoreCase(sha256)) {
            throw new BizException(ErrorCode.EXEC_004, "交付物文件哈希与声明不一致");
        }

        DeliverableEntity deliverable = new DeliverableEntity();
        deliverable.setId(UuidUtils.formatCompact(UUID.randomUUID()));
        deliverable.setProjectId(project.getId());
        deliverable.setMilestoneId(trimToNull(cmd.milestoneId()));
        deliverable.setReportVersionId(trimToNull(cmd.reportVersionId()));
        deliverable.setFileId(UuidUtils.formatCompact(fileUuid));
        deliverable.setTitle(title);
        deliverable.setSha256(sha256.toLowerCase(Locale.ROOT));
        deliverable.setStatus(DeliverableEntity.STATUS_ACTIVE);
        deliverable.setUploadedBy(UuidUtils.formatCompact(auth.userId()));
        deliverableRepository.insert(deliverable);

        auditRecorder.record(new AuditEvent(
                TraceIds.currentOrUntracked(),
                auth.userId(),
                null,
                AUDIT_DELIVERABLE_UPLOADED,
                OBJECT_DELIVERABLE,
                UuidUtils.parse(deliverable.getId()),
                deliverable.getVersion(),
                null,
                deliverable.getSha256(),
                "上传交付物：" + title,
                null,
                null,
                clock.instant()));

        return new DeliverableCreated(deliverable.getId(), deliverable.getFileId(),
                deliverable.getSha256(), deliverable.getStatus(), deliverable.getVersion());
    }

    /**
     * 查询项目交付物列表（TC-EXEC-015）。
     *
     * @param projectId   项目 ID
     * @param milestoneId 里程碑 ID（可空；非空时过滤）
     * @param page        页码（从 1 开始；非法值按 1）
     * @param size        每页条数（默认 20，上限 200）
     * @param auth        当前请求授权上下文
     * @return 统一分页载体；无数据时 {@code items=[]} 且 {@code total=0}
     * @throws BizException 项目不存在 {@code RPM-PROJ-001}；无权限 {@code RPM-COMMON-403}
     */
    @Transactional(readOnly = true)
    public PageVO<DeliverableView> list(String projectId, String milestoneId,
                                        int page, int size, AuthContext auth) {
        Objects.requireNonNull(auth, "auth 不能为空");
        ProjectEntity project = loadProject(projectId);
        authorize(project, auth, ExecutionPermissions.VIEW);

        String milestoneFilter = trimToNull(milestoneId);
        List<DeliverableView> views = deliverableRepository.findByProjectId(project.getId()).stream()
                .filter(entity -> milestoneFilter == null
                        || UuidUtils.sameId(entity.getMilestoneId(), milestoneFilter))
                .map(DeliverableService::toView)
                .toList();

        int safePage = Math.max(page, 1);
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        int from = (int) Math.min((long) (safePage - 1) * safeSize, views.size());
        int to = Math.min(from + safeSize, views.size());
        return PageVO.of(views.subList(from, to), safePage, safeSize, views.size());
    }

    private ProjectEntity loadProject(String projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new BizException(ErrorCode.PROJ_001));
    }

    private void authorize(ProjectEntity project, AuthContext auth, String permission) {
        ResourceRef resource = new ResourceRef(
                ResourceTypes.PROJECT,
                UuidUtils.parse(project.getId()),
                UuidUtils.parse(project.getPiId()),
                UuidUtils.parse(project.getDeptId()),
                activeMemberIds(project.getId()));
        AuthDecision decision = authorizationService.check(auth, Action.of(permission), resource);
        if (!decision.allowed()) {
            throw new BizException(ErrorCode.COMMON_403);
        }
    }

    private Set<UUID> activeMemberIds(String projectId) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        return projectMemberRepository.findActiveByProjectId(projectId, now).stream()
                .map(ProjectMemberEntity::getUserId)
                .map(UuidUtils::parse)
                .collect(Collectors.toSet());
    }

    private static DeliverableView toView(DeliverableEntity entity) {
        return new DeliverableView(entity.getId(), entity.getProjectId(),
                entity.getMilestoneId(), entity.getFileId(), entity.getTitle(),
                entity.getSha256(), entity.getStatus(), entity.getUploadedBy(),
                entity.getCreatedAt());
    }

    private static String requireTitle(String value) {
        if (value == null || value.isBlank()) {
            throw new BizException(ErrorCode.COMMON_400, "交付物名称不能为空");
        }
        String title = value.trim();
        if (title.length() > MAX_TITLE_LENGTH) {
            throw new BizException(ErrorCode.COMMON_400,
                    "交付物名称不能超过 " + MAX_TITLE_LENGTH + " 字符");
        }
        return title;
    }

    private static String requireSha256(String value) {
        if (value == null || !SHA256_HEX.matcher(value.trim()).matches()) {
            throw new BizException(ErrorCode.COMMON_400, "sha256 必须为 64 位十六进制");
        }
        return value.trim();
    }

    private static UUID requireUuid(String value, String field) {
        try {
            return UuidUtils.parse(value);
        } catch (IllegalArgumentException ex) {
            throw new BizException(ErrorCode.EXEC_004, field + " 非法");
        }
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
