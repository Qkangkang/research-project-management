package cn.edu.rpm.project.infrastructure.entity;

import cn.edu.rpm.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * {@code project_risk} 项目风险 PO（T-PRJ-006）。
 *
 * <p>关闭必须填写处置结果与证据（TC-EXEC-017/018）；逾期按截止日 23:59:59 边界升级，
 * 风险等级提升一级（TC-EXEC-019/020）。{@code source_rule} / {@code idempotency_key}
 * 用于规则扫描幂等。</p>
 */
@TableName("project_risk")
public class ProjectRiskEntity extends BaseEntity {

    /** 风险等级：低。 */
    public static final String LEVEL_LOW = "LOW";

    /** 风险等级：中。 */
    public static final String LEVEL_MEDIUM = "MEDIUM";

    /** 风险等级：高。 */
    public static final String LEVEL_HIGH = "HIGH";

    /** 风险等级：严重。 */
    public static final String LEVEL_CRITICAL = "CRITICAL";

    /** 风险状态：打开。 */
    public static final String STATUS_OPEN = "OPEN";

    /** 风险状态：处理中。 */
    public static final String STATUS_PROCESSING = "PROCESSING";

    /** 风险状态：已升级。 */
    public static final String STATUS_ESCALATED = "ESCALATED";

    /** 风险状态：已关闭。 */
    public static final String STATUS_CLOSED = "CLOSED";

    /** 风险状态：已取消。 */
    public static final String STATUS_CANCELLED = "CANCELLED";

    /** 项目 ID（project.id）。 */
    @TableField("project_id")
    private String projectId;

    /** 来源规则编码（自动生成风险的幂等键）。 */
    @TableField("source_rule")
    private String sourceRule;

    /** 风险类型（PROGRESS / FUNDING / OUTPUT 等）。 */
    @TableField("risk_type")
    private String riskType;

    /** 风险标题。 */
    @TableField("title")
    private String title;

    /** 风险描述。 */
    @TableField("description")
    private String description;

    /** 风险等级：LOW / MEDIUM / HIGH / CRITICAL。 */
    @TableField("risk_level")
    private String riskLevel;

    /** 责任人用户 ID。 */
    @TableField("owner_id")
    private String ownerId;

    /** 处置截止日期。 */
    @TableField("due_date")
    private LocalDate dueDate;

    /** 状态：OPEN / PROCESSING / ESCALATED / CLOSED / CANCELLED。 */
    @TableField("status")
    private String status;

    /** 处置结果；关闭时必填。 */
    @TableField("resolution")
    private String resolution;

    /** 处置证据文件 ID。 */
    @TableField("evidence_file_id")
    private String evidenceFileId;

    /** 幂等键（uk_project_risk_idempotency）。 */
    @TableField("idempotency_key")
    private String idempotencyKey;

    /** 关闭时间。 */
    @TableField("closed_at")
    private OffsetDateTime closedAt;

    /** 关闭人用户 ID。 */
    @TableField("closed_by")
    private String closedBy;

    /**
     * 标记处理中：{@code OPEN} → {@code PROCESSING}。
     *
     * @throws IllegalStateException 当前状态不是 OPEN
     */
    public void startProcessing() {
        if (!STATUS_OPEN.equals(status)) {
            throw new IllegalStateException("非法风险状态迁移：" + status + " → " + STATUS_PROCESSING);
        }
        this.status = STATUS_PROCESSING;
    }

    /**
     * 关闭风险。
     *
     * @param resolution     处置结果
     * @param evidenceFileId 证据文件 ID
     * @param closedBy       关闭人用户 ID
     * @param closedAt       关闭时间
     * @throws IllegalStateException 当前状态已关闭/已取消
     */
    public void close(String resolution, String evidenceFileId, String closedBy, OffsetDateTime closedAt) {
        if (STATUS_CLOSED.equals(status) || STATUS_CANCELLED.equals(status)) {
            throw new IllegalStateException("非法风险状态迁移：" + status + " → " + STATUS_CLOSED);
        }
        this.resolution = resolution;
        this.evidenceFileId = evidenceFileId;
        this.closedBy = closedBy;
        this.closedAt = closedAt;
        this.status = STATUS_CLOSED;
    }

    /**
     * 逾期升级：风险等级提升一级（CRITICAL 封顶），状态置 {@code ESCALATED}。
     *
     * @return 升级前的风险等级
     * @throws IllegalStateException 已关闭/已取消的风险不允许升级
     */
    public String escalate() {
        if (STATUS_CLOSED.equals(status) || STATUS_CANCELLED.equals(status)) {
            throw new IllegalStateException("非法风险状态迁移：" + status + " → " + STATUS_ESCALATED);
        }
        String previous = this.riskLevel;
        this.riskLevel = nextLevel(riskLevel);
        this.status = STATUS_ESCALATED;
        return previous;
    }

    /**
     * 判断是否处于未结状态（OPEN / PROCESSING / ESCALATED）。
     *
     * @return 未结返回 {@code true}
     */
    public boolean isOpenLike() {
        return STATUS_OPEN.equals(status) || STATUS_PROCESSING.equals(status)
                || STATUS_ESCALATED.equals(status);
    }

    private static String nextLevel(String level) {
        if (LEVEL_LOW.equals(level)) {
            return LEVEL_MEDIUM;
        }
        if (LEVEL_MEDIUM.equals(level)) {
            return LEVEL_HIGH;
        }
        if (LEVEL_HIGH.equals(level)) {
            return LEVEL_CRITICAL;
        }
        return LEVEL_CRITICAL;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getSourceRule() {
        return sourceRule;
    }

    public void setSourceRule(String sourceRule) {
        this.sourceRule = sourceRule;
    }

    public String getRiskType() {
        return riskType;
    }

    public void setRiskType(String riskType) {
        this.riskType = riskType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
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

    public String getResolution() {
        return resolution;
    }

    public void setResolution(String resolution) {
        this.resolution = resolution;
    }

    public String getEvidenceFileId() {
        return evidenceFileId;
    }

    public void setEvidenceFileId(String evidenceFileId) {
        this.evidenceFileId = evidenceFileId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public OffsetDateTime getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(OffsetDateTime closedAt) {
        this.closedAt = closedAt;
    }

    public String getClosedBy() {
        return closedBy;
    }

    public void setClosedBy(String closedBy) {
        this.closedBy = closedBy;
    }
}
