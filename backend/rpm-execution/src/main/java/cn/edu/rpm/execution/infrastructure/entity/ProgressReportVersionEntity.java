package cn.edu.rpm.execution.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * {@code progress_report_version} 报告版本 PO（T-EXEC-002）。
 *
 * <p>DDL 只有 {@code created_at}（版本行不可变），不继承 {@code BaseEntity}。
 * {@code content} 为 jsonb 文本；{@code content_hash} 为规范 JSON 的 SHA-256；
 * {@code late_flag} 是迟报事实的唯一来源（V05 修复 #23）。</p>
 */
@TableName("progress_report_version")
public class ProgressReportVersionEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 决策：待审核。 */
    public static final String DECISION_PENDING = "PENDING";

    /** 决策：通过。 */
    public static final String DECISION_PASS = "PASS";

    /** 决策：退回。 */
    public static final String DECISION_RETURN = "RETURN";

    /** 决策：拒绝。 */
    public static final String DECISION_REJECT = "REJECT";

    /** 主键；插入时由 MyBatis-Plus 生成 uuid。 */
    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    /** 创建时间；INSERT 时由审计填充器写入。 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    /** 报告主档 ID（progress_report.id）。 */
    @TableField("report_id")
    private String reportId;

    /** 报告内版本号（从 1 递增，uk_progress_report_version_no）。 */
    @TableField("version_no")
    private Integer versionNo;

    /** 报告内容（jsonb 文本）。 */
    @TableField("content")
    private String content;

    /** 内容哈希（SHA-256 小写十六进制）。 */
    @TableField("content_hash")
    private String contentHash;

    /** 提交人用户 ID。 */
    @TableField("submitted_by")
    private String submittedBy;

    /** 提交时间。 */
    @TableField("submitted_at")
    private OffsetDateTime submittedAt;

    /** 审核决策：PENDING / PASS / RETURN / REJECT。 */
    @TableField("decision")
    private String decision;

    /** 审核意见。 */
    @TableField("review_comment")
    private String reviewComment;

    /** 是否迟报；提交时按 due_date 判定。 */
    @TableField("late_flag")
    private Boolean lateFlag;

    /**
     * 写入审核决策。
     *
     * @param decision 决策（PASS / RETURN / REJECT）
     * @param comment  审核意见
     */
    public void decide(String decision, String comment) {
        this.decision = decision;
        this.reviewComment = comment;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getReportId() {
        return reportId;
    }

    public void setReportId(String reportId) {
        this.reportId = reportId;
    }

    public Integer getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(Integer versionNo) {
        this.versionNo = versionNo;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public String getSubmittedBy() {
        return submittedBy;
    }

    public void setSubmittedBy(String submittedBy) {
        this.submittedBy = submittedBy;
    }

    public OffsetDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(OffsetDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public String getReviewComment() {
        return reviewComment;
    }

    public void setReviewComment(String reviewComment) {
        this.reviewComment = reviewComment;
    }

    public Boolean getLateFlag() {
        return lateFlag;
    }

    public void setLateFlag(Boolean lateFlag) {
        this.lateFlag = lateFlag;
    }
}
