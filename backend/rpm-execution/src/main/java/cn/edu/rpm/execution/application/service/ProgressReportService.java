package cn.edu.rpm.execution.application.service;

import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.execution.application.dto.ProgressReportCreated;
import cn.edu.rpm.execution.infrastructure.entity.ProgressReportEntity;
import cn.edu.rpm.execution.infrastructure.entity.ReportPeriodRuleEntity;
import cn.edu.rpm.execution.infrastructure.repository.ProgressReportRepository;
import cn.edu.rpm.execution.infrastructure.repository.ReportPeriodRuleRepository;
import cn.edu.rpm.project.infrastructure.entity.ProjectEntity;
import cn.edu.rpm.project.infrastructure.repository.ProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 周期报告创建服务（T-EXEC-005；技术文档 04 §3.8、07 §3.2）。
 *
 * <p>按项目 + {@code period_type} + {@code period_key} 创建唯一报告（数据库
 * {@code uk_progress_report_period} 兜底），初始状态 {@code DRAFT}。到期日按
 * {@code report_period_rule} 的 {@code due_offset_days} 计算：周期结束日 + 偏移天数；
 * 无规则时默认 5 天（PRD SYS-02「月报每月 5 日到期」）。</p>
 *
 * <p>本服务不承载接口授权：报告创建端点（{@code execution:report:edit}）的授权由
 * Batch-15 的 Controller/应用服务入口完成；本服务供其与后台生成任务复用。</p>
 */
@Service
public class ProgressReportService {

    private static final Logger LOG = LoggerFactory.getLogger(ProgressReportService.class);

    /** 无周期规则时的默认到期偏移天数。 */
    static final int DEFAULT_DUE_OFFSET_DAYS = 5;

    private static final Pattern QUARTER_KEY = Pattern.compile("^(\\d{4})-Q([1-4])$");

    private final ProjectRepository projectRepository;

    private final ProgressReportRepository progressReportRepository;

    private final ReportPeriodRuleRepository reportPeriodRuleRepository;

    /**
     * 构造报告服务。
     *
     * @param projectRepository         项目仓储（存在性校验与项目类型）
     * @param progressReportRepository  报告仓储
     * @param reportPeriodRuleRepository 报告周期规则仓储
     */
    public ProgressReportService(ProjectRepository projectRepository,
                                 ProgressReportRepository progressReportRepository,
                                 ReportPeriodRuleRepository reportPeriodRuleRepository) {
        this.projectRepository = Objects.requireNonNull(projectRepository, "projectRepository 不能为空");
        this.progressReportRepository = Objects.requireNonNull(progressReportRepository,
                "progressReportRepository 不能为空");
        this.reportPeriodRuleRepository = Objects.requireNonNull(reportPeriodRuleRepository,
                "reportPeriodRuleRepository 不能为空");
    }

    /**
     * 创建报告草稿（TC-EXEC-006 / TC-EXEC-007）。
     *
     * @param projectId  项目 ID
     * @param periodType 周期类型（MONTH / QUARTER）
     * @param periodKey  周期键（月报 {@code yyyy-MM}，季报 {@code yyyy-Qn}）
     * @return 报告 ID、周期键、状态与到期日
     * @throws BizException 项目不存在 {@code RPM-PROJ-001}；参数非法 {@code RPM-COMMON-400}；
     *                      同周期报告已存在 {@code RPM-EXEC-001}
     */
    @Transactional
    public ProgressReportCreated create(String projectId, String periodType, String periodKey) {
        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BizException(ErrorCode.PROJ_001));

        String type = requirePeriodType(periodType);
        String key = requirePeriodKey(type, periodKey);
        LocalDate periodEnd = periodEnd(type, key);

        if (progressReportRepository.findByProjectIdAndPeriod(projectId, type, key).isPresent()) {
            throw new BizException(ErrorCode.EXEC_001);
        }

        int offsetDays = reportPeriodRuleRepository.findActive(project.getProjectType(), type)
                .map(ReportPeriodRuleEntity::getDueOffsetDays)
                .filter(days -> days != null && days >= 0)
                .orElse(DEFAULT_DUE_OFFSET_DAYS);
        LocalDate dueDate = periodEnd.plusDays(offsetDays);

        ProgressReportEntity report = new ProgressReportEntity();
        report.setProjectId(projectId);
        report.setPeriodType(type);
        report.setPeriodKey(key);
        report.setDueDate(dueDate);
        report.setStatus(ProgressReportEntity.STATUS_DRAFT);
        try {
            progressReportRepository.insert(report);
        } catch (DuplicateKeyException ex) {
            LOG.warn("周期报告唯一键冲突 projectId={} periodType={} periodKey={}",
                    projectId, type, key);
            throw new BizException(ErrorCode.EXEC_001);
        }
        return new ProgressReportCreated(report.getId(), type, key, report.getStatus(), dueDate);
    }

    private static String requirePeriodType(String periodType) {
        if (!ProgressReportEntity.PERIOD_MONTH.equals(periodType)
                && !ProgressReportEntity.PERIOD_QUARTER.equals(periodType)) {
            throw new BizException(ErrorCode.COMMON_400, "周期类型必须为 MONTH 或 QUARTER");
        }
        return periodType;
    }

    private static String requirePeriodKey(String periodType, String periodKey) {
        if (periodKey == null || periodKey.isBlank()) {
            throw new BizException(ErrorCode.COMMON_400, "周期键不能为空");
        }
        String key = periodKey.trim();
        if (ProgressReportEntity.PERIOD_MONTH.equals(periodType)) {
            try {
                YearMonth.parse(key);
            } catch (RuntimeException ex) {
                throw new BizException(ErrorCode.COMMON_400, "月报周期键格式应为 yyyy-MM");
            }
        } else if (!QUARTER_KEY.matcher(key).matches()) {
            throw new BizException(ErrorCode.COMMON_400, "季报周期键格式应为 yyyy-Qn");
        }
        return key;
    }

    private static LocalDate periodEnd(String periodType, String periodKey) {
        if (ProgressReportEntity.PERIOD_MONTH.equals(periodType)) {
            return YearMonth.parse(periodKey).atEndOfMonth();
        }
        Matcher matcher = QUARTER_KEY.matcher(periodKey);
        if (!matcher.matches()) {
            throw new BizException(ErrorCode.COMMON_400, "季报周期键格式应为 yyyy-Qn");
        }
        int year = Integer.parseInt(matcher.group(1));
        int quarter = Integer.parseInt(matcher.group(2));
        return YearMonth.of(year, quarter * 3).atEndOfMonth();
    }
}
