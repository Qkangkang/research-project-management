package cn.edu.rpm.application.application.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 立项决策命令（T-APP-019；技术文档 04 §3.4）。
 *
 * @param versionId         被决策的申报版本 ID（必须是当前提交版本）
 * @param decision          决策：{@code APPROVE} / {@code REJECT}
 * @param comment           决策意见（拒绝时必填）
 * @param approvedStartDate 批准开始日期（APPROVE 时必填）
 * @param approvedEndDate   批准结束日期（APPROVE 时必填）
 * @param requestId         请求幂等键
 */
public record DecisionCmd(
        String versionId,
        String decision,
        String comment,
        LocalDate approvedStartDate,
        LocalDate approvedEndDate,
        UUID requestId
) {
}
