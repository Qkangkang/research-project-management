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
import cn.edu.rpm.project.application.dto.ChangeRequestCreated;
import cn.edu.rpm.project.application.dto.CreateChangeRequestCmd;
import cn.edu.rpm.project.domain.model.ProjectApprovalTemplateCodes;
import cn.edu.rpm.project.domain.model.ProjectPermissions;
import cn.edu.rpm.project.domain.port.ProjectApprovalWorkflowLauncher;
import cn.edu.rpm.project.domain.port.ProjectApprovalWorkflowStart;
import cn.edu.rpm.project.infrastructure.entity.ChangeRequestEntity;
import cn.edu.rpm.project.infrastructure.entity.ChangeRequestVersionEntity;
import cn.edu.rpm.project.infrastructure.entity.ProjectEntity;
import cn.edu.rpm.project.infrastructure.entity.ProjectMemberEntity;
import cn.edu.rpm.project.infrastructure.repository.ChangeRequestRepository;
import cn.edu.rpm.project.infrastructure.repository.ChangeRequestVersionRepository;
import cn.edu.rpm.project.infrastructure.repository.ProjectMemberRepository;
import cn.edu.rpm.project.infrastructure.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
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
 * 项目变更发起应用服务（T-PRJ-019；技术文档 08 §8.2、§8.3、04 §3.9）。
 *
 * <p>请求体为受控 discriminated union：按 {@code changeType} 校验各类型必填字段、
 * 附件要求与允许项目状态，仅接受六类业务变更（{@code OBJECTIVE} 落库为 DDL
 * 允许的 {@code GOAL}）。校验通过后创建 {@code change_request} 与版本快照，
 * 状态置 {@code IN_REVIEW} 并启动审批；审批中项目继续沿用当前基线，禁止提前切换。</p>
 *
 * <p>错误约定：结构非法 {@code RPM-COMMON-400}（消息定位到 {@code payload.*} 字段）；
 * 成员区间重叠 {@code RPM-PROJ-005}；非 PI/科研管理 {@code RPM-COMMON-403}；
 * 项目状态不允许 {@code RPM-PROJ-002}；负责人变更目标无效 {@code RPM-PROJ-006}。</p>
 */
@Service
public class ChangeRequestAppService {

    /** 审计动作：发起项目变更。 */
    public static final String AUDIT_CHANGE_REQUESTED = "PROJECT_CHANGE_REQUESTED";

    /** 审计对象类型：项目变更单。 */
    static final String OBJECT_CHANGE = "PROJECT_CHANGE";

    private static final Set<String> MEMBER_ACTIONS = Set.of("ADD", "REMOVE");

    private static final Set<String> PARTNER_ACTIONS = Set.of("ADD", "REMOVE", "UPDATE");

    private static final int MAX_REASON_LENGTH = 2048;

    private final ProjectRepository projectRepository;

    private final ProjectMemberRepository projectMemberRepository;

    private final ChangeRequestRepository changeRequestRepository;

    private final ChangeRequestVersionRepository changeRequestVersionRepository;

    private final IamUserRepository iamUserRepository;

    private final ProjectApprovalWorkflowLauncher workflowLauncher;

    private final AuthorizationService authorizationService;

    private final AuditRecorder auditRecorder;

    private final CanonicalJsonHasher jsonHasher;

    private final Clock clock;

    /**
     * 构造变更应用服务。
     *
     * @param projectRepository             项目仓储
     * @param projectMemberRepository       成员仓储（授权与成员冲突判定）
     * @param changeRequestRepository       变更主档仓储
     * @param changeRequestVersionRepository 变更版本仓储
     * @param iamUserRepository             人员仓储（新负责人有效性）
     * @param workflowLauncher              变更审批启动端口
     * @param authorizationService          统一授权服务
     * @param auditRecorder                 审计记录器
     * @param jsonHasher                    规范化 JSON 工具
     * @param clock                         时间基准
     */
    public ChangeRequestAppService(ProjectRepository projectRepository,
                                   ProjectMemberRepository projectMemberRepository,
                                   ChangeRequestRepository changeRequestRepository,
                                   ChangeRequestVersionRepository changeRequestVersionRepository,
                                   IamUserRepository iamUserRepository,
                                   ProjectApprovalWorkflowLauncher workflowLauncher,
                                   AuthorizationService authorizationService,
                                   AuditRecorder auditRecorder,
                                   CanonicalJsonHasher jsonHasher,
                                   Clock clock) {
        this.projectRepository = Objects.requireNonNull(projectRepository, "projectRepository 不能为空");
        this.projectMemberRepository = Objects.requireNonNull(projectMemberRepository,
                "projectMemberRepository 不能为空");
        this.changeRequestRepository = Objects.requireNonNull(changeRequestRepository,
                "changeRequestRepository 不能为空");
        this.changeRequestVersionRepository = Objects.requireNonNull(
                changeRequestVersionRepository, "changeRequestVersionRepository 不能为空");
        this.iamUserRepository = Objects.requireNonNull(iamUserRepository,
                "iamUserRepository 不能为空");
        this.workflowLauncher = Objects.requireNonNull(workflowLauncher, "workflowLauncher 不能为空");
        this.authorizationService = Objects.requireNonNull(authorizationService,
                "authorizationService 不能为空");
        this.auditRecorder = Objects.requireNonNull(auditRecorder, "auditRecorder 不能为空");
        this.jsonHasher = Objects.requireNonNull(jsonHasher, "jsonHasher 不能为空");
        this.clock = Objects.requireNonNull(clock, "clock 不能为空");
    }

    /**
     * 发起项目变更（TC-PRJ-020 / TC-CHG-101 / TC-CHG-102 / TC-CHG-106）。
     *
     * @param projectId 项目 ID
     * @param cmd       变更命令
     * @param auth      当前请求授权上下文
     * @return 变更单、版本与状态
     * @throws BizException 项目不存在 {@code RPM-PROJ-001}；无权限 {@code RPM-COMMON-403}；
     *                      项目状态不允许 {@code RPM-PROJ-002}；结构非法 {@code RPM-COMMON-400}；
     *                      成员重叠 {@code RPM-PROJ-005}；负责人无效 {@code RPM-PROJ-006}
     */
    @Transactional
    public ChangeRequestCreated create(String projectId, CreateChangeRequestCmd cmd,
                                       AuthContext auth) {
        Objects.requireNonNull(cmd, "cmd 不能为空");
        Objects.requireNonNull(auth, "auth 不能为空");

        ProjectEntity project = projectRepository.selectForUpdate(projectId)
                .orElseThrow(() -> new BizException(ErrorCode.PROJ_001));
        authorize(project, auth);
        if (!ProjectEntity.STATUS_EXECUTING.equals(project.getStatus())) {
            throw new BizException(ErrorCode.PROJ_002, "仅执行中的项目可发起变更");
        }
        if (cmd.effectiveDate() == null) {
            throw new BizException(ErrorCode.COMMON_400, "effectiveDate 不能为空");
        }
        String reason = requireText(cmd.reason(), "reason 不能为空");
        if (reason.length() > MAX_REASON_LENGTH) {
            throw new BizException(ErrorCode.COMMON_400, "reason 长度不能超过 2048");
        }

        NormalizedChange normalized = validate(cmd, project);

        String changeId = UuidUtils.formatCompact(UUID.randomUUID());
        ChangeRequestEntity change = new ChangeRequestEntity();
        change.setId(changeId);
        change.setProjectId(projectId);
        change.setChangeType(normalized.entityType());
        change.setStatus(ChangeRequestEntity.STATUS_DRAFT);
        change.setEffectiveDate(cmd.effectiveDate());
        changeRequestRepository.insert(change);

        ChangeRequestVersionEntity version = new ChangeRequestVersionEntity();
        version.setId(UuidUtils.formatCompact(UUID.randomUUID()));
        version.setChangeRequestId(changeId);
        version.setVersionNo(1);
        version.setPayload(jsonHasher.canonicalJson(normalized.payload()));
        version.setOldBaselineId(project.getCurrentBaselineId());
        version.setReason(reason);
        version.setSubmitIdempotencyKey(cmd.requestId() == null ? null
                : "CHANGE:" + UuidUtils.format(cmd.requestId()));
        changeRequestVersionRepository.insert(version);

        change.submitForReview(version.getId());
        if (changeRequestRepository.update(change) == 0) {
            throw new BizException(ErrorCode.COMMON_409, "变更单已被并发修改，请重试");
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        workflowLauncher.start(new ProjectApprovalWorkflowStart(
                "CHANGE",
                UuidUtils.parse(changeId),
                UuidUtils.parse(version.getId()),
                normalized.templateCode(),
                UuidUtils.parse(projectId),
                project.getProjectType(),
                auth.userId(),
                Map.of("changeType", normalized.entityType()),
                now));

        auditRecorder.record(new AuditEvent(
                TraceIds.currentOrUntracked(),
                auth.userId(),
                null,
                AUDIT_CHANGE_REQUESTED,
                OBJECT_CHANGE,
                UuidUtils.parse(changeId),
                null,
                null,
                null,
                "发起项目变更：" + normalized.entityType(),
                null,
                null,
                clock.instant()));

        return new ChangeRequestCreated(changeId, version.getId(), change.getStatus(),
                normalized.templateCode());
    }

    private NormalizedChange validate(CreateChangeRequestCmd cmd, ProjectEntity project) {
        String type = cmd.changeType() == null ? ""
                : cmd.changeType().trim().toUpperCase(Locale.ROOT);
        Map<String, Object> payload = cmd.payload();
        return switch (type) {
            case "PERIOD" -> validatePeriod(payload, project);
            case "PI" -> validatePi(payload, project);
            case "MEMBER" -> validateMember(payload, project);
            case "OBJECTIVE" -> validateObjective(payload, cmd.attachmentFileIds());
            case "BUDGET" -> validateBudget(payload, cmd.attachmentFileIds());
            case "PARTNER" -> validatePartner(payload, cmd.attachmentFileIds());
            default -> throw new BizException(ErrorCode.COMMON_400,
                    "changeType 非法：" + cmd.changeType());
        };
    }

    private NormalizedChange validatePeriod(Map<String, Object> payload, ProjectEntity project) {
        LocalDate newStart = requireDate(payload, "newStartDate", "new_start_date");
        LocalDate newEnd = requireDate(payload, "newEndDate", "new_end_date");
        if (newEnd.isBefore(newStart)) {
            throw new BizException(ErrorCode.COMMON_400,
                    "payload.newEndDate 不能早于 payload.newStartDate");
        }
        if (project.getStartDate() != null && newStart.isBefore(project.getStartDate())) {
            throw new BizException(ErrorCode.COMMON_400,
                    "payload.newStartDate 不能早于原项目开始日期");
        }
        if (project.getEndDate() != null && newEnd.isBefore(project.getEndDate())) {
            throw new BizException(ErrorCode.COMMON_400,
                    "payload.newEndDate 不能早于原项目结束日期");
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("new_start_date", newStart.toString());
        normalized.put("new_end_date", newEnd.toString());
        return new NormalizedChange(ChangeRequestEntity.TYPE_PERIOD,
                ProjectApprovalTemplateCodes.CHANGE_PERIOD, normalized);
    }

    private NormalizedChange validatePi(Map<String, Object> payload, ProjectEntity project) {
        String newPi = compact(requireText(payload, "newPiUserId", "new_pi_user_id"),
                "payload.newPiUserId");
        String handoverNote = requireText(payload, "handoverNote", "handover_note");
        if (UuidUtils.sameId(project.getPiId(), newPi)) {
            throw new BizException(ErrorCode.COMMON_400,
                    "payload.newPiUserId 不能与现任负责人相同");
        }
        IamUserEntity user = iamUserRepository.findById(newPi)
                .orElseThrow(() -> new BizException(ErrorCode.PROJ_006, "新负责人不存在"));
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new BizException(ErrorCode.PROJ_006, "新负责人已离岗或禁用，不能接手项目");
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("new_pi_user_id", newPi);
        normalized.put("handover_note", handoverNote);
        return new NormalizedChange(ChangeRequestEntity.TYPE_PI,
                ProjectApprovalTemplateCodes.CHANGE_PI, normalized);
    }

    private NormalizedChange validateMember(Map<String, Object> payload, ProjectEntity project) {
        List<Map<String, Object>> changes = requireMapList(payload, "changes", "changes");
        Set<String> activeUserIds = projectMemberRepository.findByProjectId(project.getId()).stream()
                .filter(member -> ProjectMemberEntity.STATUS_ACTIVE.equals(member.getStatus()))
                .map(ProjectMemberEntity::getUserId)
                .collect(Collectors.toCollection(HashSet::new));
        List<Map<String, Object>> normalized = new ArrayList<>(changes.size());
        for (Map<String, Object> item : changes) {
            String userId = compact(requireText(item, "userId", "user_id"),
                    "payload.changes.userId");
            String role = requireText(item, "projectRole", "project_role");
            String action = requireText(item, "action", "action").toUpperCase(Locale.ROOT);
            if (!MEMBER_ACTIONS.contains(action)) {
                throw new BizException(ErrorCode.COMMON_400,
                        "payload.changes.action 仅支持 ADD/REMOVE");
            }
            if ("ADD".equals(action) && activeUserIds.stream()
                    .anyMatch(active -> UuidUtils.sameId(active, userId))) {
                throw new BizException(ErrorCode.PROJ_005,
                        "成员变更生效区间与现有成员重叠：" + userId);
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("user_id", userId);
            entry.put("project_role", role);
            entry.put("action", action);
            normalized.add(entry);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("changes", normalized);
        return new NormalizedChange(ChangeRequestEntity.TYPE_MEMBER,
                ProjectApprovalTemplateCodes.CHANGE_MEMBER, result);
    }

    private NormalizedChange validateObjective(Map<String, Object> payload,
                                               List<String> attachmentFileIds) {
        List<Map<String, Object>> objectives = requireMapList(payload, "objectives", "objectives");
        List<Map<String, Object>> normalized = new ArrayList<>(objectives.size());
        for (Map<String, Object> item : objectives) {
            String title = requireText(item, "title", "title");
            String criteria = requireText(item, "completionCriteria", "completion_criteria");
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("title", title);
            entry.put("completion_criteria", criteria);
            normalized.add(entry);
        }
        requireAttachments(attachmentFileIds, "目标变更需提供论证材料");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("objectives", normalized);
        return new NormalizedChange(ChangeRequestEntity.TYPE_GOAL,
                ProjectApprovalTemplateCodes.CHANGE_OBJECTIVE, result);
    }

    private NormalizedChange validateBudget(Map<String, Object> payload,
                                            List<String> attachmentFileIds) {
        String draftId = compact(requireText(payload, "budgetVersionDraftId",
                "budget_version_draft_id"), "payload.budgetVersionDraftId");
        requireAttachments(attachmentFileIds, "预算变更需提供调整说明");
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("budget_version_draft_id", draftId);
        return new NormalizedChange(ChangeRequestEntity.TYPE_BUDGET,
                ProjectApprovalTemplateCodes.CHANGE_BUDGET, normalized);
    }

    private NormalizedChange validatePartner(Map<String, Object> payload,
                                             List<String> attachmentFileIds) {
        List<Map<String, Object>> partners = requireMapList(payload, "partners", "partners");
        List<Map<String, Object>> normalized = new ArrayList<>(partners.size());
        boolean requiresAgreement = false;
        for (Map<String, Object> item : partners) {
            String unitName = requireText(item, "unitName", "unit_name");
            String role = requireText(item, "role", "role");
            String action = requireText(item, "action", "action").toUpperCase(Locale.ROOT);
            if (!PARTNER_ACTIONS.contains(action)) {
                throw new BizException(ErrorCode.COMMON_400,
                        "payload.partners.action 仅支持 ADD/REMOVE/UPDATE");
            }
            if (!"REMOVE".equals(action)) {
                requiresAgreement = true;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("unit_name", unitName);
            entry.put("role", role);
            entry.put("action", action);
            normalized.add(entry);
        }
        if (requiresAgreement) {
            requireAttachments(attachmentFileIds, "合作单位新增/更新需提供合作协议");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("partners", normalized);
        return new NormalizedChange(ChangeRequestEntity.TYPE_PARTNER,
                ProjectApprovalTemplateCodes.CHANGE_PARTNER, result);
    }

    private static void requireAttachments(List<String> attachmentFileIds, String message) {
        if (attachmentFileIds.isEmpty()) {
            throw new BizException(ErrorCode.COMMON_400,
                    "payload.attachmentFileIds 不能为空（" + message + "）");
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
                Action.of(ProjectPermissions.CHANGE_CREATE), resource);
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

    private static Object value(Map<String, Object> source, String camelKey, String snakeKey) {
        return source.containsKey(snakeKey) ? source.get(snakeKey) : source.get(camelKey);
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BizException(ErrorCode.COMMON_400, message);
        }
        return value.trim();
    }

    private static String requireText(Map<String, Object> source, String camelKey,
                                      String snakeKey) {
        Object value = value(source, camelKey, snakeKey);
        if (!(value instanceof CharSequence text) || text.toString().isBlank()) {
            throw new BizException(ErrorCode.COMMON_400, "payload." + camelKey + " 不能为空");
        }
        return text.toString().trim();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> requireMapList(Map<String, Object> source,
                                                           String camelKey, String snakeKey) {
        Object value = value(source, camelKey, snakeKey);
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            throw new BizException(ErrorCode.COMMON_400, "payload." + camelKey + " 不能为空");
        }
        List<Map<String, Object>> result = new ArrayList<>(list.size());
        for (Object element : list) {
            if (!(element instanceof Map<?, ?> map)) {
                throw new BizException(ErrorCode.COMMON_400,
                        "payload." + camelKey + " 结构非法");
            }
            result.add((Map<String, Object>) map);
        }
        return result;
    }

    private static LocalDate requireDate(Map<String, Object> source, String camelKey,
                                         String snakeKey) {
        Object value = value(source, camelKey, snakeKey);
        if (value == null) {
            throw new BizException(ErrorCode.COMMON_400, "payload." + camelKey + " 不能为空");
        }
        if (value instanceof LocalDate date) {
            return date;
        }
        try {
            return LocalDate.parse(value.toString());
        } catch (DateTimeParseException ex) {
            throw new BizException(ErrorCode.COMMON_400,
                    "payload." + camelKey + " 格式非法（yyyy-MM-dd）");
        }
    }

    private static String compact(String value, String field) {
        try {
            return UuidUtils.formatCompact(UuidUtils.parse(value));
        } catch (IllegalArgumentException ex) {
            throw new BizException(ErrorCode.COMMON_400, field + " 非法");
        }
    }

    /**
     * 受控变更的规范化结果。
     *
     * @param entityType   落库的变更类型（OBJECTIVE → GOAL）
     * @param templateCode 审批模板编码
     * @param payload      规范化载荷（snake_case）
     */
    private record NormalizedChange(String entityType, String templateCode,
                                    Map<String, Object> payload) {
    }
}
