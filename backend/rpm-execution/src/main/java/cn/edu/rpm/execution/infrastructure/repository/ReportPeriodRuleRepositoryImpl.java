package cn.edu.rpm.execution.infrastructure.repository;

import cn.edu.rpm.execution.infrastructure.entity.ReportPeriodRuleEntity;
import cn.edu.rpm.execution.infrastructure.mapper.ReportPeriodRuleMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * {@code report_period_rule} 报告周期规则仓储实现（T-EXEC-005 支撑）。
 */
@Repository
public class ReportPeriodRuleRepositoryImpl implements ReportPeriodRuleRepository {

    private final ReportPeriodRuleMapper reportPeriodRuleMapper;

    public ReportPeriodRuleRepositoryImpl(ReportPeriodRuleMapper reportPeriodRuleMapper) {
        this.reportPeriodRuleMapper = reportPeriodRuleMapper;
    }

    @Override
    public void insert(ReportPeriodRuleEntity rule) {
        reportPeriodRuleMapper.insert(rule);
    }

    @Override
    public Optional<ReportPeriodRuleEntity> findActive(String projectType, String periodType) {
        return Optional.ofNullable(reportPeriodRuleMapper.selectOne(
                new LambdaQueryWrapper<ReportPeriodRuleEntity>()
                        .eq(ReportPeriodRuleEntity::getProjectType, projectType)
                        .eq(ReportPeriodRuleEntity::getPeriodType, periodType)
                        .eq(ReportPeriodRuleEntity::getStatus, ReportPeriodRuleEntity.STATUS_ACTIVE)));
    }

    @Override
    public int update(ReportPeriodRuleEntity rule) {
        return reportPeriodRuleMapper.updateById(rule);
    }
}
