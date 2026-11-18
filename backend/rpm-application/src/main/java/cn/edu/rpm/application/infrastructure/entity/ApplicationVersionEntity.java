package cn.edu.rpm.application.infrastructure.entity;

import cn.edu.rpm.common.entity.BaseEntity;
import cn.edu.rpm.common.util.UuidUtils;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * {@code application_version} 不可变申报版本 PO（T-APP-004）。
 *
 * <p>对应《02-数据模型与数据库设计.md》§3.2 与 V01 基线 DDL。关键约束：</p>
 * <ul>
 *   <li>{@code uk_application_version_no (application_id, version_no)}：同一申报内版本号唯一，
 *       版本号从 1 递增；</li>
 *   <li>{@code uk_application_version_idem (submit_idempotency_key)}：提交幂等键全局唯一，
 *       同一 {@code Idempotency-Key} 重复请求返回首次版本（TC-APP-008）；</li>
 *   <li>{@code ck_application_version_hash}：{@code content_hash} 为 64 位 SHA-256 十六进制；</li>
 *   <li>{@code ck_application_version_status}：DRAFT / SUBMITTED / APPROVED / REJECTED /
 *       SUPERSEDED / WITHDRAWN。</li>
 * </ul>
 *
 * <p>版本一旦提交即不可变（禁止覆盖已生效版本）：补正提交生成新版本，旧版本状态
 * 由后续应用服务显式迁移。{@code form_data} 为规范化 JSON 的 jsonb 文本，审计字段由
 * {@link BaseEntity} 承载。</p>
 */
@TableName("application_version")
public class ApplicationVersionEntity extends BaseEntity {

    /** 版本状态：草稿（预留在途状态）。 */
    public static final String STATUS_DRAFT = "DRAFT";

    /** 版本状态：已提交（进入审批）。 */
    public static final String STATUS_SUBMITTED = "SUBMITTED";

    /** 版本状态：已批准。 */
    public static final String STATUS_APPROVED = "APPROVED";

    /** 版本状态：不批准。 */
    public static final String STATUS_REJECTED = "REJECTED";

    /** 版本状态：已被后续版本取代。 */
    public static final String STATUS_SUPERSEDED = "SUPERSEDED";

    /** 版本状态：申请人撤回（主档回 DRAFT）。 */
    public static final String STATUS_WITHDRAWN = "WITHDRAWN";

    /** 所属申报主档 ID（application.id，ON DELETE RESTRICT）。 */
    @TableField("application_id")
    private String applicationId;

    /** 版本号，从 1 递增（ck_application_version_no）。 */
    @TableField("version_no")
    private Integer versionNo;

    /** 规范化表单数据（jsonb 文本，不可变快照）。 */
    @TableField("form_data")
    private String formData;

    /** 表单内容 SHA-256（64 位十六进制，ck_application_version_hash）。 */
    @TableField("content_hash")
    private String contentHash;

    /** 版本状态：DRAFT / SUBMITTED / APPROVED / REJECTED / SUPERSEDED / WITHDRAWN。 */
    @TableField("status")
    private String status;

    /** 提交人用户 ID（iam_user.id）。 */
    @TableField("submitted_by")
    private String submittedBy;

    /** 提交时刻（默认 now()）。 */
    @TableField("submitted_at")
    private OffsetDateTime submittedAt;

    /** 提交幂等键（uk_application_version_idem）；草稿态为空。 */
    @TableField("submit_idempotency_key")
    private String submitIdempotencyKey;

    /**
     * 构造新的草稿版本行（保存草稿时插入）。
     *
     * @param applicationId 申报主档 ID（32 位 uuid 文本）
     * @param versionNo     版本号（从 1 起；取当前最大值 + 1）
     * @param formData      规范化表单 JSON
     * @param contentHash   表单内容 SHA-256（64 位十六进制）
     * @return 草稿版本 PO
     */
    public static ApplicationVersionEntity newDraft(String applicationId, int versionNo,
                                                    String formData, String contentHash) {
        ApplicationVersionEntity version = new ApplicationVersionEntity();
        version.setId(UuidUtils.formatCompact(java.util.UUID.randomUUID()));
        version.setApplicationId(requireText(applicationId, "applicationId"));
        version.setVersionNo(requirePositive(versionNo));
        version.setFormData(formData);
        version.setContentHash(requireHash(contentHash));
        version.setStatus(STATUS_DRAFT);
        version.setVersion(0);
        return version;
    }

    /**
     * 构造新的已提交版本行（无草稿行时提交插入）。
     *
     * @param applicationId  申报主档 ID
     * @param versionNo      版本号（当前最大值 + 1）
     * @param formData       规范化表单 JSON
     * @param contentHash    表单内容 SHA-256
     * @param submittedBy    提交人用户 ID（32 位 uuid 文本）
     * @param submittedAt    提交时刻
     * @param idempotencyKey 提交幂等键
     * @return 已提交版本 PO
     */
    public static ApplicationVersionEntity newSubmitted(String applicationId, int versionNo,
                                                        String formData, String contentHash,
                                                        String submittedBy,
                                                        java.time.OffsetDateTime submittedAt,
                                                        String idempotencyKey) {
        ApplicationVersionEntity version = newDraft(applicationId, versionNo, formData, contentHash);
        version.promoteToSubmitted(formData, contentHash, submittedBy, submittedAt, idempotencyKey);
        return version;
    }

    /**
     * 保存草稿：更新草稿版本的表单内容（仅 {@code DRAFT} 允许）。
     *
     * @param formData    规范化表单 JSON
     * @param contentHash 表单内容 SHA-256
     * @throws IllegalStateException 版本已提交后不可覆盖（不可变约束）
     */
    public void applyDraftContent(String formData, String contentHash) {
        requireStatus(STATUS_DRAFT);
        this.formData = formData;
        this.contentHash = requireHash(contentHash);
    }

    /**
     * 提交固化：{@code DRAFT} → {@code SUBMITTED}，写入幂等键与提交人。
     *
     * <p>已提交版本禁止再次迁移（不可变历史版本）。</p>
     *
     * @param formData       规范化表单 JSON
     * @param contentHash    表单内容 SHA-256
     * @param submittedBy    提交人用户 ID
     * @param submittedAt    提交时刻
     * @param idempotencyKey 提交幂等键（全局唯一）
     * @throws IllegalStateException 当前版本不是草稿
     */
    public void promoteToSubmitted(String formData, String contentHash, String submittedBy,
                                   java.time.OffsetDateTime submittedAt, String idempotencyKey) {
        requireStatus(STATUS_DRAFT);
        this.formData = formData;
        this.contentHash = requireHash(contentHash);
        this.submittedBy = requireText(submittedBy, "submittedBy");
        this.submittedAt = java.util.Objects.requireNonNull(submittedAt, "submittedAt 不能为空");
        this.submitIdempotencyKey = requireText(idempotencyKey, "idempotencyKey");
        this.status = STATUS_SUBMITTED;
    }

    private void requireStatus(String expected) {
        if (!expected.equals(status)) {
            throw new IllegalStateException("非法版本状态迁移：当前 " + status + "，要求 " + expected);
        }
    }

    /**
     * 立项决策批准：{@code SUBMITTED} → {@code APPROVED}（版本内容不变）。
     *
     * @throws IllegalStateException 当前版本不是已提交状态
     */
    public void markApproved() {
        requireStatus(STATUS_SUBMITTED);
        this.status = STATUS_APPROVED;
    }

    /**
     * 立项决策不批准：{@code SUBMITTED} → {@code REJECTED}（版本内容不变）。
     *
     * @throws IllegalStateException 当前版本不是已提交状态
     */
    public void markRejected() {
        requireStatus(STATUS_SUBMITTED);
        this.status = STATUS_REJECTED;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value;
    }

    private static int requirePositive(int versionNo) {
        if (versionNo < 1) {
            throw new IllegalArgumentException("versionNo 必须从 1 开始");
        }
        return versionNo;
    }

    private static String requireHash(String contentHash) {
        if (contentHash == null || !contentHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("contentHash 必须为 64 位小写十六进制");
        }
        return contentHash;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
    }

    public Integer getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(Integer versionNo) {
        this.versionNo = versionNo;
    }

    public String getFormData() {
        return formData;
    }

    public void setFormData(String formData) {
        this.formData = formData;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSubmittedBy() {
        return submittedBy;
    }

    public void setSubmittedBy(String submittedBy) {
        this.submittedBy = submittedBy;
    }

    public OffsetDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(OffsetDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    public String getSubmitIdempotencyKey() {
        return submitIdempotencyKey;
    }

    public void setSubmitIdempotencyKey(String submitIdempotencyKey) {
        this.submitIdempotencyKey = submitIdempotencyKey;
    }
}
