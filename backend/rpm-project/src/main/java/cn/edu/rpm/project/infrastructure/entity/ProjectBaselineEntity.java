package cn.edu.rpm.project.infrastructure.entity;

import cn.edu.rpm.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * {@code project_baseline} 项目基线 PO（T-PRJ-003）。
 *
 * <p>不可变历史版本：{@code snapshot} 保存批准版本快照（jsonb 文本），
 * {@code snapshot_hash} 为规范化 JSON 的 SHA-256（64 位十六进制）。
 * 同一项目同一时刻只能有一个 {@code EFFECTIVE} 基线，由部分唯一索引
 * {@code uk_project_baseline_effective}（V04）兜底。</p>
 */
@TableName("project_baseline")
public class ProjectBaselineEntity extends BaseEntity {

    /** 基线状态：草稿。 */
    public static final String STATUS_DRAFT = "DRAFT";

    /** 基线状态：生效。 */
    public static final String STATUS_EFFECTIVE = "EFFECTIVE";

    /** 基线状态：已被新版本取代。 */
    public static final String STATUS_SUPERSEDED = "SUPERSEDED";

    /** 项目 ID（project.id）。 */
    @TableField("project_id")
    private String projectId;

    /** 基线版本号（项目内从 1 递增，uk_project_baseline_no）。 */
    @TableField("version_no")
    private Integer versionNo;

    /** 基线快照（jsonb 文本；规范化 JSON）。 */
    @TableField("snapshot")
    private String snapshot;

    /** 快照哈希（SHA-256 小写十六进制，ck_project_baseline_hash）。 */
    @TableField("snapshot_hash")
    private String snapshotHash;

    /** 生效时间。 */
    @TableField("effective_at")
    private OffsetDateTime effectiveAt;

    /** 基线状态：DRAFT / EFFECTIVE / SUPERSEDED。 */
    @TableField("status")
    private String status;

    /** 来源变更单 ID（project change_request.id）；立项初始基线为空。 */
    @TableField("source_change_id")
    private String sourceChangeId;

    /**
     * 将基线置为被新版本取代（变更批准时与新建基线同事务）。
     *
     * @throws IllegalStateException 当前状态不是 EFFECTIVE
     */
    public void supersede() {
        if (!STATUS_EFFECTIVE.equals(status)) {
            throw new IllegalStateException("非法基线状态迁移：" + status + " → " + STATUS_SUPERSEDED);
        }
        this.status = STATUS_SUPERSEDED;
    }

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

    public String getSnapshot() {
        return snapshot;
    }

    public void setSnapshot(String snapshot) {
        this.snapshot = snapshot;
    }

    public String getSnapshotHash() {
        return snapshotHash;
    }

    public void setSnapshotHash(String snapshotHash) {
        this.snapshotHash = snapshotHash;
    }

    public OffsetDateTime getEffectiveAt() {
        return effectiveAt;
    }

    public void setEffectiveAt(OffsetDateTime effectiveAt) {
        this.effectiveAt = effectiveAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSourceChangeId() {
        return sourceChangeId;
    }

    public void setSourceChangeId(String sourceChangeId) {
        this.sourceChangeId = sourceChangeId;
    }
}
