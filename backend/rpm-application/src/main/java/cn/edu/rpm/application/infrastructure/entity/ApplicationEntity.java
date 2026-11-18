package cn.edu.rpm.application.infrastructure.entity;

import cn.edu.rpm.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * {@code application} 申报主档 PO（T-APP-003）。
 *
 * <p>对应《02-数据模型与数据库设计.md》§3.2 与 V01 基线 DDL（状态枚举以 V06 扩展后的
 * ck_application_status 为准）。关键约束：</p>
 * <ul>
 *   <li>{@code uk_application_no (application_no)}：申报编号全局唯一，由 NumberGenerator 生成；</li>
 *   <li>{@code idx_application_applicant_status (applicant_id, status, updated_at DESC)}：
 *       申请人「我的申报」列表；</li>
 *   <li>{@code idx_application_batch_status (batch_id, status)}：批次内申报筛选；</li>
 *   <li>{@code fk_application_current_version}：{@code current_version_id} 指向最近生效版本。</li>
 * </ul>
 *
 * <p>状态机见《01-总体设计与架构.md》§2.4.1；状态迁移由后续批次的聚合/应用服务以显式方法
 * 承载（禁止在业务代码中裸改 {@code status}），本批仅提供持久化载体。</p>
 */
@TableName("application")
public class ApplicationEntity extends BaseEntity {

    /** 申报状态：草稿。 */
    public static final String STATUS_DRAFT = "DRAFT";

    /** 申报状态：审批中。 */
    public static final String STATUS_IN_REVIEW = "IN_REVIEW";

    /** 申报状态：待补正。 */
    public static final String STATUS_SUPPLEMENT = "SUPPLEMENT";

    /** 申报状态：已批准（终态）。 */
    public static final String STATUS_APPROVED = "APPROVED";

    /** 申报状态：不批准（终态）。 */
    public static final String STATUS_REJECTED = "REJECTED";

    /** 申报状态：已撤回（DB 枚举保留值；按 01 §2.4.1，撤回由版本级 WITHDRAWN 标记、主档回 DRAFT）。 */
    public static final String STATUS_WITHDRAWN = "WITHDRAWN";

    /** 申报状态：已关闭（终态，草稿/补正超期）。 */
    public static final String STATUS_CLOSED = "CLOSED";

    /** 申报状态：不予受理（终态，V06 扩展，区别于立项不批准）。 */
    public static final String STATUS_NOT_ACCEPTED = "NOT_ACCEPTED";

    /** 来源类型：院内申报。 */
    public static final String SOURCE_INTERNAL = "INTERNAL";

    /** 来源类型：外部项目登记。 */
    public static final String SOURCE_EXTERNAL = "EXTERNAL";

    /** 申报编号；全局唯一（uk_application_no），如 APP-2026-000123。 */
    @TableField("application_no")
    private String applicationNo;

    /** 申报批次 ID（apply_batch.id，ON DELETE RESTRICT）。 */
    @TableField("batch_id")
    private String batchId;

    /** 项目类型（如 YEAR_PLAN）。 */
    @TableField("project_type")
    private String projectType;

    /** 申请人用户 ID（iam_user.id）。 */
    @TableField("applicant_id")
    private String applicantId;

    /** 申请人所属部门 ID（org_unit.id）。 */
    @TableField("dept_id")
    private String deptId;

    /** 申报标题（1～512 字符）。 */
    @TableField("title")
    private String title;

    /** 来源类型：INTERNAL / EXTERNAL（ck_application_source）。 */
    @TableField("source_type")
    private String sourceType;

    /** 申报状态：DRAFT / IN_REVIEW / SUPPLEMENT / APPROVED / REJECTED /
     *  WITHDRAWN / CLOSED / NOT_ACCEPTED（ck_application_status，V06）。 */
    @TableField("status")
    private String status;

    /** 当前生效版本 ID（application_version.id）；草稿尚未提交时为空。 */
    @TableField("current_version_id")
    private String currentVersionId;

    /**
     * 提交申报：{@code DRAFT}/{@code SUPPLEMENT} → {@code IN_REVIEW}，并绑定当前生效版本。
     *
     * <p>状态迁移的唯一显式入口（禁止业务代码裸调 {@link #setStatus(String)}）；
     * 非法来源状态抛 {@link IllegalStateException}。</p>
     *
     * @param versionId 已固化的申报版本 ID
     * @throws IllegalArgumentException 版本 ID 为空
     * @throws IllegalStateException    当前状态不允许提交
     */
    public void markInReview(String versionId) {
        if (versionId == null || versionId.isBlank()) {
            throw new IllegalArgumentException("versionId 不能为空");
        }
        if (!STATUS_DRAFT.equals(status) && !STATUS_SUPPLEMENT.equals(status)) {
            throw new IllegalStateException("非法申报状态迁移：" + status + " → " + STATUS_IN_REVIEW);
        }
        this.status = STATUS_IN_REVIEW;
        this.currentVersionId = versionId;
    }

    /**
     * 形式审查退回：{@code IN_REVIEW} → {@code SUPPLEMENT}。
     *
     * <p>审批中版本内容不变，申请人补正后重新提交生成新版本（TC-APP-011）。</p>
     *
     * @throws IllegalStateException 当前状态不允许退回
     */
    public void markSupplement() {
        requireStatus(STATUS_IN_REVIEW, STATUS_SUPPLEMENT);
        this.status = STATUS_SUPPLEMENT;
    }

    /**
     * 形式审查不予受理：{@code IN_REVIEW} → {@code NOT_ACCEPTED}（终态）。
     *
     * @throws IllegalStateException 当前状态不允许不予受理
     */
    public void markNotAccepted() {
        requireStatus(STATUS_IN_REVIEW, STATUS_NOT_ACCEPTED);
        this.status = STATUS_NOT_ACCEPTED;
    }

    /**
     * 立项决策批准：{@code IN_REVIEW} → {@code APPROVED}（终态）。
     *
     * @throws IllegalStateException 当前状态不允许批准
     */
    public void markApproved() {
        requireStatus(STATUS_IN_REVIEW, STATUS_APPROVED);
        this.status = STATUS_APPROVED;
    }

    /**
     * 立项决策不批准：{@code IN_REVIEW} → {@code REJECTED}（终态）。
     *
     * @throws IllegalStateException 当前状态不允许拒绝
     */
    public void markRejected() {
        requireStatus(STATUS_IN_REVIEW, STATUS_REJECTED);
        this.status = STATUS_REJECTED;
    }

    /**
     * 申请人撤回：{@code IN_REVIEW} → {@code WITHDRAWN}。
     *
     * <p>已提交版本内容保持不变，仅审批实例取消（TC-APP-036）。</p>
     *
     * @throws IllegalStateException 当前状态不允许撤回
     */
    public void markWithdrawn() {
        requireStatus(STATUS_IN_REVIEW, STATUS_WITHDRAWN);
        this.status = STATUS_WITHDRAWN;
    }

    private void requireStatus(String expected, String target) {
        if (!expected.equals(status)) {
            throw new IllegalStateException("非法申报状态迁移：" + status + " → " + target);
        }
    }

    public String getApplicationNo() {
        return applicationNo;
    }

    public void setApplicationNo(String applicationNo) {
        this.applicationNo = applicationNo;
    }

    public String getBatchId() {
        return batchId;
    }

    public void setBatchId(String batchId) {
        this.batchId = batchId;
    }

    public String getProjectType() {
        return projectType;
    }

    public void setProjectType(String projectType) {
        this.projectType = projectType;
    }

    public String getApplicantId() {
        return applicantId;
    }

    public void setApplicantId(String applicantId) {
        this.applicantId = applicantId;
    }

    public String getDeptId() {
        return deptId;
    }

    public void setDeptId(String deptId) {
        this.deptId = deptId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
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
