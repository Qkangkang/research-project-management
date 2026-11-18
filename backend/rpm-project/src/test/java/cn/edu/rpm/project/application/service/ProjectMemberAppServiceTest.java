package cn.edu.rpm.project.application.service;

import cn.edu.rpm.audit.domain.service.AuditRecorder;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.iam.application.dto.AuthContext;
import cn.edu.rpm.iam.application.dto.AuthDecision;
import cn.edu.rpm.iam.domain.service.AuthorizationService;
import cn.edu.rpm.iam.domain.service.FieldMaskService;
import cn.edu.rpm.iam.infrastructure.entity.IamUserEntity;
import cn.edu.rpm.iam.infrastructure.repository.IamUserRepository;
import cn.edu.rpm.project.application.dto.AddProjectMemberCmd;
import cn.edu.rpm.project.application.dto.ProjectMemberVO;
import cn.edu.rpm.project.infrastructure.entity.ProjectEntity;
import cn.edu.rpm.project.infrastructure.entity.ProjectMemberEntity;
import cn.edu.rpm.project.infrastructure.repository.ProjectMemberRepository;
import cn.edu.rpm.project.infrastructure.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ProjectMemberAppService} 纯单元测试（T-PRJ-013）。
 *
 * <p>覆盖 TC-PRJ-014/015/018/019：新增成员 ACTIVE、重复角色 {@code RPM-PROJ-005}、
 * 无成员历史返回空列表、有效期内过滤、联系方式脱敏、无权限 403。</p>
 */
@ExtendWith(MockitoExtension.class)
class ProjectMemberAppServiceTest {

    private static final String PROJECT_ID = "8d7c6b5a4e3f4a2b9c1d0e2f3a4b5c6d";
    private static final String USER_ID = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final UUID ACTOR_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final Instant NOW = Instant.parse("2026-11-18T02:00:00Z");

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectMemberRepository projectMemberRepository;

    @Mock
    private IamUserRepository iamUserRepository;

    @Mock
    private AuthorizationService authorizationService;

    @Mock
    private AuditRecorder auditRecorder;

    private ProjectMemberAppService service;

    private ProjectEntity project;

    @BeforeEach
    void setUp() {
        service = new ProjectMemberAppService(projectRepository, projectMemberRepository,
                iamUserRepository, authorizationService, new FieldMaskService(), auditRecorder,
                Clock.fixed(NOW, ZoneId.of("Asia/Shanghai")));
        project = new ProjectEntity();
        project.setId(PROJECT_ID);
        project.setPiId("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
        project.setDeptId("dddddddddddddddddddddddddddddddd");
        project.setStatus(ProjectEntity.STATUS_EXECUTING);
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
    }

    private void allowAccess() {
        when(projectMemberRepository.findActiveByProjectId(anyString(), any())).thenReturn(List.of());
        when(authorizationService.check(any(), any(), any())).thenReturn(AuthDecision.allow());
    }

    private AuthContext auth() {
        return new AuthContext(ACTOR_ID, null, "E001",
                Set.of("project:member:manage", "project:view"), Set.of(), NOW);
    }

    private AddProjectMemberCmd cmd() {
        return new AddProjectMemberCmd(USER_ID, "MEMBER", "负责算法设计",
                OffsetDateTime.parse("2026-11-18T10:00:00+08:00"), null);
    }

    private IamUserEntity user() {
        IamUserEntity user = new IamUserEntity();
        user.setId(USER_ID);
        user.setDisplayName("张三");
        user.setStatus("ACTIVE");
        user.setMobileMasked("13800005678");
        return user;
    }

    @Test
    void tcPrj014AddCreatesActiveMember() {
        allowAccess();
        when(iamUserRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(projectMemberRepository.existsActiveMember(anyString(), anyString(), anyString(), any()))
                .thenReturn(false);
        ArgumentCaptor<ProjectMemberEntity> captor = ArgumentCaptor.forClass(ProjectMemberEntity.class);

        service.add(PROJECT_ID, cmd(), auth());

        verify(projectMemberRepository).insert(captor.capture());
        ProjectMemberEntity member = captor.getValue();
        assertThat(member.getStatus()).isEqualTo(ProjectMemberEntity.STATUS_ACTIVE);
        assertThat(member.getUserId()).isEqualTo(USER_ID);
        assertThat(member.getProjectRole()).isEqualTo("MEMBER");
        assertThat(member.getValidTo()).isNull();
    }

    @Test
    void tcPrj015DuplicateMemberRoleReturnsProj005() {
        allowAccess();
        when(iamUserRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(projectMemberRepository.existsActiveMember(anyString(), anyString(), anyString(), any()))
                .thenReturn(true);

        assertThatThrownBy(() -> service.add(PROJECT_ID, cmd(), auth()))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.PROJ_005));
    }

    @Test
    void addRejectsInactiveUser() {
        allowAccess();
        IamUserEntity left = user();
        left.setStatus("LEAVED");
        when(iamUserRepository.findById(USER_ID)).thenReturn(Optional.of(left));

        assertThatThrownBy(() -> service.add(PROJECT_ID, cmd(), auth()))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.COMMON_422));
    }

    @Test
    void addRejectsInvalidValidityWindow() {
        allowAccess();
        AddProjectMemberCmd invalid = new AddProjectMemberCmd(USER_ID, "MEMBER", null,
                OffsetDateTime.parse("2026-11-18T10:00:00+08:00"),
                OffsetDateTime.parse("2026-11-17T10:00:00+08:00"));

        assertThatThrownBy(() -> service.add(PROJECT_ID, invalid, auth()))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.COMMON_400));
    }

    @Test
    void tcPrj019ListWithoutMembersReturnsEmptyList() {
        allowAccess();

        assertThat(service.list(PROJECT_ID, auth())).isEmpty();
    }

    @Test
    void tcPrj018ListMasksContactWithoutFieldPermission() {
        allowAccess();
        ProjectMemberEntity member = new ProjectMemberEntity();
        member.setId("mmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmm");
        member.setProjectId(PROJECT_ID);
        member.setUserId(USER_ID);
        member.setProjectRole("MEMBER");
        member.setStatus(ProjectMemberEntity.STATUS_ACTIVE);
        when(projectMemberRepository.findActiveByProjectId(anyString(), any()))
                .thenReturn(List.of(member));
        when(iamUserRepository.findByIds(List.of(USER_ID))).thenReturn(List.of(user()));

        List<ProjectMemberVO> result = service.list(PROJECT_ID, auth());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDisplayName()).isEqualTo("张三");
        assertThat(result.get(0).getMobile()).isEqualTo("138****5678");
    }

    @Test
    void listKeepsContactWhenFieldPermissionHeld() {
        allowAccess();
        ProjectMemberEntity member = new ProjectMemberEntity();
        member.setId("mmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmm");
        member.setProjectId(PROJECT_ID);
        member.setUserId(USER_ID);
        member.setProjectRole("MEMBER");
        member.setStatus(ProjectMemberEntity.STATUS_ACTIVE);
        when(projectMemberRepository.findActiveByProjectId(anyString(), any())).thenReturn(List.of(member));
        when(iamUserRepository.findByIds(List.of(USER_ID))).thenReturn(List.of(user()));
        AuthContext privileged = new AuthContext(ACTOR_ID, null, "E001",
                Set.of("project:view", "field:contact:read"), Set.of(), NOW);

        assertThat(service.list(PROJECT_ID, privileged).get(0).getMobile())
                .isEqualTo("13800005678");
    }

    @Test
    void unauthorizedListReturnsCommon403() {
        allowAccess();
        when(authorizationService.check(any(), any(), any()))
                .thenReturn(AuthDecision.deny(ErrorCode.COMMON_403));

        assertThatThrownBy(() -> service.list(PROJECT_ID, auth()))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.COMMON_403));
    }

    @Test
    void missingProjectReturnsProj001() {
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.add(PROJECT_ID, cmd(), auth()))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.PROJ_001));
    }
}
