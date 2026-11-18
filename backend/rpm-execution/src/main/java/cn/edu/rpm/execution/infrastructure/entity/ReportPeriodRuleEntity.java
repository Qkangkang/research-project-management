package cn.edu.rpm.execution.infrastructure.entity;

import cn.edu.rpm.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * {@code report_period_rule} 报告周期规则 PO（T-EXEC-005 支撑；技术文档 02 §3.3）。
 *
 * <p>周期报告到期日 = 周期结束日 + {@code due_offset_days}。</p>
 */
@TableName("report_period_rule")
public class ReportPeriodRuleEntity extends BaseEntity {

    /** 规则状态：启用。 */
    public static final String STATUS_ACTIVE = "ACTIVE";

    /** 规则状态：停用。 */
    public static final String STATUS_INACTIVE = "INACTIVE";

    /** 适用项目类型。 */
    @TableField("project_type")
    private String projectType;

    /** 周期类型：MONTH / QUARTER。 */
    @TableField("period_type")
    private String periodType;

    /** 到期偏移天数（周期结束后第几天到期，≥0）。 */
    @TableField("due_offset_days")
    private Integer dueOffsetDays;

    /** 迟报宽限天数（≥0）。 */
    @TableField("late_grace_days")
    private Integer lateGraceDays;

    /** 状态：ACTIVE / INACTIVE。 */
    @TableField("status")
    private String status;

    public String getProjectType() {
        return projectType;
    }

    public void setProjectType(String projectType) {
        this.projectType = projectType;
    }

    public String getPeriodType() {
        return periodType;
    }

    public void setPeriodType(String periodType) {
        this.periodType = periodType;
    }

    public Integer getDueOffsetDays() {
        return dueOffsetDays;
    }

    public void setDueOffsetDays(Integer dueOffsetDays) {
        this.dueOffsetDays = dueOffsetDays;
    }

    public Integer getLateGraceDays() {
        return lateGraceDays;
    }

    public void setLateGraceDays(Integer lateGraceDays) {
        this.lateGraceDays = lateGraceDays;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
