package cn.edu.rpm.iam.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 登录命令（V10 认证模块）。
 */
public class LoginCmd {

    @NotBlank(message = "工号不能为空")
    @Size(max = 64, message = "工号长度不能超过 64 位")
    private String employeeNo;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 128, message = "密码长度 6-128 位")
    private String password;

    public String getEmployeeNo() {
        return employeeNo;
    }

    public void setEmployeeNo(String employeeNo) {
        this.employeeNo = employeeNo;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
