package cn.edu.rpm.project.application.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 里程碑延期申请命令（T-PRJ-016；技术文档 08 §6.1）。
 *
 * @param requestedPlanDate 申请延期到的新计划日期（必填，必须晚于原 {@code plan_date}）
 * @param reason            延期理由（必填，≥10 字）
 * @param evidenceFileIds   证据文件 ID（可选）
 */
public record MilestoneDelayRequestCmd(
        LocalDate requestedPlanDate,
        String reason,
        List<String> evidenceFileIds
) {

    /** 规范化证据列表。 */
    public MilestoneDelayRequestCmd {
        evidenceFileIds = evidenceFileIds == null ? List.of() : List.copyOf(evidenceFileIds);
    }
}
