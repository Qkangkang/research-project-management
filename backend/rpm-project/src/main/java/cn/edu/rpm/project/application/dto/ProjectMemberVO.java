package cn.edu.rpm.project.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;

/**
 * 项目成员视图（T-PRJ-013；TC-PRJ-018 要求只返回有效期内成员并脱敏联系方式）。
 *
 * <p>使用可变 JavaBean 以支持字段脱敏（{@code FieldMaskService} 按字段名
 * {@code mobile} 施加联系方式策略）。</p>
 */
public class ProjectMemberVO {

    @JsonProperty("member_id")
    private String memberId;

    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("display_name")
    private String displayName;

    @JsonProperty("project_role")
    private String projectRole;

    @JsonProperty("contribution_desc")
    private String contributionDesc;

    @JsonProperty("valid_from")
    private OffsetDateTime validFrom;

    @JsonProperty("valid_to")
    private OffsetDateTime validTo;

    @JsonProperty("status")
    private String status;

    /** 联系方式：只返回库内已脱敏值，禁止返回明文。 */
    @JsonProperty("mobile")
    private String mobile;

    public String getMemberId() {
        return memberId;
    }

    public void setMemberId(String memberId) {
        this.memberId = memberId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
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

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }
}
