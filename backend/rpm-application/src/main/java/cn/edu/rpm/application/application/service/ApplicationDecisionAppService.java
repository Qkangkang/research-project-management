package cn.edu.rpm.application.application.service;

import cn.edu.rpm.application.application.dto.DecisionCmd;
import cn.edu.rpm.application.application.dto.DecisionResult;
import cn.edu.rpm.application.domain.model.ApplicationPermissions;
import cn.edu.rpm.application.domain.model.ProjectCreationCommand;
import cn.edu.rpm.application.domain.model.ProjectCreationResult;
import cn.edu.rpm.application.domain.port.ProjectCreationPort;
import cn.edu.rpm.application.infrastructure.entity.ApplicationEntity;
import cn.edu.rpm.application.infrastructure.entity.ApplicationReviewEntity;
import cn.edu.rpm.application.infrastructure.entity.ApplicationVersionEntity;
import cn.edu.rpm.application.infrastructure.repository.ApplicationRepository;
import cn.edu.rpm.application.infrastructure.repository.ApplicationReviewRepository;
import cn.edu.rpm.application.infrastructure.repository.ApplicationVersionRepository;
import cn.edu.rpm.audit.domain.model.AuditEvent;
import cn.edu.rpm.audit.domain.service.AuditRecorder;
import cn.edu.rpm.common.domain.model.OutboxEvent;
import cn.edu.rpm.common.domain.port.OutboxAppendPort;
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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 立项决策应用服务（T-APP-019；技术文档 04 §3.4、07 §2.3）。
 *
 * <p>{@code APPROVE}：行锁 → 授权 → 自审校验 → 版本校验 → 调用
 * {@link ProjectCreationPort}（幂等键为申报版本 ID）生成项目/基线/里程碑 →
 * 申报与版本置 {@code APPROVED} → 写决策意见、outbox 与审计。
 * 同一批准版本重复调用返回首次 {@code project_id}（TC-APP-029）。
 * {@code REJECT}：申报置 {@code REJECTED}，不生成任何项目数据（TC-APP-030）。</p>
 *
 * <p>决策与立项创建同事务提交：立项失败则申报状态不推进（RULE-E2）。</p>
 */
@Service
public class ApplicationDecisionAppService {

    /** 决策：批准。 */
    public static final String DECISION_APPROVE = "APPROVE";

    /** 决策：不批准。 */
    public static final String DECISION_REJECT = "REJECT";

    /** 审计动作：立项决策。 */
    public static final String AUDIT_DECIDED = "APPLICATION_DECIDED";

    private static final String OBJECT_APPLICATION = "APPLICATION";

    private final ApplicationRepository applicationRepository;

    private final ApplicationVersionRepository applicationVersionRepository;

    private final ApplicationReviewRepository applicationReviewRepository;

    private final ProjectCreationPort projectCreationPort;

    private final OutboxAppendPort outboxAppendPort;

    private final AuthorizationService authorizationService;

    private final AuditRecorder auditRecorder;

    private final ObjectMapper objectMapper;

    private final Clock clock;

    /**
     * 构造决策服务。
     *
     * @param applicationRepository       申报主档仓储
     * @param applicationVersionRepository 申报版本仓储
     * @param applicationReviewRepository 审查意见仓储
     * @param projectCreationPort         立项创建端口（幂等）
     * @param outboxAppendPort            Outbox 追加端口
     * @param authorizationService        统一授权服务
     * @param auditRecorder               审计记录器
     * @param objectMapper                表单快照 JSON 解析器
     * @param clock                       时间基准
     */
    public ApplicationDecisionAppService(ApplicationRepository applicationRepository,
                                         ApplicationVersionRepository applicationVersionRepository,
                                         ApplicationReviewRepository applicationReviewRepository,
                                         ProjectCreationPort projectCreationPort,
                                         OutboxAppendPort outboxAppendPort,
                                         AuthorizationService authorizationService,
                                         AuditRecorder auditRecorder,
                                         ObjectMapper objectMapper,
                                         Clock clock) {
        this.applicationRepository = Objects.requireNonNull(applicationRepository,
                "applicationRepository 不能为空");
        this.applicationVersionRepository = Objects.requireNonNull(applicationVersionRepository,
                "applicationVersionRepository 不能为空");
        this.applicationReviewRepository = Objects.requireNonNull(applicationReviewRepository,
                "applicationReviewRepository 不能为空");
        this.projectCreationPort = Objects.requireNonNull(projectCreationPort,
                "projectCreationPort 不能为空");
        this.outboxAppendPort = Objects.requireNonNull(outboxAppendPort, "outboxAppendPort 不能为空");
        this.authorizationService = Objects.requireNonNull(authorizationService,
                "authorizationService 不能为空");
        this.auditRecorder = Objects.requireNonNull(auditRecorder, "auditRecorder 不能为空");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper 不能为空");
        this.clock = Objects.requireNonNull(clock, "clock 不能为空");
    }

    /**
     * 执行立项决策（TC-APP-028 / TC-APP-029 / TC-APP-030 / TC-APP-031）。
     *
     * @param applicationId 申报 ID
     * @param cmd           决策命令
     * @param auth          当前请求授权上下文
     * @return 决策结果（批准时含项目/基线标识）
     * @throws BizException 参数非法 {@code RPM-COMMON-400}；无权限 {@code RPM-COMMON-403}；
     *                      不存在 {@code RPM-COMMON-404}；状态不允许 {@code RPM-APPLY-004}；
     *                      版本不一致 {@code RPM-WF-005}；自审 {@code RPM-WF-003}
     */
    @Transactional
    public DecisionResult decide(String applicationId, DecisionCmd cmd, AuthContext auth) {
        Objects.requireNonNull(cmd, "cmd 不能为空");
        Objects.requireNonNull(auth, "auth 不能为空");
        String decision = requireDecision(cmd.decision());
        String versionId = requireText(cmd.versionId(), "version_id 不能为空");

        ApplicationEntity application = applicationRepository.selectForUpdate(applicationId)
                .orElseThrow(() -> new BizException(ErrorCode.COMMON_404, "申报不存在"));
        requirePermission(application, auth);
        if (UuidUtils.sameId(application.getApplicantId(), auth.userId())) {
            throw new BizException(ErrorCode.WF_003);
        }
        if (!UuidUtils.sameId(application.getCurrentVersionId(), versionId)) {
            throw new BizException(ErrorCode.WF_005, "决策版本不是当前提交版本");
        }

        if (DECISION_REJECT.equals(decision)) {
            return reject(application, cmd, auth, versionId);
        }
        return approve(application, cmd, auth, versionId);
    }

    private DecisionResult reject(ApplicationEntity application, DecisionCmd cmd, AuthContext auth,
                                  String versionId) {
        requireInReview(application);
        if (cmd.comment() == null || cmd.comment().isBlank()) {
            throw new BizException(ErrorCode.COMMON_400, "不批准必须填写理由");
        }
        ApplicationVersionEntity version = requireVersion(application.getId(), versionId);
        application.markRejected();
        version.markRejected();
        persist(application, version);
        insertReview(versionId, ApplicationReviewEntity.DECISION_REJECT, cmd.comment(), auth);
        appendOutbox(application, "APPLICATION_REJECTED", null);
        audit(application, auth, "立项决策不批准");
        return new DecisionResult(application.getId(), null, null, null, application.getStatus());
    }

    private DecisionResult approve(ApplicationEntity application, DecisionCmd cmd, AuthContext auth,
                                   String versionId) {
        validateDates(cmd.approvedStartDate(), cmd.approvedEndDate());
        ApplicationVersionEntity version = requireVersion(application.getId(), versionId);

        if (!ApplicationEntity.STATUS_APPROVED.equals(application.getStatus())) {
            requireInReview(application);
        }
        ProjectCreationCommand creationCommand = toCreationCommand(application, version,
                cmd.approvedStartDate(), cmd.approvedEndDate());
        ProjectCreationResult created = projectCreationPort.createFromVersion(creationCommand);

        if (!ApplicationEntity.STATUS_APPROVED.equals(application.getStatus())) {
            application.markApproved();
            version.markApproved();
            persist(application, version);
            insertReview(versionId, ApplicationReviewEntity.DECISION_APPROVE, cmd.comment(), auth);
            appendOutbox(application, "APPLICATION_APPROVED", created);
            audit(application, auth, "立项决策批准");
        }
        return new DecisionResult(application.getId(), created.projectId(), created.projectNo(),
                created.baselineId(), application.getStatus());
    }

    private void persist(ApplicationEntity application, ApplicationVersionEntity version) {
        if (applicationVersionRepository.update(version) == 0) {
            throw new BizException(ErrorCode.COMMON_409, "申报版本已被并发修改，请刷新后重试");
        }
        if (applicationRepository.update(application) == 0) {
            throw new BizException(ErrorCode.COMMON_409, "申报已被并发修改，请刷新后重试");
        }
    }

    private void insertReview(String versionId, String decision, String comment, AuthContext auth) {
        ApplicationReviewEntity review = new ApplicationReviewEntity();
        review.setApplicationVersionId(versionId);
        review.setReviewStage(ApplicationReviewEntity.STAGE_DECISION);
        review.setReviewerId(UuidUtils.formatCompact(auth.userId()));
        review.setDecision(decision);
        review.setIssues("[]");
        review.setComment(comment);
        review.setReviewedAt(OffsetDateTime.now(clock));
        applicationReviewRepository.insert(review);
    }

    private ProjectCreationCommand toCreationCommand(ApplicationEntity application,
                                                     ApplicationVersionEntity version,
                                                     LocalDate startDate, LocalDate endDate) {
        Map<String, Object> formData = parseFormData(version.getFormData());
        return new ProjectCreationCommand(
                application.getId(),
                version.getId(),
                ProjectCreationCommand.SOURCE_INTERNAL,
                application.getTitle(),
                application.getProjectType(),
                application.getDeptId(),
                application.getApplicantId(),
                startDate,
                endDate,
                null,
                null,
                null,
                formData,
                parseMilestones(formData));
    }

    private Map<String, Object> parseFormData(String formData) {
        if (formData == null || formData.isBlank()) {
            return Map.of();
        }
        try {
            JsonNode node = objectMapper.readTree(formData);
            if (!node.isObject()) {
                return Map.of();
            }
            return objectMapper.convertValue(node,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                    });
        } catch (JsonProcessingException ex) {
            throw new BizException(ErrorCode.SYS_002, "申报版本快照不是合法 JSON");
        }
    }

    @SuppressWarnings("unchecked")
    private static List<ProjectCreationCommand.MilestonePlan> parseMilestones(Map<String, Object> formData) {
        Object raw = formData.get("milestones");
        if (!(raw instanceof List<?> items)) {
            return List.of();
        }
        List<ProjectCreationCommand.MilestonePlan> plans = new ArrayList<>(items.size());
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> row)) {
                continue;
            }
            Map<String, Object> values = (Map<String, Object>) row;
            String code = text(values.get("code"));
            String title = text(values.get("title"));
            String planDate = text(values.get("plan_date"));
            if (code == null || title == null || planDate == null) {
                continue;
            }
            plans.add(new ProjectCreationCommand.MilestonePlan(
                    code, title, LocalDate.parse(planDate),
                    text(values.get("owner_id")), text(values.get("completion_criteria"))));
        }
        return plans;
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private ApplicationVersionEntity requireVersion(String applicationId, String versionId) {
        return applicationVersionRepository.findById(versionId)
                .filter(version -> UuidUtils.sameId(applicationId, version.getApplicationId()))
                .orElseThrow(() -> new BizException(ErrorCode.COMMON_404, "申报版本不存在"));
    }

    private static void requireInReview(ApplicationEntity application) {
        if (!ApplicationEntity.STATUS_IN_REVIEW.equals(application.getStatus())) {
            throw new BizException(ErrorCode.APPLY_004, "当前状态不允许立项决策");
        }
    }

    private static void validateDates(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new BizException(ErrorCode.COMMON_400, "批准开始日期与结束日期不能为空");
        }
        if (startDate.isAfter(endDate)) {
            throw new BizException(ErrorCode.COMMON_400, "批准开始日期不能晚于结束日期");
        }
    }

    private void appendOutbox(ApplicationEntity application, String eventType,
                              ProjectCreationResult created) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("application_id", application.getId());
        payload.put("application_no", application.getApplicationNo());
        payload.put("version_id", application.getCurrentVersionId());
        payload.put("status", application.getStatus());
        if (created != null) {
            payload.put("project_id", created.projectId());
            payload.put("project_no", created.projectNo());
            payload.put("baseline_id", created.baselineId());
        }
        outboxAppendPort.append(new OutboxEvent(
                UUID.randomUUID(),
                OBJECT_APPLICATION,
                UuidUtils.parse(application.getId()),
                eventType,
                payload,
                TraceIds.currentOrUntracked(),
                eventType + ":" + application.getCurrentVersionId()));
    }

    private void audit(ApplicationEntity application, AuthContext auth, String reason) {
        auditRecorder.record(new AuditEvent(
                TraceIds.currentOrUntracked(),
                auth.userId(),
                null,
                AUDIT_DECIDED,
                OBJECT_APPLICATION,
                UuidUtils.parse(application.getId()),
                application.getVersion(),
                null,
                null,
                reason,
                null,
                null,
                OffsetDateTime.now(clock).toInstant()));
    }

    private void requirePermission(ApplicationEntity application, AuthContext auth) {
        AuthDecision decision = authorizationService.check(auth,
                Action.of(ApplicationPermissions.DECIDE),
                new ResourceRef(ResourceTypes.PROJECT,
                        UuidUtils.parse(application.getId()),
                        parseOptionalUuid(application.getApplicantId()),
                        parseOptionalUuid(application.getDeptId()),
                        Set.of()));
        if (!decision.allowed()) {
            throw new BizException(ErrorCode.COMMON_403);
        }
    }

    private static String requireDecision(String decision) {
        if (decision == null || decision.isBlank()) {
            throw new BizException(ErrorCode.COMMON_400, "决策不能为空");
        }
        String normalized = decision.trim().toUpperCase(Locale.ROOT);
        if (!DECISION_APPROVE.equals(normalized) && !DECISION_REJECT.equals(normalized)) {
            throw new BizException(ErrorCode.COMMON_400, "决策必须为 APPROVE 或 REJECT");
        }
        return normalized;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BizException(ErrorCode.COMMON_400, message);
        }
        return value.trim();
    }

    private static UUID parseOptionalUuid(String value) {
        return value == null || value.isBlank() ? null : UuidUtils.parse(value);
    }
}
