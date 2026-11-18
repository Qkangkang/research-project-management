package cn.edu.rpm.application.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * {@code external_project_registration} 外部项目登记 PO（T-APP-007）。
 *
 * <p>对应《02-数据模型与数据库设计.md》§3.2 与 V01 基线 DDL。关键约束：</p>
 * <ul>
 *   <li>{@code uk_external_registration_application (application_id)}：一个申报最多一条登记；</li>
 *   <li>{@code ck_external_registration_no}：{@code approval_no} 与 {@code contract_no}
 *       至少一个非空；</li>
 *   <li>{@code ck_external_registration_amount}：{@code approved_amount} 为空或 ≥ 0。</li>
 * </ul>
 *
 * <p><b>不继承</b> {@link cn.edu.rpm.common.entity.BaseEntity}：V01/V04 该表只有
 * {@code created_at} / {@code updated_at} / {@code version}，没有 {@code created_by} /
 * {@code updated_by}（操作人由 {@code verified_by} 承载）。审计时间列由审计填充器写入，
 * {@code version} 参与乐观锁。{@code approved_amount} 为 numeric(18,2)，Java 侧使用
 * {@link BigDecimal}（禁止 double/float）；{@code evidence_summary} 为证据文件 ID
 * 数组的 jsonb 文本。</p>
 */
@TableName("external_project_registration")
public class ExternalProjectRegistrationEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键；插入时由 MyBatis-Plus 生成 uuid。 */
    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    /** 关联申报主档 ID（application.id）；唯一（uk_external_registration_application）。 */
    @TableField("application_id")
    private String applicationId;

    /** 外部批复文号；与 {@code contract_no} 至少一个非空。 */
    @TableField("approval_no")
    private String approvalNo;

    /** 外部合同编号；与 {@code approval_no} 至少一个非空。 */
    @TableField("contract_no")
    private String contractNo;

    /** 资助方名称。 */
    @TableField("funder_name")
    private String funderName;

    /** 批复/合同金额（numeric(18,2)）；可空。 */
    @TableField("approved_amount")
    private BigDecimal approvedAmount;

    /** 核验人用户 ID（iam_user.id，须有科研管理权限）。 */
    @TableField("verified_by")
    private String verifiedBy;

    /** 核验时刻（默认 now()）。 */
    @TableField("verified_at")
    private OffsetDateTime verifiedAt;

    /** 证据文件 ID 数组（jsonb 文本，默认 []）。 */
    @TableField("evidence_summary")
    private String evidenceSummary;

    /** 创建时间；INSERT 时由审计填充器写入。 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    /** 最后更新时间；INSERT/UPDATE 时由审计填充器在字段为 null 时写入。 */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;

    /** 乐观锁版本号；更新语句以此字段旧值作为并发条件。 */
    @Version
    @TableField("version")
    private Integer version = 0;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
    }

    public String getApprovalNo() {
        return approvalNo;
    }

    public void setApprovalNo(String approvalNo) {
        this.approvalNo = approvalNo;
    }

    public String getContractNo() {
        return contractNo;
    }

    public void setContractNo(String contractNo) {
        this.contractNo = contractNo;
    }

    public String getFunderName() {
        return funderName;
    }

    public void setFunderName(String funderName) {
        this.funderName = funderName;
    }

    public BigDecimal getApprovedAmount() {
        return approvedAmount;
    }

    public void setApprovedAmount(BigDecimal approvedAmount) {
        this.approvedAmount = approvedAmount;
    }

    public String getVerifiedBy() {
        return verifiedBy;
    }

    public void setVerifiedBy(String verifiedBy) {
        this.verifiedBy = verifiedBy;
    }

    public OffsetDateTime getVerifiedAt() {
        return verifiedAt;
    }

    public void setVerifiedAt(OffsetDateTime verifiedAt) {
        this.verifiedAt = verifiedAt;
    }

    public String getEvidenceSummary() {
        return evidenceSummary;
    }

    public void setEvidenceSummary(String evidenceSummary) {
        this.evidenceSummary = evidenceSummary;
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
