package cn.edu.rpm.project.infrastructure.repository;

import cn.edu.rpm.project.infrastructure.entity.ProjectEntity;
import cn.edu.rpm.project.infrastructure.mapper.ProjectMapper;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ProjectRepositoryImpl} 纯单元测试（T-PRJ-001 先行测试）。
 *
 * <p>覆盖：插入（PENDING_START 初始状态）；按编号/来源版本唯一查询；编号查重
 * （TC-APP-040 支撑）；悲观锁委托与 Mapper XML 的 {@code FOR UPDATE}。</p>
 */
@ExtendWith(MockitoExtension.class)
class ProjectRepositoryImplTest {

    private static final String PROJECT_ID = "8d7c6b5a4e3f4a2b9c1d0e2f3a4b5c6d";
    private static final String VERSION_ID = "1f2e3d4c5b6a49788a9b0c1d2e3f4a5b";

    @Mock
    private ProjectMapper projectMapper;

    @Captor
    private ArgumentCaptor<LambdaQueryWrapper<ProjectEntity>> wrapperCaptor;

    private ProjectRepositoryImpl repository;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ProjectEntity.class);
    }

    @BeforeEach
    void setUp() {
        repository = new ProjectRepositoryImpl(projectMapper);
    }

    private static ProjectEntity project() {
        ProjectEntity entity = new ProjectEntity();
        entity.setProjectNo("KY-2026-000456");
        entity.setTitle("面向复杂工况的智能感知方法研究");
        entity.setProjectType("YEAR_PLAN");
        entity.setDeptId("dddddddddddddddddddddddddddddddd");
        entity.setPiId("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        entity.setStatus(ProjectEntity.STATUS_PENDING_START);
        entity.setSourceType(ProjectEntity.SOURCE_INTERNAL);
        entity.setSourceApplicationVersionId(VERSION_ID);
        return entity;
    }

    @Test
    void insertDelegatesPendingStartProject() {
        ProjectEntity entity = project();
        when(projectMapper.insert(ArgumentMatchers.any(ProjectEntity.class))).thenReturn(1);

        repository.insert(entity);

        verify(projectMapper).insert(entity);
        assertThat(entity.getStatus()).isEqualTo(ProjectEntity.STATUS_PENDING_START);
        assertThat(entity.getSourceApplicationVersionId()).isEqualTo(VERSION_ID);
    }

    @Test
    void findByIdUsesPrimaryKey() {
        ProjectEntity entity = project();
        entity.setId(PROJECT_ID);
        when(projectMapper.selectById(PROJECT_ID)).thenReturn(entity);

        assertThat(repository.findById(PROJECT_ID)).containsSame(entity);
    }

    @Test
    void findByProjectNoFiltersUniqueNumber() {
        ProjectEntity entity = project();
        when(projectMapper.selectOne(ArgumentMatchers.<Wrapper<ProjectEntity>>any())).thenReturn(entity);

        Optional<ProjectEntity> result = repository.findByProjectNo("KY-2026-000456");

        assertThat(result).isPresent();
        verify(projectMapper).selectOne(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment()).contains("project_no =");
        assertThat(wrapperCaptor.getValue().getParamNameValuePairs().values()).contains("KY-2026-000456");
    }

    @Test
    void findBySourceApplicationVersionIdFiltersIdempotencyKey() {
        ProjectEntity entity = project();
        when(projectMapper.selectOne(ArgumentMatchers.<Wrapper<ProjectEntity>>any())).thenReturn(entity);

        Optional<ProjectEntity> result = repository.findBySourceApplicationVersionId(VERSION_ID);

        assertThat(result).isPresent();
        verify(projectMapper).selectOne(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment()).contains("source_application_version_id =");
    }

    @Test
    void existsByProjectNoCountsMatchingRows() {
        when(projectMapper.selectCount(ArgumentMatchers.<Wrapper<ProjectEntity>>any())).thenReturn(1L);

        assertThat(repository.existsByProjectNo("KY-2026-000456")).isTrue();
    }

    @Test
    void selectForUpdateDelegatesToLockingStatement() {
        ProjectEntity entity = project();
        entity.setId(PROJECT_ID);
        when(projectMapper.selectByIdForUpdate(PROJECT_ID)).thenReturn(entity);

        assertThat(repository.selectForUpdate(PROJECT_ID)).containsSame(entity);
        verify(projectMapper).selectByIdForUpdate(PROJECT_ID);
    }

    @Test
    void mapperXmlCarriesForUpdateClause() throws IOException {
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream("mapper/ProjectMapper.xml")) {
            assertThat(in).isNotNull();
            String xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(xml).contains("selectByIdForUpdate");
            assertThat(xml).containsIgnoringCase("from project");
            assertThat(xml).containsIgnoringCase("for update");
        }
    }

    @Test
    void entityMapsProjectColumns() {
        TableInfo tableInfo = TableInfoHelper.getTableInfo(ProjectEntity.class);

        assertThat(tableInfo.getTableName()).isEqualTo("project");
        assertThat(tableInfo.getKeyColumn()).isEqualTo("id");
        assertThat(tableInfo.getFieldList()).isNotEmpty();
    }
}
