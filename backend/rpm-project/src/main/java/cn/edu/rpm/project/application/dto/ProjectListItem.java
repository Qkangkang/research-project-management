package cn.edu.rpm.project.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 项目列表行（T-PRJ-011；技术文档 04 §3.6 Response 字段逐项对应）。
 *
 * <p>字段名与文档响应 JSON 一致：{@code project_id / project_no / title / project_type /
 * dept_name / pi_name / status / start_date / end_date / budget_total / paid_amount /
 * risk_level}。部门名、PI 姓名、经费汇总与风险等级由同一条参数化 SQL 批量填充，
 * 禁止逐行回查（RULE-DB2）。</p>
 *
 * <p>使用可变 JavaBean 承载：MyBatis 结果映射依赖 setter，且后续若需字段脱敏可直接修改。</p>
 */
public class ProjectListItem {

    @JsonProperty("project_id")
    private String projectId;

    @JsonProperty("project_no")
    private String projectNo;

    @JsonProperty("title")
    private String title;

    @JsonProperty("project_type")
    private String projectType;

    @JsonProperty("dept_name")
    private String deptName;

    @JsonProperty("pi_name")
    private String piName;

    @JsonProperty("status")
    private String status;

    @JsonProperty("start_date")
    private LocalDate startDate;

    @JsonProperty("end_date")
    private LocalDate endDate;

    @JsonProperty("budget_total")
    private BigDecimal budgetTotal;

    @JsonProperty("paid_amount")
    private BigDecimal paidAmount;

    /** 未关闭风险中的最高等级；无未关闭风险时为 {@code null}。 */
    @JsonProperty("risk_level")
    private String riskLevel;

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

    public String getDeptName() {
        return deptName;
    }

    public void setDeptName(String deptName) {
        this.deptName = deptName;
    }

    public String getPiName() {
        return piName;
    }

    public void setPiName(String piName) {
        this.piName = piName;
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

    public BigDecimal getBudgetTotal() {
        return budgetTotal;
    }

    public void setBudgetTotal(BigDecimal budgetTotal) {
        this.budgetTotal = budgetTotal;
    }

    public BigDecimal getPaidAmount() {
        return paidAmount;
    }

    public void setPaidAmount(BigDecimal paidAmount) {
        this.paidAmount = paidAmount;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }
}
