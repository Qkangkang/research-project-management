package cn.edu.rpm.execution.infrastructure.repository;

import cn.edu.rpm.execution.infrastructure.entity.ReportPeriodRuleEntity;
import cn.edu.rpm.execution.infrastructure.mapper.ReportPeriodRuleMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ReportPeriodRuleRepositoryImpl} 纯单元测试（T-EXEC-005 支撑）。
 */
@ExtendWith(MockitoExtension.class)
class ReportPeriodRuleRepositoryImplTest {

    @Mock
    private ReportPeriodRuleMapper reportPeriodRuleMapper;

    @Captor
    private ArgumentCaptor<LambdaQueryWrapper<ReportPeriodRuleEntity>> wrapperCaptor;

    private ReportPeriodRuleRepositoryImpl repository;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ReportPeriodRuleEntity.class);
    }

    @BeforeEach
    void setUp() {
        repository = new ReportPeriodRuleRepositoryImpl(reportPeriodRuleMapper);
    }

    @Test
    void insertKeepsRuleWindow() {
        ReportPeriodRuleEntity rule = new ReportPeriodRuleEntity();
        rule.setProjectType("YEAR_PLAN");
        rule.setPeriodType("MONTH");
        rule.setDueOffsetDays(5);
        rule.setLateGraceDays(3);
        rule.setStatus(ReportPeriodRuleEntity.STATUS_ACTIVE);

        repository.insert(rule);

        verify(reportPeriodRuleMapper).insert(rule);
    }

    @Test
    void findActiveFiltersProjectTypePeriodTypeAndActiveStatus() {
        ReportPeriodRuleEntity rule = new ReportPeriodRuleEntity();
        rule.setDueOffsetDays(5);
        when(reportPeriodRuleMapper.selectOne(
                ArgumentMatchers.<Wrapper<ReportPeriodRuleEntity>>any())).thenReturn(rule);

        assertThat(repository.findActive("YEAR_PLAN", "MONTH"))
                .get().extracting(ReportPeriodRuleEntity::getDueOffsetDays).isEqualTo(5);

        verify(reportPeriodRuleMapper).selectOne(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment())
                .contains("project_type =", "period_type =", "status =");
        assertThat(wrapperCaptor.getValue().getParamNameValuePairs().values())
                .contains("YEAR_PLAN", "MONTH", ReportPeriodRuleEntity.STATUS_ACTIVE);
    }

    @Test
    void findActiveReturnsEmptyWhenRuleMissing() {
        when(reportPeriodRuleMapper.selectOne(
                ArgumentMatchers.<Wrapper<ReportPeriodRuleEntity>>any())).thenReturn(null);

        assertThat(repository.findActive("YEAR_PLAN", "QUARTER")).isEmpty();
    }
}
