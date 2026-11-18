package cn.edu.rpm.execution.domain.port;

/**
 * 周期报告逾期风险端口（T-EXEC-006；技术文档 04 §3.8 步骤 5）。
 *
 * <p>定义在 {@code rpm-execution}，适配器调用 {@code rpm-project} 的风险服务，
 * 避免执行模块直接写 {@code project_risk} 表。</p>
 */
public interface ProgressReportRiskPort {

    /**
     * 为迟报生成逾期风险（同一报告幂等）。
     *
     * @param projectId 项目 ID
     * @param reportId  报告 ID
     * @param versionId 报告版本 ID
     * @param daysLate  逾期天数
     */
    void raiseLateReportRisk(String projectId, String reportId, String versionId, long daysLate);
}
