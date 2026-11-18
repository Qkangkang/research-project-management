package cn.edu.rpm.execution.infrastructure.repository;

import cn.edu.rpm.execution.infrastructure.entity.ProjectTaskEntity;
import cn.edu.rpm.execution.infrastructure.mapper.ProjectTaskMapper;
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
 * {@link ProjectTaskRepositoryImpl} 纯单元测试（T-EXEC-004 先行测试）。
 */
@ExtendWith(MockitoExtension.class)
class ProjectTaskRepositoryImplTest {

    private static final String PROJECT_ID = "8d7c6b5a4e3f4a2b9c1d0e2f3a4b5c6d";
    private static final String ASSIGNEE_ID = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    @Mock
    private ProjectTaskMapper projectTaskMapper;

    @Captor
    private ArgumentCaptor<LambdaQueryWrapper<ProjectTaskEntity>> wrapperCaptor;

    private ProjectTaskRepositoryImpl repository;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ProjectTaskEntity.class);
    }

    @BeforeEach
    void setUp() {
        repository = new ProjectTaskRepositoryImpl(projectTaskMapper);
    }

    @Test
    void insertKeepsTodoStatus() {
        ProjectTaskEntity task = new ProjectTaskEntity();
        task.setProjectId(PROJECT_ID);
        task.setTitle("整理中期检查材料");
        task.setAssigneeId(ASSIGNEE_ID);
        task.setStatus(ProjectTaskEntity.STATUS_TODO);

        repository.insert(task);

        verify(projectTaskMapper).insert(task);
        assertThat(task.getStatus()).isEqualTo(ProjectTaskEntity.STATUS_TODO);
    }

    @Test
    void findByProjectIdAndStatusFiltersBoth() {
        when(projectTaskMapper.selectList(ArgumentMatchers.<Wrapper<ProjectTaskEntity>>any()))
                .thenReturn(List.of());

        repository.findByProjectIdAndStatus(PROJECT_ID, ProjectTaskEntity.STATUS_TODO);

        verify(projectTaskMapper).selectList(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment()).contains("project_id =", "status =");
    }

    @Test
    void findByAssigneeIdAndStatusFiltersAuthorizedAssignee() {
        when(projectTaskMapper.selectList(ArgumentMatchers.<Wrapper<ProjectTaskEntity>>any()))
                .thenReturn(List.of());

        repository.findByAssigneeIdAndStatus(ASSIGNEE_ID, ProjectTaskEntity.STATUS_IN_PROGRESS);

        verify(projectTaskMapper).selectList(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment()).contains("assignee_id =", "status =");
    }

    @Test
    void startThenCompleteIsExplicitTransition() {
        ProjectTaskEntity task = new ProjectTaskEntity();
        task.setStatus(ProjectTaskEntity.STATUS_TODO);

        task.start();
        task.complete(java.time.OffsetDateTime.parse("2026-12-01T10:00:00+08:00"));

        assertThat(task.getStatus()).isEqualTo(ProjectTaskEntity.STATUS_DONE);
        assertThat(task.getCompletedAt()).isNotNull();
    }
}
