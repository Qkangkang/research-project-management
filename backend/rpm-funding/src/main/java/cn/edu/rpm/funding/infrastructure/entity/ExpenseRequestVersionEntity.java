package cn.edu.rpm.funding.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * {@code expense_request_version} 支出申请不可变版本 PO（T-FUND-005；技术文档 02 §3.4）。
 *
 * <p>只追加不更新；{@code (expense_request_id, version_no)} 唯一，
 * {@code content_hash} 为 64 位十六进制。</p>
 */
@TableName("expense_request_version")
public class ExpenseRequestVersionEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键；插入时由 MyBatis-Plus 生成 uuid。 */
    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    /** 创建时间；INSERT 时由审计填充器写入。 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    /** 支出申请 ID。 */
    @TableField("expense_request_id")
    private String expenseRequestId;

    /** 版本号（从 1 递增）。 */
    @TableField("version_no")
    private Integer versionNo;

    /** 规范化 JSON 载荷。 */
    @TableField("payload")
    private String payload;

    /** 内容 SHA-256。 */
    @TableField("content_hash")
    private String contentHash;

    /** 提交人。 */
    @TableField("submitted_by")
    private String submittedBy;

    /** 提交时间。 */
    @TableField("submitted_at")
    private OffsetDateTime submittedAt;

    /** 提交动作幂等键（V04 唯一索引 uk_expense_version_submit_idem，可空）。 */
    @TableField("submit_idempotency_key")
    private String submitIdempotencyKey;

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

    public String getExpenseRequestId() {
        return expenseRequestId;
    }

    public void setExpenseRequestId(String expenseRequestId) {
        this.expenseRequestId = expenseRequestId;
    }

    public Integer getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(Integer versionNo) {
        this.versionNo = versionNo;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
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

    public String getSubmitIdempotencyKey() {
        return submitIdempotencyKey;
    }

    public void setSubmitIdempotencyKey(String submitIdempotencyKey) {
        this.submitIdempotencyKey = submitIdempotencyKey;
    }
}
