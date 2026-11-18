package cn.edu.rpm.project.infrastructure.entity;

import cn.edu.rpm.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDate;

/**
 * {@code change_request} 项目变更主档 PO（T-PRJ-007）。
 *
 * <p>变更类型取值以 V01 {@code ck_change_request_type} 为准（六类业务变更 + MILESTONE/OTHER）。
 * 08 §8 矩阵中的 {@code OBJECTIVE} 在本系统落为 DDL 允许的 {@code GOAL}（见 Batch-12 决策）。</p>
 */
@TableName("change_request")
public class ChangeRequestEntity extends BaseEntity {

    /** 变更类型：负责人。 */
    public static final String TYPE_PI = "PI";

    /** 变更类型：成员。 */
    public static final String TYPE_MEMBER = "MEMBER";

    /** 变更类型：周期。 */
    public static final String TYPE_PERIOD = "PERIOD";

    /** 变更类型：目标/里程碑（08 §8 OBJECTIVE）。 */
    public static final String TYPE_GOAL = "GOAL";

    /** 变更类型：里程碑（单独调整）。 */
    public static final String TYPE_MILESTONE = "MILESTONE";

    /** 变更类型：预算。 */
    public static final String TYPE_BUDGET = "BUDGET";

    /** 变更类型：合作单位。 */
    public static final String TYPE_PARTNER = "PARTNER";

    /** 变更类型：其他。 */
    public static final String TYPE_OTHER = "OTHER";

    /** 变更状态：草稿。 */
    public static final String STATUS_DRAFT = "DRAFT";

    /** 变更状态：审批中。 */
    public static final String STATUS_IN_REVIEW = "IN_REVIEW";

    /** 变更状态：已批准。 */
    public static final String STATUS_APPROVED = "APPROVED";

    /** 变更状态：已拒绝。 */
    public static final String STATUS_REJECTED = "REJECTED";

    /** 变更状态：已退回。 */
    public static final String STATUS_RETURNED = "RETURNED";

    /** 变更状态：已取消。 */
    public static final String STATUS_CANCELLED = "CANCELLED";

    /** 项目 ID（project.id）。 */
    @TableField("project_id")
    private String projectId;

    /** 变更类型：PI / MEMBER / PERIOD / GOAL / MILESTONE / BUDGET / PARTNER / OTHER。 */
    @TableField("change_type")
    private String changeType;

    /** 状态：DRAFT / IN_REVIEW / APPROVED / REJECTED / RETURNED / CANCELLED。 */
    @TableField("status")
    private String status;

    /** 当前生效版本 ID（change_request_version.id）。 */
    @TableField("current_version_id")
    private String currentVersionId;

    /** 生效日期。 */
    @TableField("effective_date")
    private LocalDate effectiveDate;

    /**
     * 提交审批：{@code DRAFT}/{@code RETURNED} → {@code IN_REVIEW}，绑定当前版本。
     *
     * @param versionId 变更版本 ID
     * @throws IllegalStateException 当前状态不允许提交
     */
    public void submitForReview(String versionId) {
        if (!STATUS_DRAFT.equals(status) && !STATUS_RETURNED.equals(status)) {
            throw new IllegalStateException("非法变更状态迁移：" + status + " → " + STATUS_IN_REVIEW);
        }
        this.status = STATUS_IN_REVIEW;
        this.currentVersionId = versionId;
    }

    /**
     * 批准变更：{@code IN_REVIEW} → {@code APPROVED}。
     *
     * @throws IllegalStateException 当前状态不是 IN_REVIEW
     */
    public void approve() {
        requireInReview(STATUS_APPROVED);
        this.status = STATUS_APPROVED;
    }

    /**
     * 拒绝变更：{@code IN_REVIEW} → {@code REJECTED}。
     *
     * @throws IllegalStateException 当前状态不是 IN_REVIEW
     */
    public void reject() {
        requireInReview(STATUS_REJECTED);
        this.status = STATUS_REJECTED;
    }

    /**
     * 退回变更：{@code IN_REVIEW} → {@code RETURNED}（可修改后重新提交）。
     *
     * @throws IllegalStateException 当前状态不是 IN_REVIEW
     */
    public void returned() {
        requireInReview(STATUS_RETURNED);
        this.status = STATUS_RETURNED;
    }

    /**
     * 取消变更（草稿或审批中）。
     */
    public void cancel() {
        if (!STATUS_DRAFT.equals(status) && !STATUS_IN_REVIEW.equals(status)) {
            throw new IllegalStateException("非法变更状态迁移：" + status + " → " + STATUS_CANCELLED);
        }
        this.status = STATUS_CANCELLED;
    }

    private void requireInReview(String target) {
        if (!STATUS_IN_REVIEW.equals(status)) {
            throw new IllegalStateException("非法变更状态迁移：" + status + " → " + target);
        }
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getChangeType() {
        return changeType;
    }

    public void setChangeType(String changeType) {
        this.changeType = changeType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCurrentVersionId() {
        return currentVersionId;
    }

    public void setCurrentVersionId(String currentVersionId) {
        this.currentVersionId = currentVersionId;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }

    public void setEffectiveDate(LocalDate effectiveDate) {
        this.effectiveDate = effectiveDate;
    }
}
