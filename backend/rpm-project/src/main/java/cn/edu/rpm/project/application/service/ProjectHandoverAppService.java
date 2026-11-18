package cn.edu.rpm.project.application.service;

import cn.edu.rpm.audit.domain.model.AuditEvent;
import cn.edu.rpm.audit.domain.service.AuditRecorder;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.common.util.CanonicalJsonHasher;
import cn.edu.rpm.common.util.TraceIds;
import cn.edu.rpm.common.util.UuidUtils;
import cn.edu.rpm.iam.application.dto.Action;
import cn.edu.rpm.iam.application.dto.AuthContext;
import cn.edu.rpm.iam.application.dto.AuthDecision;
import cn.edu.rpm.iam.application.dto.ResourceRef;
import cn.edu.rpm.iam.domain.model.ResourceTypes;
import cn.edu.rpm.iam.domain.service.AuthorizationService;
import cn.edu.rpm.iam.infrastructure.entity.IamUserEntity;
import cn.edu.rpm.iam.infrastructure.repository.IamUserRepository;
import cn.edu.rpm.project.application.dto.InitiateHandoverCmd;
import cn.edu.rpm.project.application.dto.ProjectHandoverInitiated;
import cn.edu.rpm.project.domain.model.ProjectApprovalTemplateCodes;
import cn.edu.rpm.project.domain.model.ProjectPermissions;
import cn.edu.rpm.project.domain.port.ProjectApprovalWorkflowLauncher;
import cn.edu.rpm.project.domain.port.ProjectApprovalWorkflowStart;
import cn.edu.rpm.project.domain.port.ProjectHandoverChecklistPort;
import cn.edu.rpm.project.infrastructure.entity.ProjectEntity;
import cn.edu.rpm.project.infrastructure.entity.ProjectHandoverEntity;
import cn.edu.rpm.project.infrastructure.entity.ProjectMemberEntity;
import cn.edu.rpm.project.infrastructure.repository.ProjectHandoverRepository;
import cn.edu.rpm.project.infrastructure.repository.ProjectMemberRepository;
import cn.edu.rpm.project.infrastructure.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 负责人交接应用服务（T-PRJ-020；技术文档 04 §3.10、07 §3.4）。
 *
 * <p>发起交接前必须：新负责人存在且状态 {@code ACTIVE}；{@code effectiveAt}
 * 不早于当前；待办与风险全部列入 {@code pendingItems}（由各来源的
 * {@link ProjectHandoverChecklistPort} 提供未结事项，遗漏任意一条返回
 * {@code RPM-PROJ-006}）。交接单创建为 {@code IN_REVIEW} 并启动审批，
 * 审批通过前不得修改 {@code project.pi_id}。</p>
 */
@Service
public class ProjectHandoverAppService {

    /** 审计动作：发起负责人交接。 */
    public static final String AUDIT_HANDOVER_INITIATED = "PROJECT_HANDOVER_INITIATED";

    /** 审计对象类型：负责人交接单。 */
    static final String OBJECT_HANDOVER = "PROJECT_HANDOVER";

    /** 事项类型：轻任务。 */
    public static final String ITEM_TYPE_TASK = "TASK";

    /** 事项类型：风险。 */
    public static final String ITEM_TYPE_RISK = "RISK";

    private static final Set<String> ITEM_TYPES = Set.of(ITEM_TYPE_TASK, ITEM_TYPE_RISK);

    private static final Set<String> ITEM_ACTIONS = Set.of("TRANSFER", "CLOSE", "CANCEL");

    private static final int MAX_REASON_LENGTH = 2048;

    private final ProjectRepository projectRepository;

    private final ProjectMemberRepository projectMemberRepository;

    private final ProjectHandoverRepository handoverRepository;

    private final IamUserRepository iamUserRepository;

    private final List<ProjectHandoverChecklistPort> checklistPorts;

    private final ProjectApprovalWorkflowLauncher workflowLauncher;

    private final AuthorizationService authorizationService;

    private final AuditRecorder auditRecorder;

    private final CanonicalJsonHasher jsonHasher;

    private final Clock clock;

    /**
     * 构造交接应用服务。
     *
     * @param projectRepository      项目仓储
     * @param projectMemberRepository 成员仓储（授权装载）
     * @param handoverRepository     交接单仓储
     * @param iamUserRepository      人员仓储（新负责人有效性）
     * @param checklistPorts         未结事项来源列表（风险/待办等）
     * @param workflowLauncher       交接审批启动端口
     * @param authorizationService   统一授权服务
     * @param auditRecorder          审计记录器
     * @param jsonHasher             规范化 JSON 工具
     * @param clock                  时间基准
     */
    public ProjectHandoverAppService(ProjectRepository projectRepository,
                                     ProjectMemberRepository projectMemberRepository,
                                     ProjectHandoverRepository handoverRepository,
                                     IamUserRepository iamUserRepository,
                                     List<ProjectHandoverChecklistPort> checklistPorts,
                                     ProjectApprovalWorkflowLauncher workflowLauncher,
                                     AuthorizationService authorizationService,
                                     AuditRecorder auditRecorder,
                                     CanonicalJsonHasher jsonHasher,
                                     Clock clock) {
        this.projectRepository = Objects.requireNonNull(projectRepository, "projectRepository 不能为空");
        this.projectMemberRepository = Objects.requireNonNull(projectMemberRepository,
                "projectMemberRepository 不能为空");
        this.handoverRepository = Objects.requireNonNull(handoverRepository,
                "handoverRepository 不能为空");
        this.iamUserRepository = Objects.requireNonNull(iamUserRepository,
                "iamUserRepository 不能为空");
        this.checklistPorts = checklistPorts == null ? List.of() : List.copyOf(checklistPorts);
        this.workflowLauncher = Objects.requireNonNull(workflowLauncher, "workflowLauncher 不能为空");
        this.authorizationService = Objects.requireNonNull(authorizationService,
                "authorizationService 不能为空");
        this.auditRecorder = Objects.requireNonNull(auditRecorder, "auditRecorder 不能为空");
        this.jsonHasher = Objects.requireNonNull(jsonHasher, "jsonHasher 不能为空");
        this.clock = Objects.requireNonNull(clock, "clock 不能为空");
    }

    /**
     * 发起负责人交接（TC-PRJ-025 / TC-PRJ-028）。
     *
     * @param projectId 项目 ID
     * @param cmd       交接命令
     * @param auth      当前请求授权上下文
     * @return 交接单、状态、生效时间与审批实例 ID
     * @throws BizException 项目不存在 {@code RPM-PROJ-001}；无权限 {@code RPM-COMMON-403}；
     *                      项目状态不允许 {@code RPM-PROJ-002}；参数非法 {@code RPM-COMMON-400}；
     *                      新负责人无效或清单遗漏 {@code RPM-PROJ-006}
     */
    @Transactional
    public ProjectHandoverInitiated initiate(String projectId, InitiateHandoverCmd cmd,
                                             AuthContext auth) {
        Objects.requireNonNull(cmd, "cmd 不能为空");
        Objects.requireNonNull(auth, "auth 不能为空");

        ProjectEntity project = projectRepository.selectForUpdate(projectId)
                .orElseThrow(() -> new BizException(ErrorCode.PROJ_001));
        authorize(project, auth);
        if (!ProjectEntity.STATUS_EXECUTING.equals(project.getStatus())) {
            throw new BizException(ErrorCode.PROJ_002, "仅执行中的项目可办理负责人交接");
        }

        String toUserId = compact(requireText(cmd.toUserId(), "toUserId 不能为空"),
                "toUserId");
        if (UuidUtils.sameId(project.getPiId(), toUserId)) {
            throw new BizException(ErrorCode.PROJ_006, "新负责人不能与现任负责人相同");
        }
        IamUserEntity toUser = iamUserRepository.findById(toUserId)
                .orElseThrow(() -> new BizException(ErrorCode.PROJ_006, "新负责人不存在"));
        if (!"ACTIVE".equals(toUser.getStatus())) {
            throw new BizException(ErrorCode.PROJ_006, "新负责人已离岗或禁用，不能接手项目");
        }

        OffsetDateTime effectiveAt = cmd.effectiveAt();
        if (effectiveAt == null) {
            throw new BizException(ErrorCode.COMMON_400, "effectiveAt 不能为空");
        }
        if (effectiveAt.isBefore(OffsetDateTime.now(clock))) {
            throw new BizException(ErrorCode.COMMON_400, "effectiveAt 不能早于当前时间");
        }
        String reason = requireText(cmd.reason(), "reason 不能为空");
        if (reason.length() > MAX_REASON_LENGTH) {
            throw new BizException(ErrorCode.COMMON_400, "reason 长度不能超过 2048");
        }

        List<Map<String, Object>> pendingItems = normalizePendingItems(cmd.pendingItems());
        requireChecklistComplete(projectId, new HashSet<>(pendingItems.stream()
                .map(item -> item.get("type") + ":" + item.get("id"))
                .toList()));

        String handoverId = UuidUtils.formatCompact(UUID.randomUUID());
        ProjectHandoverEntity handover = new ProjectHandoverEntity();
        handover.setId(handoverId);
        handover.setProjectId(projectId);
        handover.setFromUserId(project.getPiId());
        handover.setToUserId(toUserId);
        handover.setEffectiveAt(effectiveAt);
        handover.setStatus(ProjectHandoverEntity.STATUS_DRAFT);
        handover.setPendingItems(jsonHasher.canonicalJson(pendingItems));
        handover.setReason(reason);
        handover.submitForReview();
        handoverRepository.insert(handover);

        OffsetDateTime now = OffsetDateTime.now(clock);
        String instanceId = workflowLauncher.start(new ProjectApprovalWorkflowStart(
                "HANDOVER",
                UuidUtils.parse(handoverId),
                UuidUtils.parse(handoverId),
                ProjectApprovalTemplateCodes.HANDOVER,
                UuidUtils.parse(projectId),
                project.getProjectType(),
                auth.userId(),
                Map.of("toUserId", UuidUtils.format(UuidUtils.parse(toUserId))),
                now));

        auditRecorder.record(new AuditEvent(
                TraceIds.currentOrUntracked(),
                auth.userId(),
                null,
                AUDIT_HANDOVER_INITIATED,
                OBJECT_HANDOVER,
                UuidUtils.parse(handoverId),
                null,
                null,
                null,
                "发起负责人交接：" + project.getPiId() + " → " + toUserId,
                null,
                null,
                clock.instant()));

        return new ProjectHandoverInitiated(handoverId, handover.getStatus(), effectiveAt,
                instanceId);
    }

    private List<Map<String, Object>> normalizePendingItems(
            List<InitiateHandoverCmd.PendingItem> items) {
        List<Map<String, Object>> normalized = new ArrayList<>(items.size());
        for (InitiateHandoverCmd.PendingItem item : items) {
            String type = requireText(item.type(), "pendingItems.type 不能为空")
                    .toUpperCase(Locale.ROOT);
            if (!ITEM_TYPES.contains(type)) {
                throw new BizException(ErrorCode.COMMON_400,
                        "pendingItems.type 仅支持 TASK/RISK");
            }
            String id = compact(item.id(), "pendingItems.id");
            String action = item.action() == null || item.action().isBlank()
                    ? "TRANSFER" : item.action().trim().toUpperCase(Locale.ROOT);
            if (!ITEM_ACTIONS.contains(action)) {
                throw new BizException(ErrorCode.COMMON_400,
                        "pendingItems.action 仅支持 TRANSFER/CLOSE/CANCEL");
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("type", type);
            entry.put("id", id);
            entry.put("action", action);
            normalized.add(entry);
        }
        return normalized;
    }

    private void requireChecklistComplete(String projectId, Set<String> listed) {
        List<String> missing = checklistPorts.stream()
                .flatMap(port -> port.findOpenItems(projectId).stream())
                .map(item -> item.type().toUpperCase(Locale.ROOT) + ":" + item.id())
                .filter(key -> !listed.contains(key))
                .distinct()
                .sorted()
                .toList();
        if (!missing.isEmpty()) {
            throw new BizException(ErrorCode.PROJ_006,
                    "交接清单遗漏未结事项：" + String.join(",", missing));
        }
    }

    private void authorize(ProjectEntity project, AuthContext auth) {
        ResourceRef resource = new ResourceRef(
                ResourceTypes.PROJECT,
                UuidUtils.parse(project.getId()),
                UuidUtils.parse(project.getPiId()),
                UuidUtils.parse(project.getDeptId()),
                activeMemberIds(project.getId()));
        AuthDecision decision = authorizationService.check(auth,
                Action.of(ProjectPermissions.HANDOVER), resource);
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

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BizException(ErrorCode.COMMON_400, message);
        }
        return value.trim();
    }

    private static String compact(String value, String field) {
        try {
            return UuidUtils.formatCompact(UuidUtils.parse(value));
        } catch (IllegalArgumentException ex) {
            throw new BizException(ErrorCode.COMMON_400, field + " 非法");
        }
    }
}
