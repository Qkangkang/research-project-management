package cn.edu.rpm.output.infrastructure.entity;

import cn.edu.rpm.common.entity.BaseEntity;
import cn.edu.rpm.output.domain.model.OutputTypes;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDate;

/**
 * {@code research_output} 成果主档 PO（T-OUT-001）。
 *
 * <p>对应《02-数据模型与数据库设计.md》§3.5 与 V01 基线 DDL。关键约束：</p>
 * <ul>
 *   <li>{@code ck_research_output_type}：PAPER / PATENT / SOFTWARE / STANDARD / AWARD / REPORT / OTHER；</li>
 *   <li>{@code ck_research_output_status}：DRAFT / SUBMITTED / APPROVED / RETURNED / REJECTED / REVOKED；</li>
 *   <li>{@code idx_output_doi}、{@code idx_output_patent_no}、{@code idx_output_type_status}：
 *       查重与列表查询走索引；</li>
 *   <li>{@code fk_research_output_current_version}：{@code current_version_id} 指向当前生效版本。</li>
 * </ul>
 *
 * <p>状态迁移只允许通过本类显式方法（禁止业务代码裸调 {@link #setStatus(String)}）；
 * 非法迁移抛 {@link IllegalStateException}，由应用服务转换为 {@code RPM-OUT-004}。
 * 撤回的持久化终态为 {@code REVOKED}（V02 物化视图与 09 §5.2 均以 {@code REVOKED}
 * 表达撤回；08 §14.1 的 {@code WITHDRAWN} 为文档措辞差异，DDL 不含该值）。</p>
 */
@TableName("research_output")
public class ResearchOutputEntity extends BaseEntity {

    /** 成果类型：论文。 */
    public static final String TYPE_PAPER = OutputTypes.PAPER;

    /** 成果类型：专利。 */
    public static final String TYPE_PATENT = OutputTypes.PATENT;

    /** 成果类型：软件著作权。 */
    public static final String TYPE_SOFTWARE = OutputTypes.SOFTWARE;

    /** 成果类型：标准。 */
    public static final String TYPE_STANDARD = OutputTypes.STANDARD;

    /** 成果类型：获奖。 */
    public static final String TYPE_AWARD = OutputTypes.AWARD;

    /** 成果类型：报告。 */
    public static final String TYPE_REPORT = OutputTypes.REPORT;

    /** 成果类型：其他。 */
    public static final String TYPE_OTHER = OutputTypes.OTHER;

    /** 状态：草稿。 */
    public static final String STATUS_DRAFT = "DRAFT";

    /** 状态：已提交审核。 */
    public static final String STATUS_SUBMITTED = "SUBMITTED";

    /** 状态：已审核通过（全院成果库可见）。 */
    public static final String STATUS_APPROVED = "APPROVED";

    /** 状态：审核退回，待修订。 */
    public static final String STATUS_RETURNED = "RETURNED";

    /** 状态：审核拒绝（终态，可修订后重新提交）。 */
    public static final String STATUS_REJECTED = "REJECTED";

    /** 状态：已撤回/撤销（仅审计可查，列表默认过滤）。 */
    public static final String STATUS_REVOKED = "REVOKED";

    /** 成果类型（ck_research_output_type）。 */
    @TableField("output_type")
    private String outputType;

    /** 成果标题（1～512 字符）。 */
    @TableField("title")
    private String title;

    /** DOI（可空；存在时须符合 DOI 语法）。 */
    @TableField("doi")
    private String doi;

    /** 专利号（可空；PATENT 类型必填）。 */
    @TableField("patent_no")
    private String patentNo;

    /** 完成人用户 ID（iam_user.id）。 */
    @TableField("owner_id")
    private String ownerId;

    /** 所属部门 ID（org_unit.id）。 */
    @TableField("dept_id")
    private String deptId;

    /** 完成日期（date；不得晚于当前日期）。 */
    @TableField("completed_at")
    private LocalDate completedAt;

    /** 成果状态（ck_research_output_status）。 */
    @TableField("status")
    private String status;

    /** 当前生效版本 ID（output_version.id）；草稿尚未固化时为初次版本。 */
    @TableField("current_version_id")
    private String currentVersionId;

    /**
     * 构造新的草稿成果（状态 {@code DRAFT}）。
     *
     * @param outputType  成果类型
     * @param title       标题（调用方已完成去空格与长度校验）
     * @param doi         DOI，可空
     * @param patentNo    专利号，可空
     * @param ownerId     完成人用户 ID（32 位 uuid 文本）
     * @param deptId      所属部门 ID（32 位 uuid 文本）
     * @param completedAt 完成日期
     * @return 草稿成果 PO
     */
    public static ResearchOutputEntity newDraft(String outputType, String title, String doi,
                                                String patentNo, String ownerId, String deptId,
                                                LocalDate completedAt) {
        ResearchOutputEntity output = new ResearchOutputEntity();
        output.outputType = requireText(outputType, "outputType");
        output.title = requireText(title, "title");
        output.doi = doi;
        output.patentNo = patentNo;
        output.ownerId = requireText(ownerId, "ownerId");
        output.deptId = requireText(deptId, "deptId");
        output.completedAt = completedAt;
        output.status = STATUS_DRAFT;
        output.setVersion(0);
        return output;
    }

    /**
     * 提交审核：{@code DRAFT} → {@code SUBMITTED}，并固定当前版本。
     *
     * @param versionId 提交固化的版本 ID
     * @throws IllegalArgumentException 版本 ID 为空
     * @throws IllegalStateException    当前状态不是草稿
     */
    public void submit(String versionId) {
        if (versionId == null || versionId.isBlank()) {
            throw new IllegalArgumentException("versionId 不能为空");
        }
        requireStatus("提交审核", STATUS_DRAFT);
        this.status = STATUS_SUBMITTED;
        this.currentVersionId = versionId;
    }

    /**
     * 审核通过：{@code SUBMITTED} → {@code APPROVED}，并绑定固定版本。
     *
     * @param versionId 审核绑定的版本 ID
     * @throws IllegalStateException 当前状态不是已提交
     */
    public void approve(String versionId) {
        if (versionId == null || versionId.isBlank()) {
            throw new IllegalArgumentException("versionId 不能为空");
        }
        requireStatus("审核通过", STATUS_SUBMITTED);
        this.status = STATUS_APPROVED;
        this.currentVersionId = versionId;
    }

    /**
     * 审核退回：{@code SUBMITTED} → {@code RETURNED}。
     *
     * @throws IllegalStateException 当前状态不是已提交
     */
    public void returnForRevision() {
        requireStatus("审核退回", STATUS_SUBMITTED);
        this.status = STATUS_RETURNED;
    }

    /**
     * 审核拒绝：{@code SUBMITTED} → {@code REJECTED}。
     *
     * @throws IllegalStateException 当前状态不是已提交
     */
    public void reject() {
        requireStatus("审核拒绝", STATUS_SUBMITTED);
        this.status = STATUS_REJECTED;
    }

    /**
     * 进入修订：非撤回成果回到 {@code DRAFT}，修订产物为新版本。
     *
     * @throws IllegalStateException 当前状态为未提交审核或已撤回
     */
    public void startRevision() {
        if (STATUS_SUBMITTED.equals(status) || STATUS_REVOKED.equals(status)) {
            throw new IllegalStateException("非法成果状态迁移：" + status + " → " + STATUS_DRAFT);
        }
        this.status = STATUS_DRAFT;
    }

    /**
     * 撤回成果（科研管理审核通过后调用）：任意非撤回状态 → {@code REVOKED}。
     *
     * <p>关联关系不物理删除；历史版本与证据保持不变，验收快照引用不受影响（RES-06）。</p>
     *
     * @throws IllegalStateException 成果已撤回
     */
    public void withdraw() {
        if (STATUS_REVOKED.equals(status)) {
            throw new IllegalStateException("成果已撤回，不允许重复迁移");
        }
        this.status = STATUS_REVOKED;
    }

    /**
     * 修订/保存草稿时同步成果头字段。
     *
     * @param title       标题
     * @param doi         DOI，可空
     * @param patentNo    专利号，可空
     * @param completedAt 完成日期
     */
    public void applyMetadata(String title, String doi, String patentNo, LocalDate completedAt) {
        this.title = requireText(title, "title");
        this.doi = doi;
        this.patentNo = patentNo;
        this.completedAt = completedAt;
    }

    /**
     * 是否已公开（全院成果库可见）。
     *
     * @return 状态为 {@code APPROVED} 返回 {@code true}
     */
    public boolean isPublic() {
        return STATUS_APPROVED.equals(status);
    }

    private void requireStatus(String action, String expected) {
        if (!expected.equals(status)) {
            throw new IllegalStateException("成果状态不允许" + action + "：当前 " + status);
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value;
    }

    public String getOutputType() {
        return outputType;
    }

    public void setOutputType(String outputType) {
        this.outputType = outputType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDoi() {
        return doi;
    }

    public void setDoi(String doi) {
        this.doi = doi;
    }

    public String getPatentNo() {
        return patentNo;
    }

    public void setPatentNo(String patentNo) {
        this.patentNo = patentNo;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public String getDeptId() {
        return deptId;
    }

    public void setDeptId(String deptId) {
        this.deptId = deptId;
    }

    public LocalDate getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDate completedAt) {
        this.completedAt = completedAt;
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
