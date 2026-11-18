package cn.edu.rpm.project.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * {@code change_request_version} 变更版本 PO（T-PRJ-008）。
 *
 * <p>DDL 只有 {@code created_at}（版本行不可变，无 updated_at/操作人/version 列），
 * 因此不继承 {@code BaseEntity}，避免 MyBatis-Plus 向不存在的列写入审计字段。
 * {@code payload} 为按 {@code change_type} 受控反序列化后的 JSON 文本（jsonb），
 * 禁止自由 JSON 直接落库（08 §8.1）。</p>
 */
@TableName("change_request_version")
public class ChangeRequestVersionEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键；插入时由 MyBatis-Plus 生成 uuid。 */
    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    /** 创建时间；INSERT 时由审计填充器写入。 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    /** 变更单 ID（change_request.id）。 */
    @TableField("change_request_id")
    private String changeRequestId;

    /** 变更单内版本号（从 1 递增，uk_change_request_version_no）。 */
    @TableField("version_no")
    private Integer versionNo;

    /** 变更载荷（jsonb 文本；受控 discriminated union）。 */
    @TableField("payload")
    private String payload;

    /** 变更前基线 ID。 */
    @TableField("old_baseline_id")
    private String oldBaselineId;

    /** 变更后基线 ID（批准后回填）。 */
    @TableField("new_baseline_id")
    private String newBaselineId;

    /** 变更原因（≤2048 字符）。 */
    @TableField("reason")
    private String reason;

    /** 提交幂等键（uk_change_request_version_idem）。 */
    @TableField("submit_idempotency_key")
    private String submitIdempotencyKey;

    /**
     * 回填批准后生成的新基线。
     *
     * @param baselineId 新基线 ID
     */
    public void attachNewBaseline(String baselineId) {
        this.newBaselineId = baselineId;
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

    public String getChangeRequestId() {
        return changeRequestId;
    }

    public void setChangeRequestId(String changeRequestId) {
        this.changeRequestId = changeRequestId;
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

    public String getOldBaselineId() {
        return oldBaselineId;
    }

    public void setOldBaselineId(String oldBaselineId) {
        this.oldBaselineId = oldBaselineId;
    }

    public String getNewBaselineId() {
        return newBaselineId;
    }

    public void setNewBaselineId(String newBaselineId) {
        this.newBaselineId = newBaselineId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getSubmitIdempotencyKey() {
        return submitIdempotencyKey;
    }

    public void setSubmitIdempotencyKey(String submitIdempotencyKey) {
        this.submitIdempotencyKey = submitIdempotencyKey;
    }
}
