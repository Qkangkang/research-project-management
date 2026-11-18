package cn.edu.rpm.project.infrastructure.repository;

import cn.edu.rpm.project.infrastructure.entity.ProjectRiskEntity;
import cn.edu.rpm.project.infrastructure.mapper.ProjectRiskMapper;
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

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ProjectRiskRepositoryImpl} 纯单元测试（T-PRJ-006 先行测试）。
 *
 * <p>覆盖：登记 OPEN；逾期扫描只取未结状态；按规则编码幂等查询；
 * 等级升级只允许未结风险（TC-EXEC-019/020 支撑）。</p>
 */
@ExtendWith(MockitoExtension.class)
class ProjectRiskRepositoryImplTest {

    private static final String PROJECT_ID = "8d7c6b5a4e3f4a2b9c1d0e2f3a4b5c6d";
    private static final String OWNER_ID = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    @Mock
    private ProjectRiskMapper projectRiskMapper;

    @Captor
    private ArgumentCaptor<LambdaQueryWrapper<ProjectRiskEntity>> wrapperCaptor;

    private ProjectRiskRepositoryImpl repository;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ProjectRiskEntity.class);
    }

    @BeforeEach
    void setUp() {
        repository = new ProjectRiskRepositoryImpl(projectRiskMapper);
    }

    @Test
    void insertKeepsOpenStatus() {
        ProjectRiskEntity risk = new ProjectRiskEntity();
        risk.setProjectId(PROJECT_ID);
        risk.setRiskType("PROGRESS");
        risk.setTitle("联调进度滞后");
        risk.setRiskLevel(ProjectRiskEntity.LEVEL_HIGH);
        risk.setOwnerId(OWNER_ID);
        risk.setStatus(ProjectRiskEntity.STATUS_OPEN);

        repository.insert(risk);

        verify(projectRiskMapper).insert(risk);
        assertThat(risk.getStatus()).isEqualTo(ProjectRiskEntity.STATUS_OPEN);
    }

    @Test
    void findOpenDueByOnlyIncludesOpenAndProcessing() {
        when(projectRiskMapper.selectList(ArgumentMatchers.<Wrapper<ProjectRiskEntity>>any()))
                .thenReturn(List.of());

        repository.findOpenDueBy(LocalDate.of(2026, 12, 31));

        verify(projectRiskMapper).selectList(wrapperCaptor.capture());
        String sql = wrapperCaptor.getValue().getSqlSegment();
        assertThat(sql).contains("due_date <=");
        assertThat(sql).containsIgnoringCase("status in");
        assertThat(wrapperCaptor.getValue().getParamNameValuePairs().values())
                .contains(ProjectRiskEntity.STATUS_OPEN, ProjectRiskEntity.STATUS_PROCESSING)
                .doesNotContain(ProjectRiskEntity.STATUS_CLOSED);
    }

    @Test
    void findOverdueByOwnerFiltersOwner() {
        when(projectRiskMapper.selectList(ArgumentMatchers.<Wrapper<ProjectRiskEntity>>any()))
                .thenReturn(List.of());

        repository.findOverdueByOwner(OWNER_ID, LocalDate.of(2026, 12, 31));

        verify(projectRiskMapper).selectList(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment()).contains("owner_id =", "due_date <=");
    }

    @Test
    void findBySourceRuleUsesUniqueRuleCode() {
        when(projectRiskMapper.selectOne(ArgumentMatchers.<Wrapper<ProjectRiskEntity>>any()))
                .thenReturn(new ProjectRiskEntity());

        assertThat(repository.findBySourceRule("NO_UPDATE_30D")).isPresent();
        verify(projectRiskMapper).selectOne(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment()).contains("source_rule =");
    }

    @Test
    void escalateRaisesLevelOneStepAndCapsAtCritical() {
        ProjectRiskEntity risk = new ProjectRiskEntity();
        risk.setStatus(ProjectRiskEntity.STATUS_OPEN);
        risk.setRiskLevel(ProjectRiskEntity.LEVEL_HIGH);

        assertThat(risk.escalate()).isEqualTo(ProjectRiskEntity.LEVEL_HIGH);
        assertThat(risk.getRiskLevel()).isEqualTo(ProjectRiskEntity.LEVEL_CRITICAL);
        assertThat(risk.getStatus()).isEqualTo(ProjectRiskEntity.STATUS_ESCALATED);

        assertThat(risk.escalate()).isEqualTo(ProjectRiskEntity.LEVEL_CRITICAL);
        assertThat(risk.getRiskLevel()).isEqualTo(ProjectRiskEntity.LEVEL_CRITICAL);
    }
}
