package cn.edu.rpm.project.application.dto;

/**
 * 项目状态迁移结果（T-PRJ-012）。
 *
 * @param projectId 项目 ID
 * @param status    迁移后的项目状态
 * @param version   迁移后的乐观锁版本号
 */
public record ProjectStateChanged(String projectId, String status, int version) {
}
