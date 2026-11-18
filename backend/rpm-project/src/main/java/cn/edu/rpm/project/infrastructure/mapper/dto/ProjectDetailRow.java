package cn.edu.rpm.project.infrastructure.mapper.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 项目详情摘要查询的扁平行（T-PRJ-011；MyBatis 结果映射用）。
 *
 * <p>SQL 一次往返返回项目头 + 各页签计数；仓储实现再组装为对外的
 * {@code ProjectDetailSummary}。使用 JavaBean 而非 record，保证 MyBatis
 * setter 映射稳定（不依赖编译参数 {@code -parameters}）。</p>
 */
public class ProjectDetailRow {

    private String projectId;

    private String projectNo;

    private String title;

    private String projectType;

    private String piId;

    private String piName;

    private String deptId;

    private String deptName;

    private String baselineId;

    private Integer baselineVersion;

    private String status;

    private LocalDate startDate;

    private LocalDate endDate;

    private Long memberCount;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    private Long milestoneTotal;

    private Long milestoneCompleted;

    private Long reportDue;

    private BigDecimal budgetTotal;

    private BigDecimal availableAmount;

    private Long outputCount;

    private Long openRiskCount;

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
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

    public String getPiId() {
        return piId;
    }

    public void setPiId(String piId) {
        this.piId = piId;
    }

    public String getPiName() {
        return piName;
    }

    public void setPiName(String piName) {
        this.piName = piName;
    }

    public String getDeptId() {
        return deptId;
    }

    public void setDeptId(String deptId) {
        this.deptId = deptId;
    }

    public String getDeptName() {
        return deptName;
    }

    public void setDeptName(String deptName) {
        this.deptName = deptName;
    }

    public String getBaselineId() {
        return baselineId;
    }

    public void setBaselineId(String baselineId) {
        this.baselineId = baselineId;
    }

    public Integer getBaselineVersion() {
        return baselineVersion;
    }

    public void setBaselineVersion(Integer baselineVersion) {
        this.baselineVersion = baselineVersion;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

    public Long getMemberCount() {
        return memberCount;
    }

    public void setMemberCount(Long memberCount) {
        this.memberCount = memberCount;
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

    public Long getMilestoneTotal() {
        return milestoneTotal;
    }

    public void setMilestoneTotal(Long milestoneTotal) {
        this.milestoneTotal = milestoneTotal;
    }

    public Long getMilestoneCompleted() {
        return milestoneCompleted;
    }

    public void setMilestoneCompleted(Long milestoneCompleted) {
        this.milestoneCompleted = milestoneCompleted;
    }

    public Long getReportDue() {
        return reportDue;
    }

    public void setReportDue(Long reportDue) {
        this.reportDue = reportDue;
    }

    public BigDecimal getBudgetTotal() {
        return budgetTotal;
    }

    public void setBudgetTotal(BigDecimal budgetTotal) {
        this.budgetTotal = budgetTotal;
    }

    public BigDecimal getAvailableAmount() {
        return availableAmount;
    }

    public void setAvailableAmount(BigDecimal availableAmount) {
        this.availableAmount = availableAmount;
    }

    public Long getOutputCount() {
        return outputCount;
    }

    public void setOutputCount(Long outputCount) {
        this.outputCount = outputCount;
    }

    public Long getOpenRiskCount() {
        return openRiskCount;
    }

    public void setOpenRiskCount(Long openRiskCount) {
        this.openRiskCount = openRiskCount;
    }
}
