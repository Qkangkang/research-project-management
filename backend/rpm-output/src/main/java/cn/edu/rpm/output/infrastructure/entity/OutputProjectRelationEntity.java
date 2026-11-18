package cn.edu.rpm.output.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * {@code output_project_relation} 成果-项目关联 PO（T-OUT-005）。
 *
 * <p>对应《02-数据模型与数据库设计.md》§3.5 与 V01 基线 DDL。关键约束：</p>
 * <ul>
 *   <li>{@code uk_output_project_relation (output_id, project_id)}：同一成果同一项目唯一；</li>
 *   <li>{@code ck_output_relation_contribution}：贡献说明去空格后非空；</li>
 *   <li>{@code ck_output_relation_ratio}：归属比例 0～1，仅用于指定统计；</li>
 *   <li>{@code ck_output_relation_status}：ACTIVE / REVOKED；撤销置 REVOKED，不物理删除。</li>
 * </ul>
 *
 * <p>该表无 {@code updated_by} 列，故不继承 {@code BaseEntity}；乐观锁字段仍使用
 * {@code @Version}，审计时间由填充器按注解写入。</p>
 */
@TableName("output_project_relation")
public class OutputProjectRelationEntity {

    /** 关联状态：有效。 */
    public static final String STATUS_ACTIVE = "ACTIVE";

    /** 关联状态：已撤销（保留历史记录）。 */
    public static final String STATUS_REVOKED = "REVOKED";

    /** 主键；插入时由 MyBatis-Plus 生成 32 位 uuid 文本。 */
    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    /** 成果 ID（research_output.id，ON DELETE RESTRICT）。 */
    @TableField("output_id")
    private String outputId;

    /** 项目 ID（project.id，ON DELETE RESTRICT）。 */
    @TableField("project_id")
    private String projectId;

    /** 贡献/用途说明（1～1024 字符）。 */
    @TableField("contribution_desc")
    private String contributionDesc;

    /** 归属比例（0～1；仅指定统计口径，不作为法定权属）。 */
    @TableField("attribution_ratio")
    private BigDecimal attributionRatio;

    /** 关联状态：ACTIVE / REVOKED。 */
    @TableField("status")
    private String status;

    /** 创建人用户 ID。 */
    @TableField(value = "created_by", fill = FieldFill.INSERT)
    private String createdBy;

    /** 创建时间。 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    /** 撤销时间。 */
    @TableField("revoked_at")
    private OffsetDateTime revokedAt;

    /** 撤销人用户 ID。 */
    @TableField("revoked_by")
    private String revokedBy;

    /** 最后更新时间。 */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;

    /** 乐观锁版本号。 */
    @Version
    @TableField("version")
    private Integer version = 0;

    /**
     * 构造新的有效关联。
     *
     * @param outputId         成果 ID（32 位 uuid 文本）
     * @param projectId        项目 ID（32 位 uuid 文本）
     * @param contributionDesc 贡献说明（调用方已去首尾空格）
     * @param attributionRatio 归属比例，可空
     * @param createdBy        创建人用户 ID（32 位 uuid 文本）
     * @return 关联 PO
     */
    public static OutputProjectRelationEntity create(String outputId, String projectId,
                                                     String contributionDesc,
                                                     BigDecimal attributionRatio,
                                                     String createdBy) {
        OutputProjectRelationEntity relation = new OutputProjectRelationEntity();
        relation.id = UUID.randomUUID().toString().replace("-", "");
        relation.outputId = requireText(outputId, "outputId");
        relation.projectId = requireText(projectId, "projectId");
        relation.contributionDesc = requireContribution(contributionDesc);
        relation.attributionRatio = requireRatio(attributionRatio);
        relation.status = STATUS_ACTIVE;
        relation.createdBy = requireText(createdBy, "createdBy");
        relation.version = 0;
        return relation;
    }

    /**
     * 恢复已撤销的关联（同一成果-项目唯一键不允许新建第二条记录）。
     *
     * @param contributionDesc 新的贡献说明
     * @param attributionRatio 新的归属比例，可空
     * @throws IllegalStateException 当前关联不是已撤销状态
     */
    public void restore(String contributionDesc, BigDecimal attributionRatio) {
        if (!STATUS_REVOKED.equals(status)) {
            throw new IllegalStateException("仅已撤销关联允许恢复：当前 " + status);
        }
        this.contributionDesc = requireContribution(contributionDesc);
        this.attributionRatio = requireRatio(attributionRatio);
        this.status = STATUS_ACTIVE;
        this.revokedAt = null;
        this.revokedBy = null;
    }

    /**
     * 撤销关联：{@code ACTIVE} → {@code REVOKED}，记录撤销人与时间，不物理删除。
     *
     * @param revokedBy 撤销人用户 ID
     * @param revokedAt 撤销时刻
     * @throws IllegalStateException 当前关联不是有效状态
     */
    public void revoke(String revokedBy, OffsetDateTime revokedAt) {
        if (!STATUS_ACTIVE.equals(status)) {
            throw new IllegalStateException("仅有效关联允许撤销：当前 " + status);
        }
        this.status = STATUS_REVOKED;
        this.revokedBy = requireText(revokedBy, "revokedBy");
        this.revokedAt = Objects.requireNonNull(revokedAt, "revokedAt 不能为空");
    }

    private static String requireContribution(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("contributionDesc 不能为空");
        }
        String normalized = value.trim();
        if (normalized.length() > 1024) {
            throw new IllegalArgumentException("contributionDesc 不能超过 1024 字符");
        }
        return normalized;
    }

    private static BigDecimal requireRatio(BigDecimal ratio) {
        if (ratio == null) {
            return null;
        }
        if (ratio.compareTo(BigDecimal.ZERO) < 0 || ratio.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("attributionRatio 必须在 0～1 之间");
        }
        return ratio;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getOutputId() {
        return outputId;
    }

    public void setOutputId(String outputId) {
        this.outputId = outputId;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getContributionDesc() {
        return contributionDesc;
    }

    public void setContributionDesc(String contributionDesc) {
        this.contributionDesc = contributionDesc;
    }

    public BigDecimal getAttributionRatio() {
        return attributionRatio;
    }

    public void setAttributionRatio(BigDecimal attributionRatio) {
        this.attributionRatio = attributionRatio;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(OffsetDateTime revokedAt) {
        this.revokedAt = revokedAt;
    }

    public String getRevokedBy() {
        return revokedBy;
    }

    public void setRevokedBy(String revokedBy) {
        this.revokedBy = revokedBy;
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
}
