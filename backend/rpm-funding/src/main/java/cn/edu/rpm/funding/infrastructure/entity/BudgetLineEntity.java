package cn.edu.rpm.funding.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * {@code budget_line} 预算科目明细 PO（T-FUND-002；技术文档 02 §3.4）。
 *
 * <p>DDL 无 {@code created_by}/{@code updated_by} 列，不继承 {@code BaseEntity}。
 * 同一版本内 {@code (budget_version_id, subject_code)} 唯一
 * （{@code uk_budget_line_subject}）；金额 {@link BigDecimal} 原样保存。</p>
 */
@TableName("budget_line")
public class BudgetLineEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 控制级别：阻断。 */
    public static final String CONTROL_BLOCK = "BLOCK";

    /** 控制级别：告警。 */
    public static final String CONTROL_WARN = "WARN";

    /** 控制级别：需审批。 */
    public static final String CONTROL_APPROVAL = "APPROVAL";

    /** 主键；插入时由 MyBatis-Plus 生成 uuid。 */
    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    /** 创建时间；INSERT 时由审计填充器写入。 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    /** 最后更新时间；INSERT/UPDATE 时由审计填充器写入。 */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;

    /** 乐观锁版本号。 */
    @Version
    @TableField(value = "version")
    private Integer version = 0;

    /** 预算版本 ID（budget_version.id）。 */
    @TableField("budget_version_id")
    private String budgetVersionId;

    /** 科目编码（版本内唯一）。 */
    @TableField("subject_code")
    private String subjectCode;

    /** 科目名称。 */
    @TableField("subject_name")
    private String subjectName;

    /** 科目预算金额（正数）。 */
    @TableField("amount")
    private BigDecimal amount;

    /** 控制级别：BLOCK / WARN / APPROVAL。 */
    @TableField("control_level")
    private String controlLevel;

    /** 说明。 */
    @TableField("description")
    private String description;

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

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getBudgetVersionId() {
        return budgetVersionId;
    }

    public void setBudgetVersionId(String budgetVersionId) {
        this.budgetVersionId = budgetVersionId;
    }

    public String getSubjectCode() {
        return subjectCode;
    }

    public void setSubjectCode(String subjectCode) {
        this.subjectCode = subjectCode;
    }

    public String getSubjectName() {
        return subjectName;
    }

    public void setSubjectName(String subjectName) {
        this.subjectName = subjectName;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getControlLevel() {
        return controlLevel;
    }

    public void setControlLevel(String controlLevel) {
        this.controlLevel = controlLevel;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
