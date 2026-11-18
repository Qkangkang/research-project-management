package cn.edu.rpm.application.application.dto;

/**
 * 创建申报草稿命令（应用层入参，Controller 由 API DTO 转换）。
 *
 * @param batchId     申报批次 ID
 * @param projectType 项目类型
 * @param title       申报标题（服务端去首尾空格）
 * @param deptId      申报部门 ID
 */
public record CreateApplicationCmd(
        String batchId,
        String projectType,
        String title,
        String deptId
) {
}
