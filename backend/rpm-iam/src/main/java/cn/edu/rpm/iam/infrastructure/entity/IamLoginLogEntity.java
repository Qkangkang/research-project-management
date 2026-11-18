package cn.edu.rpm.iam.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * {@code iam_login_log} 登录日志 PO（V10 认证模块）。
 *
 * <p>只追加，禁止修改或删除（审计要求）。</p>
 */
@TableName("iam_login_log")
public class IamLoginLogEntity {

    @TableId("id")
    private String id;

    @TableField("user_id")
    private String userId;

    @TableField("employee_no")
    private String employeeNo;

    @TableField("login_type")
    private String loginType;

    @TableField("login_ip")
    private String loginIp;

    @TableField("user_agent")
    private String userAgent;

    @TableField("login_result")
    private String loginResult;

    @TableField("failure_reason")
    private String failureReason;

    @TableField("login_at")
    private OffsetDateTime loginAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getEmployeeNo() { return employeeNo; }
    public void setEmployeeNo(String employeeNo) { this.employeeNo = employeeNo; }

    public String getLoginType() { return loginType; }
    public void setLoginType(String loginType) { this.loginType = loginType; }

    public String getLoginIp() { return loginIp; }
    public void setLoginIp(String loginIp) { this.loginIp = loginIp; }

    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }

    public String getLoginResult() { return loginResult; }
    public void setLoginResult(String loginResult) { this.loginResult = loginResult; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

    public OffsetDateTime getLoginAt() { return loginAt; }
    public void setLoginAt(OffsetDateTime loginAt) { this.loginAt = loginAt; }
}
