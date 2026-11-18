package cn.edu.rpm.project.infrastructure.entity;

import cn.edu.rpm.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * {@code milestone_delay_request} 里程碑延期申请 PO（T-PRJ-005；V06）。
 *
 * <p>{@code original_plan_date} 为申请时快照（不可变），批准后由领域服务在同一事务内
 * 更新 {@code milestone.current_plan_date}；原 {@code plan_date} 永不变更。</p>
 */
@TableName("milestone_delay_request")
public class MilestoneDelayRequestEntity extends BaseEntity {

    /** 申请状态：待审批。 */
    public static final String STATUS_PENDING = "PENDING";

    /** 申请状态：已批准。 */
    public static final String STATUS_APPROVED = "APPROVED";

    /** 申请状态：已拒绝。 */
    public static final String STATUS_REJECTED = "REJECTED";

    /** 申请状态：已撤回。 */
    public static final String STATUS_CANCELLED = "CANCELLED";

    /** 决策：批准。 */
    public static final String DECISION_APPROVE = "APPROVE";

    /** 决策：拒绝。 */
    public static final String DECISION_REJECT = "REJECT";

    /** 决策：撤回。 */
    public static final String DECISION_CANCEL = "CANCEL";

    /** 里程碑 ID（milestone.id）。 */
    @TableField("milestone_id")
    private String milestoneId;

    /** 项目 ID（project.id）。 */
    @TableField("project_id")
    private String projectId;

    /** 基线 ID（project_baseline.id）。 */
    @TableField("baseline_id")
    private String baselineId;

    /** 申请时原计划日期快照；不可变。 */
    @TableField("original_plan_date")
    private LocalDate originalPlanDate;

    /** 申请延期到的新日期。 */
    @TableField("requested_plan_date")
    private LocalDate requestedPlanDate;

    /** 延期天数（>0，ck_delay_days）。 */
    @TableField("delay_days")
    private Integer delayDays;

    /** 延期理由（≥10 字）。 */
    @TableField("reason")
    private String reason;

    /** 证据文件 ID 列表（jsonb 文本数组）。 */
    @TableField("evidence_file_ids")
    private String evidenceFileIds;

    /** 申请人用户 ID。 */
    @TableField("requested_by")
    private String requestedBy;

    /** 状态：PENDING / APPROVED / REJECTED / CANCELLED。 */
    @TableField("status")
    private String status;

    /** 审批实例 ID（wf_instance.id）。 */
    @TableField("wf_instance_id")
    private String wfInstanceId;

    /** 决策：APPROVE / REJECT / CANCEL。 */
    @TableField("decision")
    private String decision;

    /** 决策意见。 */
    @TableField("decision_comment")
    private String decisionComment;

    /** 决策人用户 ID。 */
    @TableField("decided_by")
    private String decidedBy;

    /** 决策时间。 */
    @TableField("decided_at")
    private OffsetDateTime decidedAt;

    /**
     * 批准延期申请。
     *
     * @param decidedBy 审批人用户 ID
     * @param comment   审批意见
     * @param at        决策时间
     * @throws IllegalStateException 当前状态不是 PENDING
     */
    public void approve(String decidedBy, String comment, OffsetDateTime at) {
        requirePending();
        this.status = STATUS_APPROVED;
        this.decision = DECISION_APPROVE;
        this.decisionComment = comment;
        this.decidedBy = decidedBy;
        this.decidedAt = at;
    }

    /**
     * 拒绝延期申请。
     *
     * @param decidedBy 审批人用户 ID
     * @param comment   审批意见
     * @param at        决策时间
     * @throws IllegalStateException 当前状态不是 PENDING
     */
    public void reject(String decidedBy, String comment, OffsetDateTime at) {
        requirePending();
        this.status = STATUS_REJECTED;
        this.decision = DECISION_REJECT;
        this.decisionComment = comment;
        this.decidedBy = decidedBy;
        this.decidedAt = at;
    }

    /**
     * 撤回延期申请（申请人，审批完成前）。
     *
     * @param at 撤回时间
     * @throws IllegalStateException 当前状态不是 PENDING
     */
    public void cancel(OffsetDateTime at) {
        requirePending();
        this.status = STATUS_CANCELLED;
        this.decision = DECISION_CANCEL;
        this.decidedAt = at;
    }

    /**
     * 绑定审批实例。
     *
     * @param instanceId 审批实例 ID
     */
    public void linkWorkflow(String instanceId) {
        this.wfInstanceId = instanceId;
    }

    private void requirePending() {
        if (!STATUS_PENDING.equals(status)) {
            throw new IllegalStateException("非法延期申请状态迁移：" + status);
        }
    }

    public String getMilestoneId() {
        return milestoneId;
    }

    public void setMilestoneId(String milestoneId) {
        this.milestoneId = milestoneId;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getBaselineId() {
        return baselineId;
    }

    public void setBaselineId(String baselineId) {
        this.baselineId = baselineId;
    }

    public LocalDate getOriginalPlanDate() {
        return originalPlanDate;
    }

    public void setOriginalPlanDate(LocalDate originalPlanDate) {
        this.originalPlanDate = originalPlanDate;
    }

    public LocalDate getRequestedPlanDate() {
        return requestedPlanDate;
    }

    public void setRequestedPlanDate(LocalDate requestedPlanDate) {
        this.requestedPlanDate = requestedPlanDate;
    }

    public Integer getDelayDays() {
        return delayDays;
    }

    public void setDelayDays(Integer delayDays) {
        this.delayDays = delayDays;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getEvidenceFileIds() {
        return evidenceFileIds;
    }

    public void setEvidenceFileIds(String evidenceFileIds) {
        this.evidenceFileIds = evidenceFileIds;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public void setRequestedBy(String requestedBy) {
        this.requestedBy = requestedBy;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getWfInstanceId() {
        return wfInstanceId;
    }

    public void setWfInstanceId(String wfInstanceId) {
        this.wfInstanceId = wfInstanceId;
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public String getDecisionComment() {
        return decisionComment;
    }

    public void setDecisionComment(String decisionComment) {
        this.decisionComment = decisionComment;
    }

    public String getDecidedBy() {
        return decidedBy;
    }

    public void setDecidedBy(String decidedBy) {
        this.decidedBy = decidedBy;
    }

    public OffsetDateTime getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(OffsetDateTime decidedAt) {
        this.decidedAt = decidedAt;
    }
}
