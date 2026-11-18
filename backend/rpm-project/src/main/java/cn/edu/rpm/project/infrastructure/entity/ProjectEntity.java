package cn.edu.rpm.project.infrastructure.entity;

import cn.edu.rpm.common.entity.BaseEntity;
import cn.edu.rpm.common.util.UuidUtils;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * {@code project} 项目主档 PO（T-PRJ-001）。
 *
 * <p>对应《02-数据模型与数据库设计.md》§3.3 与 V01 基线 DDL。关键约束：</p>
 * <ul>
 *   <li>{@code uk_project_no (project_no)}：项目编号全局唯一，由 {@code ProjectNumberGenerator} 生成；</li>
 *   <li>{@code uk_project_source_version (source_application_version_id)}：立项幂等键，
 *       同一批准版本只能生成一个项目（TC-APP-029）；</li>
 *   <li>{@code ck_project_status}：六态状态机（技术文档 01 §2.4.2）。</li>
 * </ul>
 *
 * <p>状态迁移只允许通过本类显式方法（禁止业务代码裸调 {@link #setStatus(String)}）；
 * 非法迁移抛 {@link IllegalStateException}，由应用服务转换为 {@code RPM-PROJ-002}。</p>
 */
@TableName("project")
public class ProjectEntity extends BaseEntity {

    /** 待启动（立项生成后的初始状态）。 */
    public static final String STATUS_PENDING_START = "PENDING_START";

    /** 执行中。 */
    public static final String STATUS_EXECUTING = "EXECUTING";

    /** 暂停中。 */
    public static final String STATUS_SUSPENDED = "SUSPENDED";

    /** 验收中。 */
    public static final String STATUS_ACCEPTING = "ACCEPTING";

    /** 已结题（终态）。 */
    public static final String STATUS_COMPLETED = "COMPLETED";

    /** 已终止（终态）。 */
    public static final String STATUS_TERMINATED = "TERMINATED";

    /** 来源类型：院内申报。 */
    public static final String SOURCE_INTERNAL = "INTERNAL";

    /** 来源类型：外部项目登记。 */
    public static final String SOURCE_EXTERNAL = "EXTERNAL";

    /** 项目编号；全局唯一（uk_project_no），如 KY-2026-000456。 */
    @TableField("project_no")
    private String projectNo;

    /** 项目名称（1～512 字符）。 */
    @TableField("title")
    private String title;

    /** 项目类型（如 YEAR_PLAN）。 */
    @TableField("project_type")
    private String projectType;

    /** 所属部门（org_unit.id）。 */
    @TableField("dept_id")
    private String deptId;

    /** 项目负责人（iam_user.id）。 */
    @TableField("pi_id")
    private String piId;

    /** 项目状态：PENDING_START / EXECUTING / SUSPENDED / ACCEPTING / COMPLETED / TERMINATED。 */
    @TableField("status")
    private String status;

    /** 来源类型：INTERNAL / EXTERNAL（ck_project_source）。 */
    @TableField("source_type")
    private String sourceType;

    /** 来源申报版本 ID；立项幂等键（uk_project_source_version）。 */
    @TableField("source_application_version_id")
    private String sourceApplicationVersionId;

    /** 当前生效基线 ID（project_baseline.id）；立项生成基线后回填。 */
    @TableField("current_baseline_id")
    private String currentBaselineId;

    /** 批准时间。 */
    @TableField("approved_at")
    private OffsetDateTime approvedAt;

    /** 项目开始日期。 */
    @TableField("start_date")
    private LocalDate startDate;

    /** 项目结束日期。 */
    @TableField("end_date")
    private LocalDate endDate;

    /** 财务项目号（外部系统）。 */
    @TableField("financial_project_no")
    private String financialProjectNo;

    /** 外部批复号。 */
    @TableField("external_approval_no")
    private String externalApprovalNo;

    /** 外部合同号。 */
    @TableField("external_contract_no")
    private String externalContractNo;

    /** 冻结时间（结题后写入，TC-PRJ-032）。 */
    @TableField("frozen_at")
    private OffsetDateTime frozenAt;

    /** 冻结原因。 */
    @TableField("frozen_reason")
    private String frozenReason;

    /**
     * 启动项目：{@code PENDING_START} → {@code EXECUTING}。
     *
     * @throws IllegalStateException 当前状态不是 PENDING_START
     */
    public void start() {
        requireStatus("启动", STATUS_PENDING_START);
        this.status = STATUS_EXECUTING;
    }

    /**
     * 暂停项目：{@code EXECUTING} → {@code SUSPENDED}。
     *
     * @throws IllegalStateException 当前状态不是 EXECUTING
     */
    public void pause() {
        requireStatus("暂停", STATUS_EXECUTING);
        this.status = STATUS_SUSPENDED;
    }

    /**
     * 恢复项目：{@code SUSPENDED} → {@code EXECUTING}。
     *
     * @throws IllegalStateException 当前状态不是 SUSPENDED
     */
    public void resume() {
        requireStatus("恢复", STATUS_SUSPENDED);
        this.status = STATUS_EXECUTING;
    }

    /**
     * 提交验收：{@code EXECUTING} → {@code ACCEPTING}（TC-PRJ-034）。
     *
     * @throws IllegalStateException 当前状态不是 EXECUTING
     */
    public void submitAcceptance() {
        requireStatus("提交验收", STATUS_EXECUTING);
        this.status = STATUS_ACCEPTING;
    }

    /**
     * 验收退回整改：{@code ACCEPTING} → {@code EXECUTING}（TC-PRJ-031）。
     *
     * @throws IllegalStateException 当前状态不是 ACCEPTING
     */
    public void returnToExecuting() {
        requireStatus("验收退回", STATUS_ACCEPTING);
        this.status = STATUS_EXECUTING;
    }

    /**
     * 结题：{@code ACCEPTING} → {@code COMPLETED}，并写入冻结信息（TC-PRJ-032）。
     *
     * @param frozenAt     冻结时间
     * @param frozenReason 冻结原因
     * @throws IllegalStateException 当前状态不是 ACCEPTING
     */
    public void complete(OffsetDateTime frozenAt, String frozenReason) {
        requireStatus("结题", STATUS_ACCEPTING);
        this.status = STATUS_COMPLETED;
        this.frozenAt = frozenAt;
        this.frozenReason = frozenReason;
    }

    /**
     * 终止项目：{@code PENDING_START / EXECUTING / SUSPENDED / ACCEPTING} → {@code TERMINATED}。
     *
     * @param reason 终止原因
     * @throws IllegalStateException 当前状态为终态
     */
    public void terminate(String reason) {
        if (!STATUS_PENDING_START.equals(status) && !STATUS_EXECUTING.equals(status)
                && !STATUS_SUSPENDED.equals(status) && !STATUS_ACCEPTING.equals(status)) {
            throw new IllegalStateException("非法项目状态迁移：" + status + " → " + STATUS_TERMINATED);
        }
        this.status = STATUS_TERMINATED;
        this.frozenAt = null;
        this.frozenReason = reason;
    }

    /**
     * 切换生效基线（变更批准后原子切换，TC-PRJ-021）。
     *
     * @param baselineId 新生效基线 ID
     * @throws IllegalArgumentException 基线 ID 为空
     * @throws IllegalStateException    终态项目不允许切换基线
     */
    public void switchBaseline(String baselineId) {
        if (baselineId == null || baselineId.isBlank()) {
            throw new IllegalArgumentException("baselineId 不能为空");
        }
        if (STATUS_COMPLETED.equals(status) || STATUS_TERMINATED.equals(status)) {
            throw new IllegalStateException("终态项目不允许切换基线：" + status);
        }
        this.currentBaselineId = baselineId;
    }

    /**
     * 调整项目执行周期（周期变更批准后生效，T-PRJ-021）。
     *
     * @param startDate 新开始日期
     * @param endDate   新结束日期
     * @throws IllegalArgumentException 日期为空或开始晚于结束
     * @throws IllegalStateException    终态项目不允许调整周期
     */
    public void adjustPeriod(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("项目开始/结束日期不能为空");
        }
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("项目开始日期不能晚于结束日期");
        }
        if (STATUS_COMPLETED.equals(status) || STATUS_TERMINATED.equals(status)) {
            throw new IllegalStateException("终态项目不允许调整周期：" + status);
        }
        this.startDate = startDate;
        this.endDate = endDate;
    }

    /**
     * 交接项目负责人（交接批准后生效，Batch-13 使用）。
     *
     * @param newPiId 新负责人用户 ID
     * @throws IllegalArgumentException 新负责人为空或与当前负责人相同
     */
    public void handoverTo(String newPiId) {
        if (newPiId == null || newPiId.isBlank()) {
            throw new IllegalArgumentException("newPiId 不能为空");
        }
        if (UuidUtils.sameId(newPiId, this.piId)) {
            throw new IllegalArgumentException("新负责人不能与当前负责人相同");
        }
        this.piId = newPiId;
    }

    private void requireStatus(String action, String expected) {
        if (!expected.equals(status)) {
            throw new IllegalStateException("非法项目状态迁移：" + status + " → "
                    + targetStatus(action) + "（仅允许从 " + expected + "）");
        }
    }

    private static String targetStatus(String action) {
        return switch (action) {
            case "启动" -> STATUS_EXECUTING;
            case "暂停" -> STATUS_SUSPENDED;
            case "恢复" -> STATUS_EXECUTING;
            case "提交验收" -> STATUS_ACCEPTING;
            case "验收退回" -> STATUS_EXECUTING;
            default -> action;
        };
    }

    public String getProjectNo() {
        return projectNo;
    }

    public void setProjectNo(String projectNo) {
        this.projectNo = projectNo;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getProjectType() {
        return projectType;
    }

    public void setProjectType(String projectType) {
        this.projectType = projectType;
    }

    public String getDeptId() {
        return deptId;
    }

    public void setDeptId(String deptId) {
        this.deptId = deptId;
    }

    public String getPiId() {
        return piId;
    }

    public void setPiId(String piId) {
        this.piId = piId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceApplicationVersionId() {
        return sourceApplicationVersionId;
    }

    public void setSourceApplicationVersionId(String sourceApplicationVersionId) {
        this.sourceApplicationVersionId = sourceApplicationVersionId;
    }

    public String getCurrentBaselineId() {
        return currentBaselineId;
    }

    public void setCurrentBaselineId(String currentBaselineId) {
        this.currentBaselineId = currentBaselineId;
    }

    public OffsetDateTime getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(OffsetDateTime approvedAt) {
        this.approvedAt = approvedAt;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public String getFinancialProjectNo() {
        return financialProjectNo;
    }

    public void setFinancialProjectNo(String financialProjectNo) {
        this.financialProjectNo = financialProjectNo;
    }

    public String getExternalApprovalNo() {
        return externalApprovalNo;
    }

    public void setExternalApprovalNo(String externalApprovalNo) {
        this.externalApprovalNo = externalApprovalNo;
    }

    public String getExternalContractNo() {
        return externalContractNo;
    }

    public void setExternalContractNo(String externalContractNo) {
        this.externalContractNo = externalContractNo;
    }

    public OffsetDateTime getFrozenAt() {
        return frozenAt;
    }

    public void setFrozenAt(OffsetDateTime frozenAt) {
        this.frozenAt = frozenAt;
    }

    public String getFrozenReason() {
        return frozenReason;
    }

    public void setFrozenReason(String frozenReason) {
        this.frozenReason = frozenReason;
    }
}
