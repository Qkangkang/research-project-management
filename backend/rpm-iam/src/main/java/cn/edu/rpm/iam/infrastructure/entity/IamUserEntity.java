package cn.edu.rpm.iam.infrastructure.entity;

import cn.edu.rpm.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * {@code iam_user} 人员账号 PO（T-IAM-001）。
 *
 * <p>对应《02-数据模型与数据库设计.md》§3.1 与 V01 基线 DDL。字段与列逐一映射：</p>
 * <ul>
 *   <li>{@code employee_no} 工号全局唯一（uk_iam_user_employee_no）；</li>
 *   <li>{@code sso_subject} 院方 SSO 唯一标识（uk_iam_user_sso_subject），SSO 登录落账匹配键，
 *       若院方使用 LDAP uid 或 OIDC sub，由集成层映射后写入；</li>
 *   <li>{@code mobile_masked} / {@code id_card_masked} 为脱敏展示列，密文列
 *       {@code mobile_encrypted} / {@code id_card_encrypted} 仅在字段级授权解密链路使用；</li>
 *   <li>{@code status} 枚举：ACTIVE / LEAVED / DISABLED / LOCKED（ck_iam_user_status）；</li>
 *   <li>审计字段（created_at / updated_at / created_by / updated_by / version）由 {@link BaseEntity} 承载。</li>
 * </ul>
 */
@TableName("iam_user")
public class IamUserEntity extends BaseEntity {

    /** 工号；全局唯一（uk_iam_user_employee_no）。 */
    @TableField("employee_no")
    private String employeeNo;

    /** 显示姓名。 */
    @TableField("display_name")
    private String displayName;

    /** 所属部门组织 ID（org_unit.id，ON DELETE RESTRICT）。 */
    @TableField("dept_id")
    private String deptId;

    /** 电子邮箱；可空。 */
    @TableField("email")
    private String email;

    /** 联系方式脱敏展示值。 */
    @TableField("mobile_masked")
    private String mobileMasked;

    /** 联系方式密文（字段级授权解密）。 */
    @TableField("mobile_encrypted")
    private String mobileEncrypted;

    /** 身份证号脱敏展示值。 */
    @TableField("id_card_masked")
    private String idCardMasked;

    /** 身份证号密文（字段级授权解密）。 */
    @TableField("id_card_encrypted")
    private String idCardEncrypted;

    /** 院方 SSO 唯一标识（uk_iam_user_sso_subject）；可空，未绑定 SSO 时为 null。 */
    @TableField("sso_subject")
    private String ssoSubject;

    /** 账号状态：ACTIVE / LEAVED / DISABLED / LOCKED（ck_iam_user_status）。 */
    @TableField("status")
    private String status;

    /** 密码 BCrypt 哈希值；本地账号必填，SSO 账号为 null（V10 新增）。 */
    @TableField("password_hash")
    private String passwordHash;

    /** 最近登录时间；可空。 */
    @TableField("last_login_at")
    private OffsetDateTime lastLoginAt;

    public String getEmployeeNo() {
        return employeeNo;
    }

    public void setEmployeeNo(String employeeNo) {
        this.employeeNo = employeeNo;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getDeptId() {
        return deptId;
    }

    public void setDeptId(String deptId) {
        this.deptId = deptId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getMobileMasked() {
        return mobileMasked;
    }

    public void setMobileMasked(String mobileMasked) {
        this.mobileMasked = mobileMasked;
    }

    public String getMobileEncrypted() {
        return mobileEncrypted;
    }

    public void setMobileEncrypted(String mobileEncrypted) {
        this.mobileEncrypted = mobileEncrypted;
    }

    public String getIdCardMasked() {
        return idCardMasked;
    }

    public void setIdCardMasked(String idCardMasked) {
        this.idCardMasked = idCardMasked;
    }

    public String getIdCardEncrypted() {
        return idCardEncrypted;
    }

    public void setIdCardEncrypted(String idCardEncrypted) {
        this.idCardEncrypted = idCardEncrypted;
    }

    public String getSsoSubject() {
        return ssoSubject;
    }

    public void setSsoSubject(String ssoSubject) {
        this.ssoSubject = ssoSubject;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public OffsetDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(OffsetDateTime lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }
}
