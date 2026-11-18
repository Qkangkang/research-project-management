package cn.edu.rpm.project.infrastructure.entity;

import cn.edu.rpm.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * {@code project_handover} 负责人交接单 PO（T-PRJ-009；04 §3.10）。
 *
 * <p>{@code pending_items} 保存待转移事项清单（jsonb 文本数组）；
 * 审批通过前不得提前修改 {@code project.pi_id}。</p>
 */
@TableName("project_handover")
public class ProjectHandoverEntity extends BaseEntity {

    /** 交接状态：草稿。 */
    public static final String STATUS_DRAFT = "DRAFT";

    /** 交接状态：审批中。 */
    public static final String STATUS_IN_REVIEW = "IN_REVIEW";

    /** 交接状态：已批准（待生效）。 */
    public static final String STATUS_APPROVED = "APPROVED";

    /** 交接状态：已完成（生效）。 */
    public static final String STATUS_COMPLETED = "COMPLETED";

    /** 交接状态：已取消。 */
    public static final String STATUS_CANCELLED = "CANCELLED";

    /** 项目 ID（project.id）。 */
    @TableField("project_id")
    private String projectId;

    /** 原负责人用户 ID。 */
    @TableField("from_user_id")
    private String fromUserId;

    /** 新负责人用户 ID。 */
    @TableField("to_user_id")
    private String toUserId;

    /** 交接生效时间。 */
    @TableField("effective_at")
    private OffsetDateTime effectiveAt;

    /** 状态：DRAFT / IN_REVIEW / APPROVED / COMPLETED / CANCELLED。 */
    @TableField("status")
    private String status;

    /** 待转移事项清单（jsonb 文本数组）。 */
    @TableField("pending_items")
    private String pendingItems;

    /** 交接原因。 */
    @TableField("reason")
    private String reason;

    /** 完成时间。 */
    @TableField("completed_at")
    private OffsetDateTime completedAt;

    /**
     * 提交审批：{@code DRAFT} → {@code IN_REVIEW}。
     *
     * @throws IllegalStateException 当前状态不是 DRAFT
     */
    public void submitForReview() {
        if (!STATUS_DRAFT.equals(status)) {
            throw new IllegalStateException("非法交接状态迁移：" + status + " → " + STATUS_IN_REVIEW);
        }
        this.status = STATUS_IN_REVIEW;
    }

    /**
     * 批准交接：{@code IN_REVIEW} → {@code APPROVED}。
     *
     * @throws IllegalStateException 当前状态不是 IN_REVIEW
     */
    public void approve() {
        requireInReview(STATUS_APPROVED);
        this.status = STATUS_APPROVED;
    }

    /**
     * 完成交接：{@code APPROVED} → {@code COMPLETED}，写入完成时间。
     *
     * @param at 完成时间
     * @throws IllegalStateException 当前状态不是 APPROVED
     */
    public void complete(OffsetDateTime at) {
        if (!STATUS_APPROVED.equals(status)) {
            throw new IllegalStateException("非法交接状态迁移：" + status + " → " + STATUS_COMPLETED);
        }
        this.status = STATUS_COMPLETED;
        this.completedAt = at;
    }

    /**
     * 取消交接（草稿或审批中）。
     */
    public void cancel() {
        if (!STATUS_DRAFT.equals(status) && !STATUS_IN_REVIEW.equals(status)) {
            throw new IllegalStateException("非法交接状态迁移：" + status + " → " + STATUS_CANCELLED);
        }
        this.status = STATUS_CANCELLED;
    }

    private void requireInReview(String target) {
        if (!STATUS_IN_REVIEW.equals(status)) {
            throw new IllegalStateException("非法交接状态迁移：" + status + " → " + target);
        }
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getFromUserId() {
        return fromUserId;
    }

    public void setFromUserId(String fromUserId) {
        this.fromUserId = fromUserId;
    }

    public String getToUserId() {
        return toUserId;
    }

    public void setToUserId(String toUserId) {
        this.toUserId = toUserId;
    }

    public OffsetDateTime getEffectiveAt() {
        return effectiveAt;
    }

    public void setEffectiveAt(OffsetDateTime effectiveAt) {
        this.effectiveAt = effectiveAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPendingItems() {
        return pendingItems;
    }

    public void setPendingItems(String pendingItems) {
        this.pendingItems = pendingItems;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(OffsetDateTime completedAt) {
        this.completedAt = completedAt;
    }
}
