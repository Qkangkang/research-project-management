package cn.edu.rpm.output.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * {@code output_duplicate_candidate} 疑似重复候选 PO（T-OUT-006）。
 *
 * <p>对应《02-数据模型与数据库设计.md》§3.5 与 V01 基线 DDL。关键约束：</p>
 * <ul>
 *   <li>{@code uk_output_duplicate (output_id, candidate_output_id, match_type)}：
 *       同一对成果同一匹配类型只保留一条候选；</li>
 *   <li>{@code ck_output_duplicate_self}：成果不能与自身构成候选；</li>
 *   <li>{@code ck_output_duplicate_type}：DOI / PATENT / TITLE / OWNER；</li>
 *   <li>{@code ck_output_duplicate_score}：0～1；低于确认阈值仅为提示，不自动合并（BR-21）。</li>
 * </ul>
 */
@TableName("output_duplicate_candidate")
public class OutputDuplicateCandidateEntity {

    /** 匹配类型：DOI 完全相同。 */
    public static final String MATCH_DOI = "DOI";

    /** 匹配类型：专利号完全相同。 */
    public static final String MATCH_PATENT = "PATENT";

    /** 匹配类型：名称完全相同（完成人或 DOI 不同为低分候选）。 */
    public static final String MATCH_TITLE = "TITLE";

    /** 匹配类型：完成人与名称组合相同。 */
    public static final String MATCH_OWNER = "OWNER";

    /** 候选状态：待确认。 */
    public static final String STATUS_OPEN = "OPEN";

    /** 候选状态：已确认重复。 */
    public static final String STATUS_CONFIRMED = "CONFIRMED";

    /** 候选状态：已排除。 */
    public static final String STATUS_DISMISSED = "DISMISSED";

    /** 主键；插入时由 MyBatis-Plus 生成 32 位 uuid 文本。 */
    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    /** 当前成果 ID（research_output.id，ON DELETE CASCADE）。 */
    @TableField("output_id")
    private String outputId;

    /** 候选成果 ID（research_output.id，ON DELETE CASCADE）。 */
    @TableField("candidate_output_id")
    private String candidateOutputId;

    /** 匹配类型（ck_output_duplicate_type）。 */
    @TableField("match_type")
    private String matchType;

    /** 匹配得分 0～1；低于阈值仅为提示。 */
    @TableField("score")
    private BigDecimal score;

    /** 候选状态：OPEN / CONFIRMED / DISMISSED。 */
    @TableField("status")
    private String status;

    /** 确认/排除人用户 ID。 */
    @TableField("reviewed_by")
    private String reviewedBy;

    /** 确认/排除时间。 */
    @TableField("reviewed_at")
    private OffsetDateTime reviewedAt;

    /** 创建时间。 */
    @TableField("created_at")
    private OffsetDateTime createdAt;

    /**
     * 构造待确认候选。
     *
     * @param outputId          当前成果 ID（32 位 uuid 文本）
     * @param candidateOutputId 候选成果 ID（32 位 uuid 文本）
     * @param matchType         匹配类型
     * @param score             匹配得分（0～1）
     * @param createdAt         创建时间
     * @return 候选 PO
     */
    public static OutputDuplicateCandidateEntity create(String outputId, String candidateOutputId,
                                                        String matchType, BigDecimal score,
                                                        OffsetDateTime createdAt) {
        OutputDuplicateCandidateEntity candidate = new OutputDuplicateCandidateEntity();
        candidate.id = UUID.randomUUID().toString().replace("-", "");
        candidate.outputId = requireText(outputId, "outputId");
        candidate.candidateOutputId = requireText(candidateOutputId, "candidateOutputId");
        if (candidate.outputId.equals(candidate.candidateOutputId)) {
            throw new IllegalArgumentException("成果不能与自身构成重复候选");
        }
        candidate.matchType = requireText(matchType, "matchType");
        if (score == null || score.compareTo(BigDecimal.ZERO) < 0 || score.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("score 必须在 0～1 之间");
        }
        candidate.score = score;
        candidate.status = STATUS_OPEN;
        candidate.createdAt = Objects.requireNonNull(createdAt, "createdAt 不能为空");
        return candidate;
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

    public String getOutputId() {
        return outputId;
    }

    public void setOutputId(String outputId) {
        this.outputId = outputId;
    }

    public String getCandidateOutputId() {
        return candidateOutputId;
    }

    public void setCandidateOutputId(String candidateOutputId) {
        this.candidateOutputId = candidateOutputId;
    }

    public String getMatchType() {
        return matchType;
    }

    public void setMatchType(String matchType) {
        this.matchType = matchType;
    }

    public BigDecimal getScore() {
        return score;
    }

    public void setScore(BigDecimal score) {
        this.score = score;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getReviewedBy() {
        return reviewedBy;
    }

    public void setReviewedBy(String reviewedBy) {
        this.reviewedBy = reviewedBy;
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
