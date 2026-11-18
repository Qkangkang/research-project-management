package cn.edu.rpm.project.infrastructure.entity;

import cn.edu.rpm.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDate;

/**
 * {@code milestone} 里程碑 PO（T-PRJ-004）。
 *
 * <p>{@code plan_date} 为原计划日期，<b>永不变更</b>；延期批准只更新
 * {@code current_plan_date}（08 §6.1）。完成判定必须有 {@code completion_criteria}
 * 与证据（TC-EXEC-002/003）。</p>
 */
@TableName("milestone")
public class MilestoneEntity extends BaseEntity {

    /** 里程碑状态：计划中。 */
    public static final String STATUS_PLANNED = "PLANNED";

    /** 里程碑状态：进行中。 */
    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";

    /** 里程碑状态：已完成。 */
    public static final String STATUS_COMPLETED = "COMPLETED";

    /** 里程碑状态：已延期。 */
    public static final String STATUS_DELAYED = "DELAYED";

    /** 里程碑状态：已取消。 */
    public static final String STATUS_CANCELLED = "CANCELLED";

    /** 项目 ID（project.id）。 */
    @TableField("project_id")
    private String projectId;

    /** 所属基线 ID（project_baseline.id）。 */
    @TableField("baseline_id")
    private String baselineId;

    /** 里程碑编码（基线内唯一，uk_milestone_baseline_code）。 */
    @TableField("code")
    private String code;

    /** 里程碑名称。 */
    @TableField("title")
    private String title;

    /** 原计划日期；永不变更。 */
    @TableField("plan_date")
    private LocalDate planDate;

    /** 当前计划日期；延期批准后更新。 */
    @TableField("current_plan_date")
    private LocalDate currentPlanDate;

    /** 实际完成日期。 */
    @TableField("actual_date")
    private LocalDate actualDate;

    /** 责任人用户 ID（iam_user.id）。 */
    @TableField("owner_id")
    private String ownerId;

    /** 完成判定标准；完成操作前必须非空。 */
    @TableField("completion_criteria")
    private String completionCriteria;

    /** 状态：PLANNED / IN_PROGRESS / COMPLETED / DELAYED / CANCELLED。 */
    @TableField("status")
    private String status;

    /** 延期审批实例 ID（wf_instance.id）。 */
    @TableField("delay_wf_instance_id")
    private String delayWfInstanceId;

    /**
     * 标记进行中：{@code PLANNED} → {@code IN_PROGRESS}。
     *
     * @throws IllegalStateException 当前状态不是 PLANNED
     */
    public void start() {
        if (!STATUS_PLANNED.equals(status)) {
            throw new IllegalStateException("非法里程碑状态迁移：" + status + " → " + STATUS_IN_PROGRESS);
        }
        this.status = STATUS_IN_PROGRESS;
    }

    /**
     * 标记完成：写入实际日期并置 {@code COMPLETED}。
     *
     * @param actualDate 实际完成日期
     * @throws IllegalStateException 当前状态为 COMPLETED/CANCELLED
     */
    public void complete(LocalDate actualDate) {
        if (STATUS_COMPLETED.equals(status) || STATUS_CANCELLED.equals(status)) {
            throw new IllegalStateException("非法里程碑状态迁移：" + status + " → " + STATUS_COMPLETED);
        }
        this.actualDate = actualDate;
        this.status = STATUS_COMPLETED;
    }

    /**
     * 应用已批准的延期：只更新 {@code current_plan_date}，原 {@code plan_date} 不变。
     *
     * @param newPlanDate 新计划日期
     * @throws IllegalStateException    已完成/已取消的里程碑不允许延期
     * @throws IllegalArgumentException 新日期不晚于当前计划日期
     */
    public void applyApprovedDelay(LocalDate newPlanDate) {
        if (STATUS_COMPLETED.equals(status) || STATUS_CANCELLED.equals(status)) {
            throw new IllegalStateException("非法里程碑状态迁移：" + status + " → " + STATUS_DELAYED);
        }
        if (newPlanDate == null || !newPlanDate.isAfter(currentPlanDate)) {
            throw new IllegalArgumentException("延期日期必须晚于当前计划日期");
        }
        this.currentPlanDate = newPlanDate;
        this.status = STATUS_DELAYED;
    }

    /**
     * 取消里程碑（基线变更等场景）。
     *
     * @throws IllegalStateException 已完成/已取消的里程碑不允许取消
     */
    public void cancel() {
        if (STATUS_COMPLETED.equals(status) || STATUS_CANCELLED.equals(status)) {
            throw new IllegalStateException("非法里程碑状态迁移：" + status + " → " + STATUS_CANCELLED);
        }
        this.status = STATUS_CANCELLED;
    }

    /**
     * 绑定延期审批实例。
     *
     * @param instanceId 审批实例 ID
     */
    public void linkDelayWorkflow(String instanceId) {
        this.delayWfInstanceId = instanceId;
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

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public LocalDate getPlanDate() {
        return planDate;
    }

    public void setPlanDate(LocalDate planDate) {
        this.planDate = planDate;
    }

    public LocalDate getCurrentPlanDate() {
        return currentPlanDate;
    }

    public void setCurrentPlanDate(LocalDate currentPlanDate) {
        this.currentPlanDate = currentPlanDate;
    }

    public LocalDate getActualDate() {
        return actualDate;
    }

    public void setActualDate(LocalDate actualDate) {
        this.actualDate = actualDate;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public String getCompletionCriteria() {
        return completionCriteria;
    }

    public void setCompletionCriteria(String completionCriteria) {
        this.completionCriteria = completionCriteria;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDelayWfInstanceId() {
        return delayWfInstanceId;
    }

    public void setDelayWfInstanceId(String delayWfInstanceId) {
        this.delayWfInstanceId = delayWfInstanceId;
    }
}
