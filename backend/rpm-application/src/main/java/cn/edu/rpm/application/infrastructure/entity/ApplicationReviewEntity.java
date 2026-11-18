package cn.edu.rpm.application.infrastructure.entity;

import cn.edu.rpm.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * {@code application_review} 形式/专家/决策意见 PO（T-APP-006）。
 *
 * <p>对应《02-数据模型与数据库设计.md》§3.2 与 V01 基线 DDL（V04 补齐审计列）。
 * 关键约束：</p>
 * <ul>
 *   <li>{@code idx_application_review_version_stage (application_version_id, review_stage)}：
 *       按版本与阶段读取审查意见；</li>
 *   <li>{@code ck_application_review_stage}：FORMAL / EXPERT / DECISION；</li>
 *   <li>{@code ck_application_review_decision}：PASS / RETURN / REJECT / NOT_ACCEPT / APPROVE。</li>
 * </ul>
 *
 * <p>{@code issues} 为问题项数组的 jsonb 文本（形式审查退回时记录定位信息）；
 * 审查意见只追加不覆盖，审计字段由 {@link BaseEntity} 承载。</p>
 */
@TableName("application_review")
public class ApplicationReviewEntity extends BaseEntity {

    /** 审查阶段：形式审查。 */
    public static final String STAGE_FORMAL = "FORMAL";

    /** 审查阶段：专家评审。 */
    public static final String STAGE_EXPERT = "EXPERT";

    /** 审查阶段：立项决策。 */
    public static final String STAGE_DECISION = "DECISION";

    /** 审查结论：通过。 */
    public static final String DECISION_PASS = "PASS";

    /** 审查结论：退回补正。 */
    public static final String DECISION_RETURN = "RETURN";

    /** 审查结论：立项不批准。 */
    public static final String DECISION_REJECT = "REJECT";

    /** 审查结论：不予受理（终态 NOT_ACCEPTED）。 */
    public static final String DECISION_NOT_ACCEPT = "NOT_ACCEPT";

    /** 审查结论：批准立项。 */
    public static final String DECISION_APPROVE = "APPROVE";

    /** 被审查的申报版本 ID（application_version.id）。 */
    @TableField("application_version_id")
    private String applicationVersionId;

    /** 审查阶段：FORMAL / EXPERT / DECISION（ck_application_review_stage）。 */
    @TableField("review_stage")
    private String reviewStage;

    /** 评审人用户 ID（iam_user.id）。 */
    @TableField("reviewer_id")
    private String reviewerId;

    /** 审查结论：PASS / RETURN / REJECT / NOT_ACCEPT / APPROVE（ck_application_review_decision）。 */
    @TableField("decision")
    private String decision;

    /** 问题项数组（jsonb 文本，默认 []）。 */
    @TableField("issues")
    private String issues;

    /** 审查评语；可空（NOT_ACCEPT 场景要求 ≥10 字，由应用服务校验）。 */
    @TableField("comment")
    private String comment;

    /** 审查时刻（默认 now()）。 */
    @TableField("reviewed_at")
    private OffsetDateTime reviewedAt;

    public String getApplicationVersionId() {
        return applicationVersionId;
    }

    public void setApplicationVersionId(String applicationVersionId) {
        this.applicationVersionId = applicationVersionId;
    }

    public String getReviewStage() {
        return reviewStage;
    }

    public void setReviewStage(String reviewStage) {
        this.reviewStage = reviewStage;
    }

    public String getReviewerId() {
        return reviewerId;
    }

    public void setReviewerId(String reviewerId) {
        this.reviewerId = reviewerId;
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public String getIssues() {
        return issues;
    }

    public void setIssues(String issues) {
        this.issues = issues;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public OffsetDateTime getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(OffsetDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }
}
