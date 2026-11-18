package cn.edu.rpm.output.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * {@code output_review} 成果审核记录 PO（T-OUT-004）。
 *
 * <p>对应《02-数据模型与数据库设计.md》§3.5 与 V01 基线 DDL。关键约束：</p>
 * <ul>
 *   <li>{@code ck_output_review_decision}：APPROVED / RETURNED / REJECTED；</li>
 *   <li>{@code idx_output_review_version}：按版本查询审核意见；</li>
 *   <li>审核绑定固定 {@code output_version_id}，历史审核意见不随修订变化。</li>
 * </ul>
 */
@TableName("output_review")
public class OutputReviewEntity {

    /** 审核结论：通过。 */
    public static final String DECISION_APPROVED = "APPROVED";

    /** 审核结论：退回修订。 */
    public static final String DECISION_RETURNED = "RETURNED";

    /** 审核结论：拒绝。 */
    public static final String DECISION_REJECTED = "REJECTED";

    /** 主键；插入时由 MyBatis-Plus 生成 32 位 uuid 文本。 */
    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    /** 所属成果版本 ID（output_version.id，ON DELETE RESTRICT）。 */
    @TableField("output_version_id")
    private String outputVersionId;

    /** 审核人用户 ID（iam_user.id）。 */
    @TableField("reviewer_id")
    private String reviewerId;

    /** 审核结论（ck_output_review_decision）。 */
    @TableField("decision")
    private String decision;

    /** 审核意见（退回/拒绝必填）。 */
    @TableField("comment")
    private String comment;

    /** 审核时刻。 */
    @TableField("reviewed_at")
    private OffsetDateTime reviewedAt;

    /** 创建时间。 */
    @TableField("created_at")
    private OffsetDateTime createdAt;

    /**
     * 构造审核记录。
     *
     * @param outputVersionId 成果版本 ID（32 位 uuid 文本）
     * @param reviewerId      审核人用户 ID（32 位 uuid 文本）
     * @param decision        审核结论
     * @param comment         审核意见，可空（通过时可空）
     * @param reviewedAt      审核时刻
     * @return 审核记录 PO
     */
    public static OutputReviewEntity create(String outputVersionId, String reviewerId,
                                            String decision, String comment,
                                            OffsetDateTime reviewedAt) {
        OutputReviewEntity review = new OutputReviewEntity();
        review.id = UUID.randomUUID().toString().replace("-", "");
        review.outputVersionId = requireText(outputVersionId, "outputVersionId");
        review.reviewerId = requireText(reviewerId, "reviewerId");
        review.decision = requireText(decision, "decision");
        review.comment = comment;
        review.reviewedAt = Objects.requireNonNull(reviewedAt, "reviewedAt 不能为空");
        review.createdAt = reviewedAt;
        return review;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getOutputVersionId() {
        return outputVersionId;
    }

    public void setOutputVersionId(String outputVersionId) {
        this.outputVersionId = outputVersionId;
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

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
