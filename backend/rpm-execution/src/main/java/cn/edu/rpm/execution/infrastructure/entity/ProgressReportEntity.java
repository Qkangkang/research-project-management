package cn.edu.rpm.execution.infrastructure.entity;

import cn.edu.rpm.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDate;

/**
 * {@code progress_report} 周期报告主档 PO（T-EXEC-001）。
 *
 * <p>同一项目同一周期唯一（{@code uk_progress_report_period}）。
 * 主档状态不含 {@code LATE}：迟报事实唯一来源为版本 {@code late_flag}（V05 修复 #23）。</p>
 */
@TableName("progress_report")
public class ProgressReportEntity extends BaseEntity {

    /** 报告周期类型：月报。 */
    public static final String PERIOD_MONTH = "MONTH";

    /** 报告周期类型：季报。 */
    public static final String PERIOD_QUARTER = "QUARTER";

    /** 状态：草稿。 */
    public static final String STATUS_DRAFT = "DRAFT";

    /** 状态：已提交。 */
    public static final String STATUS_SUBMITTED = "SUBMITTED";

    /** 状态：已退回。 */
    public static final String STATUS_RETURNED = "RETURNED";

    /** 状态：已通过。 */
    public static final String STATUS_APPROVED = "APPROVED";

    /** 状态：已关闭。 */
    public static final String STATUS_CLOSED = "CLOSED";

    /** 项目 ID（project.id）。 */
    @TableField("project_id")
    private String projectId;

    /** 周期类型：MONTH / QUARTER（ck_progress_report_type）。 */
    @TableField("period_type")
    private String periodType;

    /** 周期键，如 {@code 2026-11}（月报）或 {@code 2026-Q4}（季报）。 */
    @TableField("period_key")
    private String periodKey;

    /** 到期日；按报告周期规则计算。 */
    @TableField("due_date")
    private LocalDate dueDate;

    /** 状态：DRAFT / SUBMITTED / RETURNED / APPROVED / CLOSED。 */
    @TableField("status")
    private String status;

    /** 当前版本 ID（progress_report_version.id）。 */
    @TableField("current_version_id")
    private String currentVersionId;

    /**
     * 提交报告：{@code DRAFT}/{@code RETURNED} → {@code SUBMITTED}，并绑定当前版本。
     *
     * @param versionId 版本 ID
     * @throws IllegalStateException 当前状态不允许提交
     */
    public void markSubmitted(String versionId) {
        if (!STATUS_DRAFT.equals(status) && !STATUS_RETURNED.equals(status)) {
            throw new IllegalStateException("非法报告状态迁移：" + status + " → " + STATUS_SUBMITTED);
        }
        this.status = STATUS_SUBMITTED;
        this.currentVersionId = versionId;
    }

    /**
     * 审核退回：{@code SUBMITTED} → {@code RETURNED}。
     *
     * @throws IllegalStateException 当前状态不是 SUBMITTED
     */
    public void markReturned() {
        if (!STATUS_SUBMITTED.equals(status)) {
            throw new IllegalStateException("非法报告状态迁移：" + status + " → " + STATUS_RETURNED);
        }
        this.status = STATUS_RETURNED;
    }

    /**
     * 审核通过：{@code SUBMITTED} → {@code APPROVED}，当前版本固定。
     *
     * @throws IllegalStateException 当前状态不是 SUBMITTED
     */
    public void markApproved() {
        if (!STATUS_SUBMITTED.equals(status)) {
            throw new IllegalStateException("非法报告状态迁移：" + status + " → " + STATUS_APPROVED);
        }
        this.status = STATUS_APPROVED;
    }

    /**
     * 关闭报告：{@code APPROVED} → {@code CLOSED}。
     *
     * @throws IllegalStateException 当前状态不是 APPROVED
     */
    public void close() {
        if (!STATUS_APPROVED.equals(status)) {
            throw new IllegalStateException("非法报告状态迁移：" + status + " → " + STATUS_CLOSED);
        }
        this.status = STATUS_CLOSED;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getPeriodType() {
        return periodType;
    }

    public void setPeriodType(String periodType) {
        this.periodType = periodType;
    }

    public String getPeriodKey() {
        return periodKey;
    }

    public void setPeriodKey(String periodKey) {
        this.periodKey = periodKey;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCurrentVersionId() {
        return currentVersionId;
    }

    public void setCurrentVersionId(String currentVersionId) {
        this.currentVersionId = currentVersionId;
    }
}
