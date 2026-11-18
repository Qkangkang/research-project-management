package cn.edu.rpm.iam.application.dto;

import java.util.List;

/**
 * 当前登录用户信息 VO（V10 认证模块）。
 *
 * <p>与前端 src/api/types.ts UserInfo 对齐。</p>
 */
public class UserInfoVO {

    private String userId;
    private String username;      // 工号
    private String realName;      // 显示姓名
    private List<String> roles;   // 角色 code 列表
    private List<String> permissions; // 权限 code 列表（前端菜单/按钮控制用）
    private String departmentId;
    private String departmentName;
    private String email;

    public UserInfoVO() {}

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getRealName() { return realName; }
    public void setRealName(String realName) { this.realName = realName; }

    public List<String> getRoles() { return roles; }
    public void setRoles(List<String> roles) { this.roles = roles; }

    public List<String> getPermissions() { return permissions; }
    public void setPermissions(List<String> permissions) { this.permissions = permissions; }

    public String getDepartmentId() { return departmentId; }
    public void setDepartmentId(String departmentId) { this.departmentId = departmentId; }

    public String getDepartmentName() { return departmentName; }
    public void setDepartmentName(String departmentName) { this.departmentName = departmentName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}
