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
import cn.edu.rpm.project.domain.port.HandoverTaskTransferPort;
import cn.edu.rpm.project.infrastructure.entity.ProjectEntity;
import cn.edu.rpm.project.infrastructure.entity.ProjectHandoverEntity;
import cn.edu.rpm.project.infrastructure.entity.ProjectMemberEntity;
import cn.edu.rpm.project.infrastructure.repository.ProjectHandoverRepository;
import cn.edu.rpm.project.infrastructure.repository.ProjectMemberRepository;
import cn.edu.rpm.project.infrastructure.repository.ProjectRepository;
import cn.edu.rpm.project.infrastructure.repository.ProjectRiskRepository;
import cn.edu.rpm.workflow.domain.dto.ApprovalResult;
import cn.edu.rpm.workflow.domain.port.ApprovalResultHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 负责人交接审批回调（T-PRJ-022；技术文档 04 §3.10、07 §3.4）。
 *
 * <p>批准时若 {@code effective_at} 已到，立即完成交接；否则交接单置
 * {@code APPROVED}，由 {@link #applyDue(OffsetDateTime)} 在生效时刻惰性/定时完成。</p>
 *
 * <p>完成动作：锁定项目 → 切换 {@code pi_id} → 成员角色切换 → 未结风险责任人转移
 * → 通过 {@link HandoverTaskTransferPort} 转移待办/任务 → 交接单置
 * {@code COMPLETED}。历史审计与 {@code created_by} 不改写（TC-PRJ-029）。</p>
 */
@Component
public class HandoverApprovalHandler implements ApprovalResultHandler {

    private static final Logger LOG = LoggerFactory.getLogger(HandoverApprovalHandler.class);

    /** 审计动作：交接批准（未到生效时刻）。 */
    public static final String AUDIT_HANDOVER_APPROVED = "PROJECT_HANDOVER_APPROVED";

    /** 审计动作：交接完成。 */
    public static final String AUDIT_HANDOVER_COMPLETED = "PROJECT_HANDOVER_COMPLETED";

    /** 审计动作：交接取消。 */
    public static final String AUDIT_HANDOVER_CANCELLED = "PROJECT_HANDOVER_CANCELLED";

    /** 审计动作：审批版本过期。 */
    public static final String AUDIT_STALE_APPROVAL = "STALE_APPROVAL";

    /** 审计对象类型：负责人交接单。 */
    static final String OBJECT_HANDOVER = "PROJECT_HANDOVER";

    /** 事件类型：交接完成。 */
    public static final String EVENT_HANDOVER_COMPLETED = "PROJECT_HANDOVER_COMPLETED";

    private final ProjectHandoverRepository handoverRepository;

    private final ProjectRepository projectRepository;

    private final ProjectMemberRepository projectMemberRepository;

    private final ProjectRiskRepository projectRiskRepository;

    private final List<HandoverTaskTransferPort> taskTransferPorts;

    private final OutboxAppendPort outboxAppendPort;

    private final AuditRecorder auditRecorder;

    private final CanonicalJsonHasher jsonHasher;

    private final Clock clock;

    /**
     * 构造交接审批回调。
     *
     * @param handoverRepository      交接仓储
     * @param projectRepository       项目仓储
     * @param projectMemberRepository 成员仓储
     * @param projectRiskRepository   风险仓储
     * @param taskTransferPorts       待办/任务转移端口
     * @param outboxAppendPort        Outbox 追加端口
     * @param auditRecorder           审计记录器
     * @param jsonHasher              规范化 JSON 工具（清单摘要）
     * @param clock                   时间基准
     */
    public HandoverApprovalHandler(ProjectHandoverRepository handoverRepository,
                                   ProjectRepository projectRepository,
                                   ProjectMemberRepository projectMemberRepository,
                                   ProjectRiskRepository projectRiskRepository,
                                   List<HandoverTaskTransferPort> taskTransferPorts,
                                   OutboxAppendPort outboxAppendPort,
                                   AuditRecorder auditRecorder,
                                   CanonicalJsonHasher jsonHasher,
                                   Clock clock) {
        this.handoverRepository = Objects.requireNonNull(handoverRepository,
                "handoverRepository 不能为空");
        this.projectRepository = Objects.requireNonNull(projectRepository, "projectRepository 不能为空");
        this.projectMemberRepository = Objects.requireNonNull(projectMemberRepository,
                "projectMemberRepository 不能为空");
        this.projectRiskRepository = Objects.requireNonNull(projectRiskRepository,
                "projectRiskRepository 不能为空");
        this.taskTransferPorts = taskTransferPorts == null ? List.of() : List.copyOf(taskTransferPorts);
        this.outboxAppendPort = Objects.requireNonNull(outboxAppendPort, "outboxAppendPort 不能为空");
        this.auditRecorder = Objects.requireNonNull(auditRecorder, "auditRecorder 不能为空");
        this.jsonHasher = Objects.requireNonNull(jsonHasher, "jsonHasher 不能为空");
        this.clock = Objects.requireNonNull(clock, "clock 不能为空");
    }

    @Override
    public String bizType() {
        return "HANDOVER";
    }

    /**
     * 交接批准（TC-PRJ-026 / TC-PRJ-027）。
     *
     * @param result 审批结果
     * @throws BizException 交接单不存在 {@code RPM-COMMON-404}；版本过期 {@code RPM-WF-005}；
     *                      并发冲突 {@code RPM-COMMON-409}
     */
    @Override
    @Transactional
    public void onApproved(ApprovalResult result) {
        ProjectHandoverEntity handover = lockHandover(result);
        if (ProjectHandoverEntity.STATUS_COMPLETED.equals(handover.getStatus())) {
            LOG.info("交接已完成，幂等跳过 handoverId={}", handover.getId());
            return;
        }
        if (!ProjectHandoverEntity.STATUS_IN_REVIEW.equals(handover.getStatus())
                && !ProjectHandoverEntity.STATUS_APPROVED.equals(handover.getStatus())) {
            throw new BizException(ErrorCode.COMMON_422,
                    "交接单状态不允许批准：" + handover.getStatus());
        }
        if (!handover.getEffectiveAt().isAfter(result.occurredAt())) {
            complete(handover, result.operatorId(), result.occurredAt());
            return;
        }
        handover.approve();
        if (handoverRepository.update(handover) == 0) {
            throw new BizException(ErrorCode.COMMON_409, "交接单已被并发修改，请重试");
        }
        audit(result.operatorId(), result.onBehalfOf(), AUDIT_HANDOVER_APPROVED,
                handover.getId(), "交接批准，待生效");
    }

    /**
     * 惰性/定时生效：完成所有已到生效时刻的 {@code APPROVED} 交接单。
     *
     * @param at 判定时刻
     * @return 本次完成的交接单数量
     */
    @Transactional
    public int applyDue(OffsetDateTime at) {
        Objects.requireNonNull(at, "at 不能为空");
        List<ProjectHandoverEntity> due = handoverRepository
                .findByStatusAndEffectiveAtBefore(ProjectHandoverEntity.STATUS_APPROVED, at);
        int completed = 0;
        for (ProjectHandoverEntity handover : due) {
            complete(handover, null, at);
            completed++;
        }
        return completed;
    }

    @Override
    @Transactional
    public void onRejected(ApprovalResult result) {
        cancel(result, "交接被拒绝");
    }

    @Override
    @Transactional
    public void onReturned(ApprovalResult result) {
        cancel(result, "交接被退回");
    }

    @Override
    @Transactional
    public void onWithdrawn(ApprovalResult result) {
        cancel(result, "交接审批撤回");
    }

    private void cancel(ApprovalResult result, String reason) {
        ProjectHandoverEntity handover = lockHandover(result);
        if (ProjectHandoverEntity.STATUS_CANCELLED.equals(handover.getStatus())) {
            return;
        }
        handover.cancel();
        if (handoverRepository.update(handover) == 0) {
            throw new BizException(ErrorCode.COMMON_409, "交接单已被并发修改，请重试");
        }
        audit(result.operatorId(), result.onBehalfOf(), AUDIT_HANDOVER_CANCELLED,
                handover.getId(), reason);
    }

    private ProjectHandoverEntity lockHandover(ApprovalResult result) {
        String handoverId = UuidUtils.formatCompact(result.bizId());
        ProjectHandoverEntity handover = handoverRepository.findById(handoverId)
                .orElseThrow(() -> new BizException(ErrorCode.COMMON_404, "交接单不存在"));
        if (!UuidUtils.sameId(handoverId, UuidUtils.formatCompact(result.bizVersionId()))) {
            auditRecorder.recordNonCritical(new AuditEvent(
                    TraceIds.currentOrUntracked(),
                    result.operatorId(),
                    result.onBehalfOf(),
                    AUDIT_STALE_APPROVAL,
                    OBJECT_HANDOVER,
                    UuidUtils.parse(handoverId),
                    null,
                    null,
                    null,
                    "交接审批版本已过期",
                    null,
                    null,
                    clock.instant()));
            throw new BizException(ErrorCode.WF_005, "交接审批版本已过期");
        }
        return handover;
    }

    private void complete(ProjectHandoverEntity handover, UUID operatorId, OffsetDateTime at) {
        String fromUserId = handover.getFromUserId();
        String toUserId = handover.getToUserId();
        ProjectEntity project = projectRepository.selectForUpdate(handover.getProjectId())
                .orElseThrow(() -> new BizException(ErrorCode.PROJ_001));

        if (UuidUtils.sameId(project.getPiId(), fromUserId)) {
            project.handoverTo(toUserId);
            if (projectRepository.update(project) == 0) {
                throw new BizException(ErrorCode.COMMON_409, "项目已被并发修改，请重试");
            }
            switchPiMembership(project.getId(), fromUserId, toUserId, at);
            projectRiskRepository.transferOwner(project.getId(), fromUserId, toUserId);
            taskTransferPorts.forEach(port ->
                    port.transferTasksAndTodos(project.getId(), fromUserId, toUserId));
        } else if (!UuidUtils.sameId(project.getPiId(), toUserId)) {
            throw new BizException(ErrorCode.WF_005, "项目负责人已变化，交接审批版本过期");
        }

        if (ProjectHandoverEntity.STATUS_IN_REVIEW.equals(handover.getStatus())) {
            handover.approve();
        }
        handover.complete(at);
        if (handoverRepository.update(handover) == 0) {
            throw new BizException(ErrorCode.COMMON_409, "交接单已被并发修改，请重试");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("project_id", project.getId());
        payload.put("handover_id", handover.getId());
        payload.put("from_user_id", fromUserId);
        payload.put("to_user_id", toUserId);
        payload.put("pending_items_hash",
                jsonHasher.sha256(handover.getPendingItems() == null ? "[]"
                        : handover.getPendingItems()));
        outboxAppendPort.append(new OutboxEvent(UUID.randomUUID(), OBJECT_HANDOVER,
                UuidUtils.parse(handover.getId()), EVENT_HANDOVER_COMPLETED, payload,
                TraceIds.currentOrUntracked(),
                EVENT_HANDOVER_COMPLETED + ":" + handover.getId()));
        audit(operatorId, null, AUDIT_HANDOVER_COMPLETED, handover.getId(), "负责人交接完成");
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

    private void audit(UUID operatorId, UUID onBehalfOf, String action, String objectId,
                       String reason) {
        auditRecorder.record(new AuditEvent(
                TraceIds.currentOrUntracked(),
                operatorId,
                onBehalfOf,
                action,
                OBJECT_HANDOVER,
                UuidUtils.parse(objectId),
                null,
                null,
                null,
                reason,
                null,
                null,
                clock.instant()));
    }
}
