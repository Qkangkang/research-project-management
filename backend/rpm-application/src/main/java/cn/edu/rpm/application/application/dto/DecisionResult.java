package cn.edu.rpm.application.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 立项决策结果（T-APP-019；技术文档 04 §3.4 Response 形状）。
 *
 * <p>{@code REJECT} 时 {@code project_id / project_no / baseline_id} 为 {@code null}，
 * 序列化时省略。</p>
 *
 * @param applicationId 申报 ID
 * @param projectId     项目 ID（APPROVE 时非空）
 * @param projectNo     项目编号（APPROVE 时非空）
 * @param baselineId    初始基线 ID（APPROVE 时非空）
 * @param status        决策后申报状态
 */
public record DecisionResult(
        @JsonProperty("application_id") String applicationId,
        @JsonProperty("project_id") @JsonInclude(JsonInclude.Include.NON_NULL) String projectId,
        @JsonProperty("project_no") @JsonInclude(JsonInclude.Include.NON_NULL) String projectNo,
        @JsonProperty("baseline_id") @JsonInclude(JsonInclude.Include.NON_NULL) String baselineId,
        @JsonProperty("status") String status
) {
}
