package cn.edu.rpm.project.infrastructure.repository;

import cn.edu.rpm.project.infrastructure.entity.MilestoneEntity;
import cn.edu.rpm.project.infrastructure.mapper.MilestoneMapper;
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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link MilestoneRepositoryImpl} 纯单元测试（T-PRJ-004 先行测试）。
 *
 * <p>覆盖：批量生成绑定同一 {@code baseline_id}（TC-EXEC-001）；未完成查询排除
 * {@code COMPLETED/CANCELLED}；{@code plan_date} 原计划字段不可变语义由实体保证；
 * 悲观锁 XML。</p>
 */
@ExtendWith(MockitoExtension.class)
class MilestoneRepositoryImplTest {

    private static final String PROJECT_ID = "8d7c6b5a4e3f4a2b9c1d0e2f3a4b5c6d";
    private static final String BASELINE_ID = "2a3b4c5d6e7f4a8b9c0d1e2f3a4b5c6d";

    @Mock
    private MilestoneMapper milestoneMapper;

    @Captor
    private ArgumentCaptor<LambdaQueryWrapper<MilestoneEntity>> wrapperCaptor;

    private MilestoneRepositoryImpl repository;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, MilestoneEntity.class);
    }

    @BeforeEach
    void setUp() {
        repository = new MilestoneRepositoryImpl(milestoneMapper);
    }

    private static MilestoneEntity milestone(String code, LocalDate planDate, String status) {
        MilestoneEntity entity = new MilestoneEntity();
        entity.setProjectId(PROJECT_ID);
        entity.setBaselineId(BASELINE_ID);
        entity.setCode(code);
        entity.setTitle("里程碑-" + code);
        entity.setPlanDate(planDate);
        entity.setCurrentPlanDate(planDate);
        entity.setStatus(status);
        return entity;
    }

    @Test
    void insertBatchPersistsAllSixMilestonesBoundToSameBaseline() {
        List<MilestoneEntity> milestones = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            milestones.add(milestone("M" + i, LocalDate.of(2027, i, 28),
                    MilestoneEntity.STATUS_PLANNED));
        }

        repository.insertBatch(milestones);

        verify(milestoneMapper, times(6)).insert(ArgumentMatchers.any(MilestoneEntity.class));
        assertThat(milestones).allMatch(item -> BASELINE_ID.equals(item.getBaselineId()));
    }

    @Test
    void insertBatchIgnoresEmptyList() {
        repository.insertBatch(List.of());

        verify(milestoneMapper, times(0)).insert(ArgumentMatchers.any(MilestoneEntity.class));
    }

    @Test
    void findUnfinishedDueByExcludesCompletedAndCancelled() {
        when(milestoneMapper.selectList(ArgumentMatchers.<Wrapper<MilestoneEntity>>any()))
                .thenReturn(List.of(milestone("M1", LocalDate.of(2027, 3, 31),
                        MilestoneEntity.STATUS_PLANNED)));

        List<MilestoneEntity> result = repository.findUnfinishedDueBy(PROJECT_ID,
                LocalDate.of(2027, 4, 1));

        assertThat(result).hasSize(1);
        verify(milestoneMapper).selectList(wrapperCaptor.capture());
        String sql = wrapperCaptor.getValue().getSqlSegment();
        assertThat(sql).contains("current_plan_date <=");
        assertThat(sql).containsIgnoringCase("status not in");
        assertThat(wrapperCaptor.getValue().getParamNameValuePairs().values())
                .contains(MilestoneEntity.STATUS_COMPLETED, MilestoneEntity.STATUS_CANCELLED);
    }

    @Test
    void findByProjectIdAndBaselineIdUsesBaselineFilter() {
        when(milestoneMapper.selectList(ArgumentMatchers.<Wrapper<MilestoneEntity>>any()))
                .thenReturn(List.of());

        repository.findByProjectIdAndBaselineId(PROJECT_ID, BASELINE_ID);

        verify(milestoneMapper).selectList(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment())
                .contains("project_id =", "baseline_id =");
    }

    @Test
    void selectForUpdateDelegatesToLockingStatement() {
        MilestoneEntity entity = milestone("M1", LocalDate.of(2027, 3, 31),
                MilestoneEntity.STATUS_PLANNED);
        when(milestoneMapper.selectByIdForUpdate("mmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmm"))
                .thenReturn(entity);

        assertThat(repository.selectForUpdate("mmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmm")).isPresent();
        verify(milestoneMapper).selectByIdForUpdate("mmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmm");
    }

    @Test
    void mapperXmlCarriesForUpdateClause() throws IOException {
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream("mapper/MilestoneMapper.xml")) {
            assertThat(in).isNotNull();
            String xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(xml).contains("selectByIdForUpdate");
            assertThat(xml).containsIgnoringCase("for update");
        }
    }
}
