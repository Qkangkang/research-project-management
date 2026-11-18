package cn.edu.rpm.execution.infrastructure.repository;

import cn.edu.rpm.execution.infrastructure.entity.ReportPeriodRuleEntity;

import java.util.Optional;

/**
 * {@code report_period_rule} 报告周期规则仓储（T-EXEC-005 支撑）。
 */
public interface ReportPeriodRuleRepository {

    /**
     * 插入规则。
     *
     * @param rule 待插入的规则 PO
     */
    void insert(ReportPeriodRuleEntity rule);

    /**
     * 按项目类型 + 周期类型查询启用规则（uk_report_period_rule）。
     *
     * @param projectType 项目类型
     * @param periodType  周期类型
     * @return 启用规则；未配置返回空
     */
    Optional<ReportPeriodRuleEntity> findActive(String projectType, String periodType);

    /**
     * 按乐观锁更新规则。
     *
     * @param rule 携带主键与旧版本的规则 PO
     * @return 受影响行数；0 表示版本冲突或记录不存在
     */
    int update(ReportPeriodRuleEntity rule);
}
