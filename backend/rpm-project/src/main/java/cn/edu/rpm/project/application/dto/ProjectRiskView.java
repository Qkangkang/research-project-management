package cn.edu.rpm.project.application.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 项目风险视图（T-PRJ-015）。
 *
 * @param riskId      风险 ID
 * @param projectId   项目 ID
 * @param ownerId     责任人用户 ID
 * @param riskType    风险类型
 * @param title       风险标题
 * @param description 风险描述
 * @param status      风险状态
 * @param riskLevel   风险等级
 * @param dueDate     处置截止日期
 * @param resolution  处置结果
 * @param closedAt    关闭时间
 * @param createdAt   创建时间
 */
public record ProjectRiskView(
        String riskId,
        String projectId,
        String ownerId,
        String riskType,
        String title,
        String description,
        String status,
        String riskLevel,
        LocalDate dueDate,
        String resolution,
        OffsetDateTime closedAt,
        OffsetDateTime createdAt
) {

    /**
     * 由风险实体构造视图。
     *
     * @param risk 风险实体
     * @return 风险视图
     */
    public static ProjectRiskView from(cn.edu.rpm.project.infrastructure.entity.ProjectRiskEntity risk) {
        return new ProjectRiskView(
                risk.getId(),
                risk.getProjectId(),
                risk.getOwnerId(),
                risk.getRiskType(),
                risk.getTitle(),
                risk.getDescription(),
                risk.getStatus(),
                risk.getRiskLevel(),
                risk.getDueDate(),
                risk.getResolution(),
                risk.getClosedAt(),
                risk.getCreatedAt()
        );
    }
}
