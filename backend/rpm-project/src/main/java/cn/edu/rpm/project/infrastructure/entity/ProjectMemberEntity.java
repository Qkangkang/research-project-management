package cn.edu.rpm.project.infrastructure.entity;

import cn.edu.rpm.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * {@code project_member} 项目成员 PO（T-PRJ-002）。
 *
 * <p>有效成员判定：{@code status = ACTIVE} 且 {@code valid_from <= at} 且
 * （{@code valid_to} 为空或 {@code valid_to > at}）。有效期重叠由数据库
 * EXCLUDE 约束 {@code exc_project_member_overlap} 兜底（V04），仓储不重复实现区间校验。</p>
 */
@TableName("project_member")
public class ProjectMemberEntity extends BaseEntity {

    /** 成员状态：有效。 */
    public static final String STATUS_ACTIVE = "ACTIVE";

    /** 成员状态：失效。 */
    public static final String STATUS_INACTIVE = "INACTIVE";

    /** 项目 ID（project.id）。 */
    @TableField("project_id")
    private String projectId;

    /** 成员用户 ID（iam_user.id）。 */
    @TableField("user_id")
    private String userId;

    /** 项目内角色（PI / MEMBER / ASSISTANT 等，取值见 RoleCodes）。 */
    @TableField("project_role")
    private String projectRole;

    /** 贡献说明（≤1024 字符）。 */
    @TableField("contribution_desc")
    private String contributionDesc;

    /** 有效期起（含）。 */
    @TableField("valid_from")
    private OffsetDateTime validFrom;

    /** 有效期止（不含）；为空表示长期有效。 */
    @TableField("valid_to")
    private OffsetDateTime validTo;

    /** 成员状态：ACTIVE / INACTIVE（ck_project_member_status）。 */
    @TableField("status")
    private String status;

    /**
     * 将成员置为失效：写入 {@code valid_to} 并置 {@code INACTIVE}。
     *
     * @param at 失效时间
     * @throws IllegalStateException 当前状态不是 ACTIVE
     */
    public void deactivate(OffsetDateTime at) {
        if (!STATUS_ACTIVE.equals(status)) {
            throw new IllegalStateException("非法成员状态迁移：" + status + " → " + STATUS_INACTIVE);
        }
        this.status = STATUS_INACTIVE;
        this.validTo = at;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getProjectRole() {
        return projectRole;
    }

    public void setProjectRole(String projectRole) {
        this.projectRole = projectRole;
    }

    public String getContributionDesc() {
        return contributionDesc;
    }

    public void setContributionDesc(String contributionDesc) {
        this.contributionDesc = contributionDesc;
    }

    public OffsetDateTime getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(OffsetDateTime validFrom) {
        this.validFrom = validFrom;
    }

    public OffsetDateTime getValidTo() {
        return validTo;
    }

    public void setValidTo(OffsetDateTime validTo) {
        this.validTo = validTo;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
