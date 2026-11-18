package cn.edu.rpm.project.infrastructure.repository;

import cn.edu.rpm.project.infrastructure.entity.ProjectBaselineEntity;
import cn.edu.rpm.project.infrastructure.mapper.ProjectBaselineMapper;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ProjectBaselineRepositoryImpl} 纯单元测试（T-PRJ-003 先行测试）。
 */
@ExtendWith(MockitoExtension.class)
class ProjectBaselineRepositoryImplTest {

    private static final String PROJECT_ID = "8d7c6b5a4e3f4a2b9c1d0e2f3a4b5c6d";

    @Mock
    private ProjectBaselineMapper projectBaselineMapper;

    @Captor
    private ArgumentCaptor<LambdaQueryWrapper<ProjectBaselineEntity>> wrapperCaptor;

    private ProjectBaselineRepositoryImpl repository;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ProjectBaselineEntity.class);
    }

    @BeforeEach
    void setUp() {
        repository = new ProjectBaselineRepositoryImpl(projectBaselineMapper);
    }

    private static ProjectBaselineEntity baseline(int versionNo, String status) {
        ProjectBaselineEntity entity = new ProjectBaselineEntity();
        entity.setProjectId(PROJECT_ID);
        entity.setVersionNo(versionNo);
        entity.setSnapshot("{}");
        entity.setSnapshotHash("a".repeat(64));
        entity.setStatus(status);
        return entity;
    }

    @Test
    void insertKeepsSnapshotAndHash() {
        ProjectBaselineEntity entity = baseline(1, ProjectBaselineEntity.STATUS_EFFECTIVE);

        repository.insert(entity);

        verify(projectBaselineMapper).insert(entity);
        assertThat(entity.getSnapshotHash()).hasSize(64);
    }

    @Test
    void findByProjectIdAndVersionNoFiltersBoth() {
        when(projectBaselineMapper.selectOne(
                ArgumentMatchers.<Wrapper<ProjectBaselineEntity>>any()))
                .thenReturn(baseline(2, ProjectBaselineEntity.STATUS_SUPERSEDED));

        var result = repository.findByProjectIdAndVersionNo(PROJECT_ID, 2);

        assertThat(result).isPresent();
        verify(projectBaselineMapper).selectOne(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment()).contains("project_id =", "version_no =");
    }

    @Test
    void findEffectiveByProjectIdReturnsLatestEffectiveVersion() {
        when(projectBaselineMapper.selectList(
                ArgumentMatchers.<Wrapper<ProjectBaselineEntity>>any()))
                .thenReturn(List.of(baseline(3, ProjectBaselineEntity.STATUS_EFFECTIVE),
                        baseline(1, ProjectBaselineEntity.STATUS_EFFECTIVE)));

        assertThat(repository.findEffectiveByProjectId(PROJECT_ID))
                .get().extracting(ProjectBaselineEntity::getVersionNo).isEqualTo(3);

        verify(projectBaselineMapper).selectList(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment())
                .contains("status =").containsIgnoringCase("order by version_no desc");
    }

    @Test
    void findMaxVersionNoReturnsZeroWhenNoBaseline() {
        when(projectBaselineMapper.selectList(
                ArgumentMatchers.<Wrapper<ProjectBaselineEntity>>any())).thenReturn(List.of());

        assertThat(repository.findMaxVersionNo(PROJECT_ID)).isZero();
    }

    @Test
    void findMaxVersionNoIgnoresNullVersion() {
        ProjectBaselineEntity nullVersion = baseline(0, ProjectBaselineEntity.STATUS_DRAFT);
        nullVersion.setVersionNo(null);
        when(projectBaselineMapper.selectList(
                ArgumentMatchers.<Wrapper<ProjectBaselineEntity>>any()))
                .thenReturn(List.of(baseline(2, ProjectBaselineEntity.STATUS_SUPERSEDED), nullVersion));

        assertThat(repository.findMaxVersionNo(PROJECT_ID)).isEqualTo(2);
    }

    @Test
    void supersedeMovesOnlyEffectiveBaseline() {
        ProjectBaselineEntity entity = baseline(1, ProjectBaselineEntity.STATUS_EFFECTIVE);

        entity.supersede();

        assertThat(entity.getStatus()).isEqualTo(ProjectBaselineEntity.STATUS_SUPERSEDED);
    }
}
