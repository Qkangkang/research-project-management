package cn.edu.rpm.application.infrastructure.entity;

import cn.edu.rpm.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * {@code application_member} 申报团队成员 PO（T-APP-005）。
 *
 * <p>对应《02-数据模型与数据库设计.md》§3.2 与 V01 基线 DDL（V04 补齐审计列）。
 * 关键约束：</p>
 * <ul>
 *   <li>{@code uk_application_member (application_version_id, user_id, member_role)}：
 *       同一版本内同一成员同一角色唯一；</li>
 *   <li>{@code idx_application_member_version (application_version_id)}：按版本批量读取成员快照；</li>
 *   <li>成员随版本固化：提交时写入快照，历史版本成员不被覆盖。</li>
 * </ul>
 *
 * <p>审计字段（含 V04 追加的 updated_at / created_by / updated_by / version）由
 * {@link BaseEntity} 承载。</p>
 */
@TableName("application_member")
public class ApplicationMemberEntity extends BaseEntity {

    /** 所属申报版本 ID（application_version.id，ON DELETE RESTRICT）。 */
    @TableField("application_version_id")
    private String applicationVersionId;

    /** 成员用户 ID（iam_user.id）。 */
    @TableField("user_id")
    private String userId;

    /** 成员角色（varchar(32)，如 PI / CO_AUTHOR / ADVISOR；取值由业务配置约束）。 */
    @TableField("member_role")
    private String memberRole;

    /** 贡献说明；可空。 */
    @TableField("contribution_desc")
    private String contributionDesc;

    /** 展示排序（升序）；默认 0。 */
    @TableField("sort_order")
    private Integer sortOrder;

    public String getApplicationVersionId() {
        return applicationVersionId;
    }

    public void setApplicationVersionId(String applicationVersionId) {
        this.applicationVersionId = applicationVersionId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getMemberRole() {
        return memberRole;
    }

    public void setMemberRole(String memberRole) {
        this.memberRole = memberRole;
    }

    public String getContributionDesc() {
        return contributionDesc;
    }

    public void setContributionDesc(String contributionDesc) {
        this.contributionDesc = contributionDesc;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }
}
