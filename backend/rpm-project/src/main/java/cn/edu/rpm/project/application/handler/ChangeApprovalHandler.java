package cn.edu.rpm.project.application.handler;

import cn.edu.rpm.audit.domain.model.AuditEvent;
import cn.edu.rpm.audit.domain.service.AuditRecorder;
import cn.edu.rpm.common.domain.model.OutboxEvent;
import cn.edu.rpm.common.domain.port.OutboxAppendPort;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.common.util.CanonicalJsonHasher;
import cn.edu.rpm.common.util.TraceIds;
import cn.edu.rpm.common.util.UuidUtils;
import cn.edu.rpm.project.domain.port.BudgetChangePort;
import cn.edu.rpm.project.domain.port.HandoverTaskTransferPort;
import cn.edu.rpm.project.infrastructure.entity.ChangeRequestEntity;
import cn.edu.rpm.project.infrastructure.entity.ChangeRequestVersionEntity;
import cn.edu.rpm.project.infrastructure.entity.ProjectBaselineEntity;
import cn.edu.rpm.project.infrastructure.entity.ProjectEntity;
import cn.edu.rpm.project.infrastructure.entity.ProjectMemberEntity;
import cn.edu.rpm.project.infrastructure.repository.ChangeRequestRepository;
import cn.edu.rpm.project.infrastructure.repository.ChangeRequestVersionRepository;
import cn.edu.rpm.project.infrastructure.repository.ProjectBaselineRepository;
import cn.edu.rpm.project.infrastructure.repository.ProjectMemberRepository;
import cn.edu.rpm.project.infrastructure.repository.ProjectRepository;
import cn.edu.rpm.project.infrastructure.repository.ProjectRiskRepository;
import cn.edu.rpm.workflow.domain.dto.ApprovalResult;
import cn.edu.rpm.workflow.domain.port.ApprovalResultHandler;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 项目变更审批回调（T-PRJ-021；技术文档 08 §8.2、§8.3、04 §3.9）。
 *
 * <p>批准时锁定变更单与项目，按 {@code old_baseline_id} 校验审批版本仍是当前基线；
 * 非预算变更生成 {@code version_no + 1} 的新基线并原子切换，旧基线置
 * {@code SUPERSEDED}；预算变更委托 {@link BudgetChangePort}。拒绝/退回不生成基线。</p>
 *
 * <p>并发两笔变更时，第二笔在项目行锁释放后会发现其 {@code old_baseline_id}
 * 已不是当前基线，返回 {@code RPM-WF-005}，保证同一时刻只有一个 EFFECTIVE 基线。</p>
 */
@Component
public class ChangeApprovalHandler implements ApprovalResultHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ChangeApprovalHandler.class);

    /** 审计动作：变更批准。 */
    public static final String AUDIT_CHANGE_APPROVED = "PROJECT_CHANGE_APPROVED";

    /** 审计动作：变更拒绝。 */
    public static final String AUDIT_CHANGE_REJECTED = "PROJECT_CHANGE_REJECTED";

    /** 审计动作：变更退回。 */
    public static final String AUDIT_CHANGE_RETURNED = "PROJECT_CHANGE_RETURNED";

    /** 审计动作：审批版本过期。 */
    public static final String AUDIT_STALE_APPROVAL = "STALE_APPROVAL";

    /** 审计对象类型：项目变更单。 */
    static final String OBJECT_CHANGE = "PROJECT_CHANGE";

    /** 审计对象类型：项目基线。 */
    static final String OBJECT_BASELINE = "PROJECT_BASELINE";

    /** 事件类型：基线切换。 */
    public static final String EVENT_BASELINE_CHANGED = "BASELINE_CHANGED";

    /** 事件类型：预算变更生效。 */
    public static final String EVENT_BUDGET_CHANGE_APPLIED = "BUDGET_CHANGE_APPLIED";

    /** 审计对象类型：项目。 */
    static final String OBJECT_PROJECT = "PROJECT";

    private final ChangeRequestRepository changeRequestRepository;

    private final ChangeRequestVersionRepository versionRepository;

    private final ProjectRepository projectRepository;

    private final ProjectBaselineRepository baselineRepository;

    private final ProjectMemberRepository projectMemberRepository;

    private final ProjectRiskRepository projectRiskRepository;

    private final List<BudgetChangePort> budgetChangePorts;

    private final List<HandoverTaskTransferPort> taskTransferPorts;

    private final OutboxAppendPort outboxAppendPort;

    private final AuditRecorder auditRecorder;

    private final CanonicalJsonHasher jsonHasher;

    private final ObjectMapper objectMapper;

    private final Clock clock;

    /**
     * 构造变更审批回调。
     *
     * @param changeRequestRepository 变更主档仓储
     * @param versionRepository       变更版本仓储
     * @param projectRepository       项目仓储
     * @param baselineRepository      基线仓储
     * @param projectMemberRepository 成员仓储
     * @param projectRiskRepository   风险仓储（PI 变更转移责任人）
     * @param budgetChangePorts       预算变更端口（BUDGET 类型）
     * @param taskTransferPorts       待办/任务转移端口（PI 变更）
     * @param outboxAppendPort        Outbox 追加端口
     * @param auditRecorder           审计记录器
     * @param jsonHasher              规范化 JSON 工具
     * @param objectMapper            基线快照解析
     * @param clock                   时间基准
     */
    public ChangeApprovalHandler(ChangeRequestRepository changeRequestRepository,
                                 ChangeRequestVersionRepository versionRepository,
                                 ProjectRepository projectRepository,
                                 ProjectBaselineRepository baselineRepository,
                                 ProjectMemberRepository projectMemberRepository,
                                 ProjectRiskRepository projectRiskRepository,
                                 List<BudgetChangePort> budgetChangePorts,
                                 List<HandoverTaskTransferPort> taskTransferPorts,
                                 OutboxAppendPort outboxAppendPort,
                                 AuditRecorder auditRecorder,
                                 CanonicalJsonHasher jsonHasher,
                                 ObjectMapper objectMapper,
                                 Clock clock) {
        this.changeRequestRepository = Objects.requireNonNull(changeRequestRepository,
                "changeRequestRepository 不能为空");
        this.versionRepository = Objects.requireNonNull(versionRepository, "versionRepository 不能为空");
        this.projectRepository = Objects.requireNonNull(projectRepository, "projectRepository 不能为空");
        this.baselineRepository = Objects.requireNonNull(baselineRepository,
                "baselineRepository 不能为空");
        this.projectMemberRepository = Objects.requireNonNull(projectMemberRepository,
                "projectMemberRepository 不能为空");
        this.projectRiskRepository = Objects.requireNonNull(projectRiskRepository,
                "projectRiskRepository 不能为空");
        this.budgetChangePorts = budgetChangePorts == null ? List.of() : List.copyOf(budgetChangePorts);
        this.taskTransferPorts = taskTransferPorts == null ? List.of() : List.copyOf(taskTransferPorts);
        this.outboxAppendPort = Objects.requireNonNull(outboxAppendPort, "outboxAppendPort 不能为空");
        this.auditRecorder = Objects.requireNonNull(auditRecorder, "auditRecorder 不能为空");
        this.jsonHasher = Objects.requireNonNull(jsonHasher, "jsonHasher 不能为空");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper 不能为空");
        this.clock = Objects.requireNonNull(clock, "clock 不能为空");
    }

    @Override
    public String bizType() {
        return "CHANGE";
    }

    /**
     * 变更批准：非预算类型切换基线，预算类型生成新预算版本（TC-PRJ-021、TC-CHG-107）。
     *
     * @param result 审批结果
     * @throws BizException 审批版本过期 {@code RPM-WF-005}；并发冲突 {@code RPM-COMMON-409}
     */
    @Override
    @Transactional
    public void onApproved(ApprovalResult result) {
        ChangeRequestEntity change = lockChange(result);
        if (ChangeRequestEntity.STATUS_APPROVED.equals(change.getStatus())) {
            LOG.info("变更已批准，幂等跳过 changeId={}", change.getId());
            return;
        }
        if (!ChangeRequestEntity.STATUS_IN_REVIEW.equals(change.getStatus())) {
            throw new BizException(ErrorCode.COMMON_422,
                    "变更单状态不允许批准：" + change.getStatus());
        }
        ChangeRequestVersionEntity version = currentVersion(change, result);
        OffsetDateTime now = OffsetDateTime.now(clock);

        if (ChangeRequestEntity.TYPE_BUDGET.equals(change.getChangeType())) {
            applyBudgetChange(change, version, result);
            markApproved(change, now);
            outboxAppendPort.append(new OutboxEvent(UUID.randomUUID(), OBJECT_CHANGE,
                    UuidUtils.parse(change.getId()), EVENT_BUDGET_CHANGE_APPLIED,
                    Map.of("change_id", change.getId(), "project_id", change.getProjectId(),
                            "version_id", version.getId()),
                    TraceIds.currentOrUntracked(),
                    EVENT_BUDGET_CHANGE_APPLIED + ":" + change.getId()));
            audit(result, AUDIT_CHANGE_APPROVED, OBJECT_CHANGE, change.getId(), null,
                    "预算变更批准");
            return;
        }

        ProjectEntity project = projectRepository.selectForUpdate(change.getProjectId())
                .orElseThrow(() -> new BizException(ErrorCode.PROJ_001));
        if (!UuidUtils.sameId(version.getOldBaselineId(), project.getCurrentBaselineId())) {
            stale(result, change);
            throw new BizException(ErrorCode.WF_005, "变更审批版本已过期");
        }
        ProjectBaselineEntity oldBaseline = baselineRepository
                .findById(project.getCurrentBaselineId())
                .orElseThrow(() -> new BizException(ErrorCode.PROJ_004));

        Map<String, Object> snapshot = parseSnapshot(oldBaseline.getSnapshot());
        applyPayload(project, change, version, snapshot, now);

        String canonical = jsonHasher.canonicalJson(snapshot);
        ProjectBaselineEntity newBaseline = new ProjectBaselineEntity();
        newBaseline.setId(UuidUtils.formatCompact(UUID.randomUUID()));
        newBaseline.setProjectId(project.getId());
        newBaseline.setVersionNo(baselineRepository.findMaxVersionNo(project.getId()) + 1);
        newBaseline.setSnapshot(canonical);
        newBaseline.setSnapshotHash(jsonHasher.sha256(canonical));
        newBaseline.setEffectiveAt(now);
        newBaseline.setStatus(ProjectBaselineEntity.STATUS_EFFECTIVE);
        newBaseline.setSourceChangeId(change.getId());

        oldBaseline.supersede();
        if (baselineRepository.update(oldBaseline) == 0) {
            throw new BizException(ErrorCode.COMMON_409, "基线已被并发修改，请重试");
        }
        try {
            baselineRepository.insert(newBaseline);
        } catch (DuplicateKeyException ex) {
            throw new BizException(ErrorCode.COMMON_409, "基线版本号冲突，请重试");
        }

        project.switchBaseline(newBaseline.getId());
        if (projectRepository.update(project) == 0) {
            throw new BizException(ErrorCode.COMMON_409, "项目已被并发修改，请重试");
        }
        markApproved(change, now);
        version.attachNewBaseline(newBaseline.getId());
        if (versionRepository.update(version) == 0) {
            throw new BizException(ErrorCode.COMMON_409, "变更版本已被并发修改，请重试");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("project_id", project.getId());
        payload.put("change_id", change.getId());
        payload.put("old_baseline_id", oldBaseline.getId());
        payload.put("new_baseline_id", newBaseline.getId());
        payload.put("baseline_version_no", newBaseline.getVersionNo());
        outboxAppendPort.append(new OutboxEvent(UUID.randomUUID(), OBJECT_PROJECT,
                UuidUtils.parse(project.getId()), EVENT_BASELINE_CHANGED, payload,
                TraceIds.currentOrUntracked(),
                EVENT_BASELINE_CHANGED + ":" + change.getId()));
        audit(result, AUDIT_CHANGE_APPROVED, OBJECT_BASELINE, newBaseline.getId(),
                newBaseline.getVersionNo(), "变更批准并切换基线");
    }

    @Override
    @Transactional
    public void onRejected(ApprovalResult result) {
        ChangeRequestEntity change = lockChange(result);
        if (ChangeRequestEntity.STATUS_REJECTED.equals(change.getStatus())) {
            return;
        }
        if (!ChangeRequestEntity.STATUS_IN_REVIEW.equals(change.getStatus())) {
            throw new BizException(ErrorCode.COMMON_422,
                    "变更单状态不允许拒绝：" + change.getStatus());
        }
        change.reject();
        if (changeRequestRepository.update(change) == 0) {
            throw new BizException(ErrorCode.COMMON_409, "变更单已被并发修改，请重试");
        }
        audit(result, AUDIT_CHANGE_REJECTED, OBJECT_CHANGE, change.getId(), null,
                "变更被拒绝");
    }

    @Override
    @Transactional
    public void onReturned(ApprovalResult result) {
        ChangeRequestEntity change = lockChange(result);
        if (ChangeRequestEntity.STATUS_RETURNED.equals(change.getStatus())) {
            return;
        }
        if (!ChangeRequestEntity.STATUS_IN_REVIEW.equals(change.getStatus())) {
            throw new BizException(ErrorCode.COMMON_422,
                    "变更单状态不允许退回：" + change.getStatus());
        }
        change.returned();
        if (changeRequestRepository.update(change) == 0) {
            throw new BizException(ErrorCode.COMMON_409, "变更单已被并发修改，请重试");
        }
        audit(result, AUDIT_CHANGE_RETURNED, OBJECT_CHANGE, change.getId(), null,
                "变更被退回");
    }

    @Override
    @Transactional
    public void onWithdrawn(ApprovalResult result) {
        ChangeRequestEntity change = lockChange(result);
        if (ChangeRequestEntity.STATUS_CANCELLED.equals(change.getStatus())) {
            return;
        }
        change.cancel();
        if (changeRequestRepository.update(change) == 0) {
            throw new BizException(ErrorCode.COMMON_409, "变更单已被并发修改，请重试");
        }
        audit(result, "PROJECT_CHANGE_CANCELLED", OBJECT_CHANGE, change.getId(), null,
                "变更审批撤回");
    }

    private ChangeRequestEntity lockChange(ApprovalResult result) {
        String changeId = UuidUtils.formatCompact(result.bizId());
        return changeRequestRepository.selectForUpdate(changeId)
                .orElseThrow(() -> new BizException(ErrorCode.COMMON_404, "变更单不存在"));
    }

    private ChangeRequestVersionEntity currentVersion(ChangeRequestEntity change,
                                                      ApprovalResult result) {
        String versionId = UuidUtils.formatCompact(result.bizVersionId());
        if (!UuidUtils.sameId(change.getCurrentVersionId(), versionId)) {
            stale(result, change);
            throw new BizException(ErrorCode.WF_005, "变更审批版本已过期");
        }
        return versionRepository.findById(versionId)
                .orElseThrow(() -> new BizException(ErrorCode.WF_005, "变更版本不存在"));
    }

    private void applyBudgetChange(ChangeRequestEntity change, ChangeRequestVersionEntity version,
                                   ApprovalResult result) {
        if (budgetChangePorts.isEmpty()) {
            throw new BizException(ErrorCode.COMMON_422, "预算变更端口未装配，无法生效");
        }
        if (budgetChangePorts.size() > 1) {
            throw new BizException(ErrorCode.COMMON_500, "预算变更端口存在多个实现");
        }
        budgetChangePorts.get(0).applyBudgetChange(new BudgetChangePort.BudgetChangeCommand(
                change.getProjectId(), change.getId(), version.getId(),
                version.getPayload(), result.operatorId()));
    }

    @SuppressWarnings("unchecked")
    private void applyPayload(ProjectEntity project, ChangeRequestEntity change,
                              ChangeRequestVersionEntity version,
                              Map<String, Object> snapshot, OffsetDateTime now) {
        Map<String, Object> payload = parseSnapshot(version.getPayload());
        switch (change.getChangeType()) {
            case ChangeRequestEntity.TYPE_PERIOD -> {
                String start = Objects.toString(payload.get("new_start_date"), null);
                String end = Objects.toString(payload.get("new_end_date"), null);
                if (start == null || end == null) {
                    throw new BizException(ErrorCode.COMMON_400, "周期变更载荷缺少日期");
                }
                LocalDate startDate = LocalDate.parse(start);
                LocalDate endDate = LocalDate.parse(end);
                snapshot.put("start_date", start);
                snapshot.put("end_date", end);
                project.adjustPeriod(startDate, endDate);
            }
            case ChangeRequestEntity.TYPE_PI -> {
                String newPi = Objects.toString(payload.get("new_pi_user_id"), null);
                if (newPi == null) {
                    throw new BizException(ErrorCode.COMMON_400, "负责人变更载荷缺少新负责人");
                }
                String oldPi = project.getPiId();
                project.handoverTo(newPi);
                snapshot.put("pi_id", newPi);
                switchPiMembership(project.getId(), oldPi, newPi, now);
                projectRiskRepository.transferOwner(project.getId(), oldPi, newPi);
                taskTransferPorts.forEach(port ->
                        port.transferTasksAndTodos(project.getId(), oldPi, newPi));
            }
            case ChangeRequestEntity.TYPE_MEMBER -> {
                Object changes = payload.get("changes");
                applyMemberChanges(project.getId(), changes, now);
                snapshot.put("member_changes", changes);
            }
            case ChangeRequestEntity.TYPE_GOAL -> snapshot.put("objectives",
                    payload.get("objectives"));
            case ChangeRequestEntity.TYPE_PARTNER -> snapshot.put("partners",
                    payload.get("partners"));
            default -> snapshot.put("last_change", payload);
        }
    }

    private void switchPiMembership(String projectId, String oldPi, String newPi,
                                    OffsetDateTime at) {
        List<ProjectMemberEntity> active = projectMemberRepository
                .findActiveByProjectId(projectId, at);
        boolean newMemberExists = false;
        for (ProjectMemberEntity member : active) {
            if (UuidUtils.sameId(member.getUserId(), oldPi)) {
                member.deactivate(at);
                projectMemberRepository.update(member);
            }
            if (UuidUtils.sameId(member.getUserId(), newPi)) {
                member.setProjectRole("PI");
                projectMemberRepository.update(member);
                newMemberExists = true;
            }
        }
        if (!newMemberExists) {
            ProjectMemberEntity created = new ProjectMemberEntity();
            created.setId(UuidUtils.formatCompact(UUID.randomUUID()));
            created.setProjectId(projectId);
            created.setUserId(newPi);
            created.setProjectRole("PI");
            created.setValidFrom(at);
            created.setStatus(ProjectMemberEntity.STATUS_ACTIVE);
            projectMemberRepository.insert(created);
        }
    }

    @SuppressWarnings("unchecked")
    private void applyMemberChanges(String projectId, Object rawChanges, OffsetDateTime at) {
        if (!(rawChanges instanceof List<?> changes)) {
            return;
        }
        List<ProjectMemberEntity> active = projectMemberRepository
                .findActiveByProjectId(projectId, at);
        for (Object raw : changes) {
            if (!(raw instanceof Map<?, ?> change)) {
                continue;
            }
            String userId = Objects.toString(change.get("user_id"), null);
            String action = Objects.toString(change.get("action"), "");
            if (userId == null) {
                continue;
            }
            ProjectMemberEntity existing = active.stream()
                    .filter(member -> UuidUtils.sameId(member.getUserId(), userId))
                    .findFirst().orElse(null);
            if ("REMOVE".equals(action) && existing != null) {
                existing.deactivate(at);
                projectMemberRepository.update(existing);
            } else if ("ADD".equals(action) && existing == null) {
                ProjectMemberEntity created = new ProjectMemberEntity();
                created.setId(UuidUtils.formatCompact(UUID.randomUUID()));
                created.setProjectId(projectId);
                created.setUserId(userId);
                created.setProjectRole(Objects.toString(change.get("project_role"), "MEMBER"));
                created.setValidFrom(at);
                created.setStatus(ProjectMemberEntity.STATUS_ACTIVE);
                projectMemberRepository.insert(created);
            }
        }
    }

    private Map<String, Object> parseSnapshot(String json) {
        try {
            return objectMapper.readValue(json,
                    new TypeReference<Map<String, Object>>() { });
        } catch (IOException ex) {
            throw new BizException(ErrorCode.COMMON_500, "基线/变更快照解析失败");
        }
    }

    private void markApproved(ChangeRequestEntity change, OffsetDateTime now) {
        change.approve();
        if (changeRequestRepository.update(change) == 0) {
            throw new BizException(ErrorCode.COMMON_409, "变更单已被并发修改，请重试");
        }
    }

    private void stale(ApprovalResult result, ChangeRequestEntity change) {
        auditRecorder.recordNonCritical(new AuditEvent(
                TraceIds.currentOrUntracked(),
                result.operatorId(),
                result.onBehalfOf(),
                AUDIT_STALE_APPROVAL,
                OBJECT_CHANGE,
                UuidUtils.parse(change.getId()),
                null,
                null,
                null,
                "审批版本不是当前版本，拒绝回调",
                null,
                null,
                clock.instant()));
    }

    private void audit(ApprovalResult result, String action, String objectType,
                       String objectId, Integer objectVersion, String reason) {
        auditRecorder.record(new AuditEvent(
                TraceIds.currentOrUntracked(),
                result.operatorId(),
                result.onBehalfOf(),
                action,
                objectType,
                UuidUtils.parse(objectId),
                objectVersion,
                null,
                null,
                reason,
                null,
                null,
                clock.instant()));
    }
}
