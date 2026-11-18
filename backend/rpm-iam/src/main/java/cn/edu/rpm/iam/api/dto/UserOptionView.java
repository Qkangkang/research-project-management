package cn.edu.rpm.iam.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 用户下拉选项。
 *
 * @param id          用户 ID
 * @param employeeNo  工号
 * @param displayName 姓名
 * @param deptId      所属部门 ID
 * @param deptName    所属部门名称
 * @param status      账号状态
 */
public record UserOptionView(
        @JsonProperty("id") String id,
        @JsonProperty("employee_no") String employeeNo,
        @JsonProperty("display_name") String displayName,
        @JsonProperty("dept_id") String deptId,
        @JsonProperty("dept_name") String deptName,
        @JsonProperty("status") String status
) {
}
