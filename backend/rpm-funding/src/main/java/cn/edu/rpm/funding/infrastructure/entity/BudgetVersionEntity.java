package cn.edu.rpm.funding.infrastructure.entity;

import cn.edu.rpm.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * {@code budget_version} 预算版本 PO（T-FUND-001；技术文档 02 §3.4）。
 *
 * <p>同一项目 {@code (project_id, version_no)} 唯一（{@code uk_budget_version_no}）；
 * 同一项目最多一个 {@code EFFECTIVE} 版本，由 T-FUND-017 的激活流程与数据库约束共同守护。
 * 金额统一 {@link BigDecimal}（{@code numeric(18,2)}）。</p>
 */
@TableName("budget_version")
public class BudgetVersionEntity extends BaseEntity {

    /** 状态：草稿。 */
    public static final String STATUS_DRAFT = "DRAFT";

    /** 状态：审批中。 */
    public static final String STATUS_IN_REVIEW = "IN_REVIEW";

    /** 状态：已生效。 */
    public static final String STATUS_EFFECTIVE = "EFFECTIVE";

    /** 状态：已被新版本替代。 */
    public static final String STATUS_SUPERSEDED = "SUPERSEDED";

    /** 状态：已拒绝。 */
    public static final String STATUS_REJECTED = "REJECTED";

    /** 来源：财政拨款。 */
    public static final String SOURCE_GRANT = "GRANT";

    /** 来源：合同经费。 */
    public static final String SOURCE_CONTRACT = "CONTRACT";

    /** 来源：配套经费。 */
    public static final String SOURCE_MATCHING = "MATCHING";

    /** 来源：预算调整。 */
    public static final String SOURCE_ADJUSTMENT = "ADJUSTMENT";

    /** 来源：历史数据迁移。 */
    public static final String SOURCE_MIGRATION = "MIGRATION";

    /** 控制级别：阻断。 */
    public static final String CONTROL_BLOCK = "BLOCK";

    /** 控制级别：告警。 */
    public static final String CONTROL_WARN = "WARN";

    /** 控制级别：需审批。 */
    public static final String CONTROL_APPROVAL = "APPROVAL";

    /** 项目 ID（project.id）。 */
    @TableField("project_id")
    private String projectId;

    /** 项目内版本号（从 1 递增，唯一）。 */
    @TableField("version_no")
    private Integer versionNo;

    /** 预算总额。 */
    @TableField("total_amount")
    private BigDecimal totalAmount;

    /** 来源类型：GRANT / CONTRACT / MATCHING / ADJUSTMENT / MIGRATION。 */
    @TableField("source_type")
    private String sourceType;

    /** 控制级别：BLOCK / WARN / APPROVAL。 */
    @TableField("control_level")
    private String controlLevel;

    /** 状态：DRAFT / IN_REVIEW / EFFECTIVE / SUPERSEDED / REJECTED。 */
    @TableField("status")
    private String status;

    /** 生效开始日期。 */
    @TableField("effective_from")
    private LocalDate effectiveFrom;

    /** 生效结束日期。 */
    @TableField("effective_to")
    private LocalDate effectiveTo;

    /** 关联变更申请 ID（budget 调整来源）。 */
    @TableField("change_request_id")
    private String changeRequestId;

    /** 批准人用户 ID。 */
    @TableField("approved_by")
    private String approvedBy;

    /** 批准时间。 */
    @TableField("approved_at")
    private OffsetDateTime approvedAt;

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public Integer getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(Integer versionNo) {
        this.versionNo = versionNo;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getControlLevel() {
        return controlLevel;
    }

    public void setControlLevel(String controlLevel) {
        this.controlLevel = controlLevel;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public void setEffectiveFrom(LocalDate effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    public LocalDate getEffectiveTo() {
        return effectiveTo;
    }

    public void setEffectiveTo(LocalDate effectiveTo) {
        this.effectiveTo = effectiveTo;
    }

    public String getChangeRequestId() {
        return changeRequestId;
    }

    public void setChangeRequestId(String changeRequestId) {
        this.changeRequestId = changeRequestId;
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(String approvedBy) {
        this.approvedBy = approvedBy;
    }

    public OffsetDateTime getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(OffsetDateTime approvedAt) {
        this.approvedAt = approvedAt;
    }
}
