package cn.edu.rpm.output.application.dto;

import java.time.LocalDate;

/**
 * 成果列表行视图（T-OUT-014）。
 *
 * @param outputId    成果 ID
 * @param outputType  成果类型
 * @param title       标题
 * @param doi         DOI，可空
 * @param patentNo    专利号，可空
 * @param status      成果状态
 * @param ownerId     完成人用户 ID
 * @param deptId      所属部门 ID
 * @param completedAt 完成日期
 */
public record OutputSummaryVO(
        String outputId,
        String outputType,
        String title,
        String doi,
        String patentNo,
        String status,
        String ownerId,
        String deptId,
        LocalDate completedAt
) {
}
