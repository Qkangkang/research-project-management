package cn.edu.rpm.project.infrastructure.repository;

import cn.edu.rpm.project.infrastructure.entity.ProjectMemberEntity;
import cn.edu.rpm.project.infrastructure.mapper.ProjectMemberMapper;
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
 * {@link ProjectMemberRepositoryImpl} 纯单元测试（T-PRJ-002 先行测试）。
 *
 * <p>验收锚点：有效成员 = {@code status=ACTIVE} 且 {@code valid_from <= at} 且
 * （{@code valid_to} 为空或 {@code valid_to > at}）；重复成员同角色判定
 * （TC-PRJ-015）；成员项目列表按用户倒查（TC-PRJ-014 可见性支撑）。</p>
 */
@ExtendWith(MockitoExtension.class)
class ProjectMemberRepositoryImplTest {

    private static final String PROJECT_ID = "8d7c6b5a4e3f4a2b9c1d0e2f3a4b5c6d";
    private static final String USER_ID = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final OffsetDateTime AT = OffsetDateTime.parse("2026-11-18T10:00:00+08:00");

    @Mock
    private ProjectMemberMapper projectMemberMapper;

    @Captor
    private ArgumentCaptor<LambdaQueryWrapper<ProjectMemberEntity>> wrapperCaptor;

    private ProjectMemberRepositoryImpl repository;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ProjectMemberEntity.class);
    }

    @BeforeEach
    void setUp() {
        repository = new ProjectMemberRepositoryImpl(projectMemberMapper);
    }

    @Test
    void insertKeepsActiveStatusAndValidity() {
        ProjectMemberEntity member = new ProjectMemberEntity();
        member.setProjectId(PROJECT_ID);
        member.setUserId(USER_ID);
        member.setProjectRole("MEMBER");
        member.setValidFrom(AT);
        member.setStatus(ProjectMemberEntity.STATUS_ACTIVE);

        repository.insert(member);

        verify(projectMemberMapper).insert(member);
        assertThat(member.getStatus()).isEqualTo(ProjectMemberEntity.STATUS_ACTIVE);
    }

    @Test
    void findActiveByProjectIdAppliesStatusAndValidityWindow() {
        when(projectMemberMapper.selectList(
                ArgumentMatchers.<Wrapper<ProjectMemberEntity>>any())).thenReturn(List.of());

        repository.findActiveByProjectId(PROJECT_ID, AT);

        verify(projectMemberMapper).selectList(wrapperCaptor.capture());
        String sql = wrapperCaptor.getValue().getSqlSegment();
        assertThat(sql).contains("project_id =", "status =", "valid_from <=");
        assertThat(sql).containsIgnoringCase("valid_to is null").containsIgnoringCase("valid_to >");
        assertThat(wrapperCaptor.getValue().getParamNameValuePairs().values())
                .contains(PROJECT_ID, ProjectMemberEntity.STATUS_ACTIVE);
    }

    @Test
    void existsActiveMemberFiltersUserAndRole() {
        when(projectMemberMapper.selectCount(
                ArgumentMatchers.<Wrapper<ProjectMemberEntity>>any())).thenReturn(1L);

        assertThat(repository.existsActiveMember(PROJECT_ID, USER_ID, "MEMBER", AT)).isTrue();

        verify(projectMemberMapper).selectCount(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment())
                .contains("user_id =", "project_role =");
    }

    @Test
    void findActiveProjectIdsByUserIdReturnsDistinctProjectIds() {
        ProjectMemberEntity first = new ProjectMemberEntity();
        first.setProjectId(PROJECT_ID);
        ProjectMemberEntity duplicate = new ProjectMemberEntity();
        duplicate.setProjectId(PROJECT_ID);
        when(projectMemberMapper.selectList(
                ArgumentMatchers.<Wrapper<ProjectMemberEntity>>any()))
                .thenReturn(List.of(first, duplicate));

        assertThat(repository.findActiveProjectIdsByUserId(USER_ID, AT)).containsExactly(PROJECT_ID);
    }

    @Test
    void deactivateWritesValidToAndInactiveStatusOnEntity() {
        ProjectMemberEntity member = new ProjectMemberEntity();
        member.setStatus(ProjectMemberEntity.STATUS_ACTIVE);

        member.deactivate(AT);

        assertThat(member.getStatus()).isEqualTo(ProjectMemberEntity.STATUS_INACTIVE);
        assertThat(member.getValidTo()).isEqualTo(AT);
    }
}
