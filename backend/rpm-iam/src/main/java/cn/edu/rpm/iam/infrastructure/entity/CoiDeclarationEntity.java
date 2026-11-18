package cn.edu.rpm.iam.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.Version;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * {@code coi_declaration} 利益冲突声明 PO（T-IAM-006）。
 *
 * <p>对应《02-数据模型与数据库设计.md》§3.1 与 V01 基线 DDL。专家在承接评审前
 * 声明与业务相关人的利益冲突关系：</p>
 * <ul>
 *   <li>{@code biz_type} + {@code biz_id} 定位业务对象（如 REVIEW + 评审单 ID）；</li>
 *   <li>{@code expert_id} 声明专家（iam_user.id），{@code related_user_id} 与专家存在
 *       冲突关系的用户（iam_user.id）；uk_coi_biz_expert 为
 *       (biz_type, biz_id, expert_id, related_user_id) 四列唯一；</li>
 *   <li>{@code relation_type} 枚举：DEPT / COLLABORATION / FAMILY / FINANCIAL / OTHER
 *       （ck_coi_relation_type）；</li>
 *   <li>{@code status} 枚举：DECLARED / CONFIRMED / EXEMPTED / CLOSED（ck_coi_status）；</li>
 *   <li>V01 该表审计列为 created_at / updated_at / version（DDL 权威结构），无
 *       created_by / updated_by，故<b>不继承</b> BaseEntity，时间填充与乐观锁在本类内声明；
 *       查询走 idx_coi_biz_expert（biz_type, biz_id, expert_id）。</li>
 * </ul>
 */
@TableName("coi_declaration")
public class CoiDeclarationEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键；插入时由 MyBatis-Plus 生成 uuid（与 BaseEntity 同策略）。 */
    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    /** 业务对象类型，如 REVIEW。 */
    @TableField("biz_type")
    private String bizType;

    /** 业务对象 ID（uuid）。 */
    @TableField("biz_id")
    private String bizId;

    /** 声明专家用户 ID（iam_user.id）。 */
    @TableField("expert_id")
    private String expertId;

    /** 与专家存在冲突关系的用户 ID（iam_user.id）。 */
    @TableField("related_user_id")
    private String relatedUserId;

    /** 冲突关系类型：DEPT / COLLABORATION / FAMILY / FINANCIAL / OTHER（ck_coi_relation_type）。 */
    @TableField("relation_type")
    private String relationType;

    /** 冲突关系说明；可空。 */
    @TableField("description")
    private String description;

    /** 声明状态：DECLARED / CONFIRMED / EXEMPTED / CLOSED（ck_coi_status）。 */
    @TableField("status")
    private String status;

    /** 声明时间。 */
    @TableField("declared_at")
    private OffsetDateTime declaredAt;

    /** 处置复核人用户 ID（iam_user.id）；可空，未处置时为 null。 */
    @TableField("reviewed_by")
    private String reviewedBy;

    /** 处置复核时间；可空。 */
    @TableField("reviewed_at")
    private OffsetDateTime reviewedAt;

    /** 创建时间；INSERT 时由审计填充器写入。 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    /** 最后更新时间；INSERT/UPDATE 时由审计填充器在字段为 null 时写入。 */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;

    /** 乐观锁版本号；更新语句以此字段旧值作为并发条件。 */
    @Version
    @TableField(value = "version")
    private Integer version = 0;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getBizType() {
        return bizType;
    }

    public void setBizType(String bizType) {
        this.bizType = bizType;
    }

    public String getBizId() {
        return bizId;
    }

    public void setBizId(String bizId) {
        this.bizId = bizId;
    }

    public String getExpertId() {
        return expertId;
    }

    public void setExpertId(String expertId) {
        this.expertId = expertId;
    }

    public String getRelatedUserId() {
        return relatedUserId;
    }

    public void setRelatedUserId(String relatedUserId) {
        this.relatedUserId = relatedUserId;
    }

    public String getRelationType() {
        return relationType;
    }

    public void setRelationType(String relationType) {
        this.relationType = relationType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public OffsetDateTime getDeclaredAt() {
        return declaredAt;
    }

    public void setDeclaredAt(OffsetDateTime declaredAt) {
        this.declaredAt = declaredAt;
    }

    public String getReviewedBy() {
        return reviewedBy;
    }

    public void setReviewedBy(String reviewedBy) {
        this.reviewedBy = reviewedBy;
    }

    public OffsetDateTime getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(OffsetDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
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
}
