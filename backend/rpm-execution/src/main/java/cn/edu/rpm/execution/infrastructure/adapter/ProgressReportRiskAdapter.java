package cn.edu.rpm.execution.infrastructure.adapter;

import cn.edu.rpm.execution.domain.port.ProgressReportRiskPort;
import cn.edu.rpm.project.domain.service.ProjectRiskService;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 周期报告逾期风险适配器（T-EXEC-006）。
 *
 * <p>委托 {@code rpm-project} 的风险领域服务生成风险，执行模块不直接写
 * {@code project_risk} 表。</p>
 */
@Component
public class ProgressReportRiskAdapter implements ProgressReportRiskPort {

    private final ProjectRiskService projectRiskService;

    /**
     * 构造适配器。
     *
     * @param projectRiskService 项目风险领域服务
     */
    public ProgressReportRiskAdapter(ProjectRiskService projectRiskService) {
        this.projectRiskService = Objects.requireNonNull(projectRiskService,
                "projectRiskService 不能为空");
    }

    @Override
    public void raiseLateReportRisk(String projectId, String reportId, String versionId,
                                    long daysLate) {
        projectRiskService.raiseLateReportRisk(projectId, reportId, versionId, daysLate);
    }
}
