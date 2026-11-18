package cn.edu.rpm.project.application.dto;

import java.time.OffsetDateTime;

/**
 * 新增项目成员命令（T-PRJ-013；技术文档 04 §2.1、07 §3.1）。
 *
 * @param userId           成员用户 ID
 * @param projectRole      项目内角色（如 MEMBER / ASSISTANT）
 * @param contributionDesc 贡献说明（≤1024 字符，可空）
 * @param validFrom        有效期起（空取当前时间）
 * @param validTo          有效期止（空表示长期有效）
 */
public record AddProjectMemberCmd(
        String userId,
        String projectRole,
        String contributionDesc,
        OffsetDateTime validFrom,
        OffsetDateTime validTo
) {
}
