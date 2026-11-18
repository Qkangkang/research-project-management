package cn.edu.rpm.application.domain.model;

/**
 * 立项创建结果（T-APP-016；技术文档 04 §3.4）。
 *
 * <p>审批决策响应由此结果组装：{@code application_id} 由调用方补充，
 * {@code project_id / project_no / baseline_id} 原样返回。</p>
 *
 * @param projectId  新生成（或已存在）的项目 ID
 * @param projectNo  项目编号（{@code KY-{年}-{6位}}）
 * @param baselineId 初始基线 ID
 */
public record ProjectCreationResult(
        String projectId,
        String projectNo,
        String baselineId
) {
}
