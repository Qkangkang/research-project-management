package cn.edu.rpm.execution.infrastructure.entity;

import cn.edu.rpm.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * {@code project_task} 轻量任务 PO（T-EXEC-004；V07）。
 *
 * <p>无任务依赖；成员仅可更新被授权任务（09 §4）。状态迁移通过显式方法。</p>
 */
@TableName("project_task")
public class ProjectTaskEntity extends BaseEntity {

    /** 状态：待办。 */
    public static final String STATUS_TODO = "TODO";

    /** 状态：进行中。 */
    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";

    /** 状态：已完成。 */
    public static final String STATUS_DONE = "DONE";

    /** 状态：已取消。 */
    public static final String STATUS_CANCELLED = "CANCELLED";

    /** 项目 ID（project.id）。 */
    @TableField("project_id")
    private String projectId;

    /** 任务标题（≤256 字符）。 */
    @TableField("title")
    private String title;

    /** 任务描述（≤2000 字符）。 */
    @TableField("description")
    private String description;

    /** 处理人用户 ID。 */
    @TableField("assignee_id")
    private String assigneeId;

    /** 截止日期。 */
    @TableField("due_date")
    private LocalDate dueDate;

    /** 状态：TODO / IN_PROGRESS / DONE / CANCELLED。 */
    @TableField("status")
    private String status;

    /** 关联交付物 ID（deliverable.id）；可空。 */
    @TableField("deliverable_id")
    private String deliverableId;

    /** 完成时间。 */
    @TableField("completed_at")
    private OffsetDateTime completedAt;

    /**
     * 开始任务：{@code TODO} → {@code IN_PROGRESS}。
     *
     * @throws IllegalStateException 当前状态不是 TODO
     */
    public void start() {
        if (!STATUS_TODO.equals(status)) {
            throw new IllegalStateException("非法任务状态迁移：" + status + " → " + STATUS_IN_PROGRESS);
        }
        this.status = STATUS_IN_PROGRESS;
    }

    /**
     * 完成任务：{@code TODO}/{@code IN_PROGRESS} → {@code DONE}。
     *
     * @param at 完成时间
     * @throws IllegalStateException 当前状态为 DONE/CANCELLED
     */
    public void complete(OffsetDateTime at) {
        if (STATUS_DONE.equals(status) || STATUS_CANCELLED.equals(status)) {
            throw new IllegalStateException("非法任务状态迁移：" + status + " → " + STATUS_DONE);
        }
        this.status = STATUS_DONE;
        this.completedAt = at;
    }

    /**
     * 取消任务：{@code TODO}/{@code IN_PROGRESS} → {@code CANCELLED}。
     *
     * @throws IllegalStateException 当前状态为 DONE/CANCELLED
     */
    public void cancel() {
        if (STATUS_DONE.equals(status) || STATUS_CANCELLED.equals(status)) {
            throw new IllegalStateException("非法任务状态迁移：" + status + " → " + STATUS_CANCELLED);
        }
        this.status = STATUS_CANCELLED;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getAssigneeId() {
        return assigneeId;
    }

    public void setAssigneeId(String assigneeId) {
        this.assigneeId = assigneeId;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDeliverableId() {
        return deliverableId;
    }

    public void setDeliverableId(String deliverableId) {
        this.deliverableId = deliverableId;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(OffsetDateTime completedAt) {
        this.completedAt = completedAt;
    }
}
