package cn.edu.rpm.project.infrastructure.repository;

import cn.edu.rpm.project.infrastructure.entity.ProjectHandoverEntity;
import cn.edu.rpm.project.infrastructure.mapper.ProjectHandoverMapper;
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

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ProjectHandoverRepositoryImpl} 纯单元测试（T-PRJ-009 先行测试）。
 */
@ExtendWith(MockitoExtension.class)
class ProjectHandoverRepositoryImplTest {

    private static final String PROJECT_ID = "8d7c6b5a4e3f4a2b9c1d0e2f3a4b5c6d";

    @Mock
    private ProjectHandoverMapper projectHandoverMapper;

    @Captor
    private ArgumentCaptor<LambdaQueryWrapper<ProjectHandoverEntity>> wrapperCaptor;

    private ProjectHandoverRepositoryImpl repository;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ProjectHandoverEntity.class);
    }

    @BeforeEach
    void setUp() {
        repository = new ProjectHandoverRepositoryImpl(projectHandoverMapper);
    }

    @Test
    void insertKeepsDraftHandover() {
        ProjectHandoverEntity handover = new ProjectHandoverEntity();
        handover.setProjectId(PROJECT_ID);
        handover.setFromUserId("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        handover.setToUserId("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
        handover.setStatus(ProjectHandoverEntity.STATUS_DRAFT);

        repository.insert(handover);

        verify(projectHandoverMapper).insert(handover);
        assertThat(handover.getStatus()).isEqualTo(ProjectHandoverEntity.STATUS_DRAFT);
    }

    @Test
    void findByProjectIdAndStatusFiltersBoth() {
        when(projectHandoverMapper.selectList(
                ArgumentMatchers.<Wrapper<ProjectHandoverEntity>>any())).thenReturn(List.of());

        repository.findByProjectIdAndStatus(PROJECT_ID, ProjectHandoverEntity.STATUS_IN_REVIEW);

        verify(projectHandoverMapper).selectList(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment()).contains("project_id =", "status =");
    }

    @Test
    void approveThenCompleteIsExplicitTransition() {
        ProjectHandoverEntity handover = new ProjectHandoverEntity();
        handover.setStatus(ProjectHandoverEntity.STATUS_IN_REVIEW);
        OffsetDateTime at = OffsetDateTime.parse("2026-12-01T09:00:00+08:00");

        handover.approve();
        handover.complete(at);

        assertThat(handover.getStatus()).isEqualTo(ProjectHandoverEntity.STATUS_COMPLETED);
        assertThat(handover.getCompletedAt()).isEqualTo(at);
    }

    @Test
    void updateDelegatesOptimisticLocking() {
        ProjectHandoverEntity handover = new ProjectHandoverEntity();
        handover.setVersion(2);
        when(projectHandoverMapper.updateById(handover)).thenReturn(1);

        assertThat(repository.update(handover)).isEqualTo(1);
    }
}
