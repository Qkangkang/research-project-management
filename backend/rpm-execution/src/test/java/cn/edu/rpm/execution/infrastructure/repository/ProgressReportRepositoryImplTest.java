package cn.edu.rpm.execution.infrastructure.repository;

import cn.edu.rpm.execution.infrastructure.entity.ProgressReportEntity;
import cn.edu.rpm.execution.infrastructure.mapper.ProgressReportMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ProgressReportRepositoryImpl} 纯单元测试（T-EXEC-001 先行测试）。
 *
 * <p>验收锚点：同项目同 {@code period_type + period_key} 唯一查询
 * （TC-EXEC-006/007）；到期扫描按 {@code due_date + status}；悲观锁 XML。</p>
 */
@ExtendWith(MockitoExtension.class)
class ProgressReportRepositoryImplTest {

    private static final String PROJECT_ID = "8d7c6b5a4e3f4a2b9c1d0e2f3a4b5c6d";

    @Mock
    private ProgressReportMapper progressReportMapper;

    @Captor
    private ArgumentCaptor<LambdaQueryWrapper<ProgressReportEntity>> wrapperCaptor;

    private ProgressReportRepositoryImpl repository;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ProgressReportEntity.class);
    }

    @BeforeEach
    void setUp() {
        repository = new ProgressReportRepositoryImpl(progressReportMapper);
    }

    @Test
    void insertKeepsDraftStatusAndDueDate() {
        ProgressReportEntity report = new ProgressReportEntity();
        report.setProjectId(PROJECT_ID);
        report.setPeriodType(ProgressReportEntity.PERIOD_MONTH);
        report.setPeriodKey("2026-11");
        report.setDueDate(LocalDate.of(2026, 12, 5));
        report.setStatus(ProgressReportEntity.STATUS_DRAFT);

        repository.insert(report);

        verify(progressReportMapper).insert(report);
        assertThat(report.getStatus()).isEqualTo(ProgressReportEntity.STATUS_DRAFT);
        assertThat(report.getDueDate()).isEqualTo(LocalDate.of(2026, 12, 5));
    }

    @Test
    void findByProjectIdAndPeriodFiltersUniquePeriodKey() {
        when(progressReportMapper.selectOne(
                ArgumentMatchers.<Wrapper<ProgressReportEntity>>any())).thenReturn(null);

        assertThat(repository.findByProjectIdAndPeriod(PROJECT_ID,
                ProgressReportEntity.PERIOD_MONTH, "2026-11")).isEmpty();

        verify(progressReportMapper).selectOne(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment())
                .contains("project_id =", "period_type =", "period_key =");
        assertThat(wrapperCaptor.getValue().getParamNameValuePairs().values())
                .contains(PROJECT_ID, "MONTH", "2026-11");
    }

    @Test
    void findDueByFiltersDateAndStatus() {
        when(progressReportMapper.selectList(
                ArgumentMatchers.<Wrapper<ProgressReportEntity>>any())).thenReturn(List.of());

        repository.findDueBy(LocalDate.of(2026, 12, 5), ProgressReportEntity.STATUS_DRAFT);

        verify(progressReportMapper).selectList(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment()).contains("due_date <=", "status =");
    }

    @Test
    void selectForUpdateDelegatesToLockingStatement() {
        String id = "rrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrr";
        when(progressReportMapper.selectByIdForUpdate(id)).thenReturn(new ProgressReportEntity());

        assertThat(repository.selectForUpdate(id)).isPresent();
        verify(progressReportMapper).selectByIdForUpdate(id);
    }

    @Test
    void mapperXmlCarriesForUpdateClause() throws IOException {
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream("mapper/ProgressReportMapper.xml")) {
            assertThat(in).isNotNull();
            String xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(xml).contains("selectByIdForUpdate");
            assertThat(xml).containsIgnoringCase("for update");
        }
    }

    @Test
    void entityMapsProgressReportColumns() {
        TableInfo tableInfo = TableInfoHelper.getTableInfo(ProgressReportEntity.class);

        assertThat(tableInfo.getTableName()).isEqualTo("progress_report");
        assertThat(tableInfo.getKeyColumn()).isEqualTo("id");
    }
}
