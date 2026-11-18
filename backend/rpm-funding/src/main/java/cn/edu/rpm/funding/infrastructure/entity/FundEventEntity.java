package cn.edu.rpm.funding.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * {@code fund_event} 资金事件 PO（T-FUND-006；技术文档 02 §3.4、§4.3）。
 *
 * <p>只追加不更新/删除（数据库不可变触发器兜底）；九类事件见 DDL 约束。
 * {@code idempotency_key} 全局唯一，重复投递返回原事件由应用服务判定。</p>
 */
@TableName("fund_event")
public class FundEventEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 事件：占用。 */
    public static final String TYPE_RESERVE = "RESERVE";

    /** 事件：释放占用。 */
    public static final String TYPE_RELEASE = "RELEASE";

    /** 事件：审批通过。 */
    public static final String TYPE_APPROVE = "APPROVE";

    /** 事件：全额支付。 */
    public static final String TYPE_PAY = "PAY";

    /** 事件：部分支付。 */
    public static final String TYPE_PARTIAL_PAY = "PARTIAL_PAY";

    /** 事件：取消。 */
    public static final String TYPE_CANCEL = "CANCEL";

    /** 事件：冲正。 */
    public static final String TYPE_REVERSE = "REVERSE";

    /** 事件：退款。 */
    public static final String TYPE_REFUND = "REFUND";

    /** 事件：预算调整。 */
    public static final String TYPE_ADJUST = "ADJUST";

    /** 主键；插入时由 MyBatis-Plus 生成 uuid。 */
    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    /** 创建时间；INSERT 时由审计填充器写入。 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    /** 创建人；INSERT 时由审计填充器写入。 */
    @TableField(value = "created_by", fill = FieldFill.INSERT)
    private String createdBy;

    /** 事件编号（uk_fund_event_no）。 */
    @TableField("event_no")
    private String eventNo;

    /** 项目 ID。 */
    @TableField("project_id")
    private String projectId;

    /** 关联支出 ID。 */
    @TableField("expense_request_id")
    private String expenseRequestId;

    /** 预算版本 ID。 */
    @TableField("budget_version_id")
    private String budgetVersionId;

    /** 预算科目 ID。 */
    @TableField("budget_line_id")
    private String budgetLineId;

    /** 事件类型。 */
    @TableField("event_type")
    private String eventType;

    /** 事件金额（正数）。 */
    @TableField("amount")
    private BigDecimal amount;

    /** 发生时间。 */
    @TableField("occurred_at")
    private OffsetDateTime occurredAt;

    /** 外部单号。 */
    @TableField("external_no")
    private String externalNo;

    /** 幂等键（uk_fund_event_idempotency）。 */
    @TableField("idempotency_key")
    private String idempotencyKey;

    /** 被冲正事件 ID。 */
    @TableField("reversal_of_event_id")
    private String reversalOfEventId;

    /** 事件载荷（jsonb）。 */
    @TableField("payload")
    private String payload;

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

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getEventNo() {
        return eventNo;
    }

    public void setEventNo(String eventNo) {
        this.eventNo = eventNo;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getExpenseRequestId() {
        return expenseRequestId;
    }

    public void setExpenseRequestId(String expenseRequestId) {
        this.expenseRequestId = expenseRequestId;
    }

    public String getBudgetVersionId() {
        return budgetVersionId;
    }

    public void setBudgetVersionId(String budgetVersionId) {
        this.budgetVersionId = budgetVersionId;
    }

    public String getBudgetLineId() {
        return budgetLineId;
    }

    public void setBudgetLineId(String budgetLineId) {
        this.budgetLineId = budgetLineId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(OffsetDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }

    public String getExternalNo() {
        return externalNo;
    }

    public void setExternalNo(String externalNo) {
        this.externalNo = externalNo;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getReversalOfEventId() {
        return reversalOfEventId;
    }

    public void setReversalOfEventId(String reversalOfEventId) {
        this.reversalOfEventId = reversalOfEventId;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }
}
