package cn.edu.rpm.project.application.service;

import cn.edu.rpm.common.api.PageVO;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.iam.application.dto.AuthContext;
import cn.edu.rpm.iam.application.dto.AuthDecision;
import cn.edu.rpm.iam.application.dto.ProjectQuery;
import cn.edu.rpm.iam.application.dto.ProjectScope;
import cn.edu.rpm.iam.domain.service.AuthorizationService;
import cn.edu.rpm.project.application.dto.ProjectDetailSummary;
import cn.edu.rpm.project.application.dto.ProjectListItem;
import cn.edu.rpm.project.application.dto.ProjectListQuery;
import cn.edu.rpm.project.application.dto.ProjectSummary;
import cn.edu.rpm.project.infrastructure.entity.ProjectEntity;
import cn.edu.rpm.project.infrastructure.repository.ProjectMemberRepository;
import cn.edu.rpm.project.infrastructure.repository.ProjectQueryRepository;
import cn.edu.rpm.project.infrastructure.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ProjectQueryAppService} 纯单元测试（T-PRJ-017）。
 *
 * <p>覆盖 TC-PRJ-001～004、TC-PRJ-035：列表与详情复用同一 scope；空结果返回空分页；
 * 详情不存在 {@code RPM-PROJ-001}；存在但不在数据范围 {@code RPM-COMMON-403}
 * 且不泄露标题等字段。</p>
 */
@ExtendWith(MockitoExtension.class)
class ProjectQueryAppServiceTest {

    private static final String PROJECT_ID = "8d7c6b5a4e3f4a2b9c1d0e2f3a4b5c6d";
    private static final UUID VIEWER_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final Instant NOW = Instant.parse("2026-11-18T02:00:00Z");

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectMemberRepository projectMemberRepository;

    @Mock
    private ProjectQueryRepository projectQueryRepository;

    @Mock
    private AuthorizationService authorizationService;

    private ProjectQueryAppService service;

    private AuthContext auth;

    @BeforeEach
    void setUp() {
        service = new ProjectQueryAppService(projectRepository, projectMemberRepository,
                projectQueryRepository, authorizationService,
                Clock.fixed(NOW, ZoneId.of("Asia/Shanghai")));
        auth = new AuthContext(VIEWER_ID, null, "E001",
                Set.of("project:view"), Set.of(), NOW);
    }

    private ProjectEntity project() {
        ProjectEntity project = new ProjectEntity();
        project.setId(PROJECT_ID);
        project.setPiId("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        project.setDeptId("dddddddddddddddddddddddddddddddd");
        project.setStatus(ProjectEntity.STATUS_EXECUTING);
        return project;
    }

    @Test
    void tcPrj001ListPassesResolvedScopeAndViewerToRepository() {
        ProjectScope scope = ProjectScope.allScope();
        ProjectListQuery query = new ProjectListQuery();
        PageVO<ProjectListItem> page = PageVO.of(List.of(new ProjectListItem()), 1, 20, 1);
        when(authorizationService.resolveProjectScope(eq(auth), any(ProjectQuery.class)))
                .thenReturn(scope);
        when(projectQueryRepository.search(query, scope,
                "cccccccccccccccccccccccccccccccc")).thenReturn(page);

        assertThat(service.list(query, auth)).isSameAs(page);
        verify(projectQueryRepository).search(query, scope, "cccccccccccccccccccccccccccccccc");
    }

    @Test
    void tcPrj001ListNullQueryUsesDefaultQuery() {
        ProjectScope scope = ProjectScope.empty();
        PageVO<ProjectListItem> empty = PageVO.of(List.of(), 1, 20, 0);
        when(authorizationService.resolveProjectScope(eq(auth), any(ProjectQuery.class)))
                .thenReturn(scope);
        when(projectQueryRepository.search(any(ProjectListQuery.class), eq(scope), anyString()))
                .thenReturn(empty);

        PageVO<ProjectListItem> result = service.list(null, auth);

        assertThat(result.getItems()).isEmpty();
        assertThat(result.getTotal()).isZero();
    }

    @Test
    void tcPrj002EmptyResultReturnsEmptyPageWithoutError() {
        ProjectScope scope = ProjectScope.empty();
        PageVO<ProjectListItem> empty = PageVO.of(List.of(), 2, 50, 0);
        when(authorizationService.resolveProjectScope(eq(auth), any(ProjectQuery.class)))
                .thenReturn(scope);
        when(projectQueryRepository.search(any(ProjectListQuery.class), eq(scope), anyString()))
                .thenReturn(empty);

        PageVO<ProjectListItem> result = service.list(new ProjectListQuery(), auth);

        assertThat(result.getItems()).isEmpty();
        assertThat(result.getTotal()).isZero();
        assertThat(result.getPage()).isEqualTo(2);
    }

    @Test
    void tcPrj003DetailReturnsHeaderAndSummary() {
        ProjectScope scope = ProjectScope.of(false, VIEWER_ID, Set.of(), Set.of());
        ProjectDetailSummary summary = new ProjectDetailSummary(PROJECT_ID, "RPM-2026-0001",
                "测试项目", "RESEARCH", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "负责人",
                "dddddddddddddddddddddddddddddddd", "测试学院",
                "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", 1, ProjectEntity.STATUS_EXECUTING,
                null, null, new BigDecimal("100000.00"), 1L, null, null,
                new ProjectSummary(6, 2, 1, new BigDecimal("100000.00"),
                        new BigDecimal("60000.00"), 3, 1));
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project()));
        when(projectMemberRepository.findActiveByProjectId(anyString(), any())).thenReturn(List.of());
        when(authorizationService.check(any(), any(), any())).thenReturn(AuthDecision.allow());
        when(authorizationService.resolveProjectScope(eq(auth), any(ProjectQuery.class)))
                .thenReturn(scope);
        when(projectQueryRepository.findDetailSummary(PROJECT_ID, scope,
                "cccccccccccccccccccccccccccccccc")).thenReturn(Optional.of(summary));

        ProjectDetailSummary result = service.detail(PROJECT_ID, auth);

        assertThat(result.baselineVersion()).isEqualTo(1);
        assertThat(result.summary().milestoneTotal()).isEqualTo(6);
        assertThat(result.summary().availableAmount()).isEqualByComparingTo("60000.00");
    }

    @Test
    void tcPrj004DetailOutsideScopeReturnsCommon403WithoutLeakingFields() {
        ProjectScope scope = ProjectScope.empty();
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project()));
        when(projectMemberRepository.findActiveByProjectId(anyString(), any())).thenReturn(List.of());
        when(authorizationService.check(any(), any(), any())).thenReturn(AuthDecision.allow());
        when(authorizationService.resolveProjectScope(eq(auth), any(ProjectQuery.class)))
                .thenReturn(scope);
        when(projectQueryRepository.findDetailSummary(PROJECT_ID, scope,
                "cccccccccccccccccccccccccccccccc")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.detail(PROJECT_ID, auth))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> {
                    BizException biz = (BizException) ex;
                    assertThat(biz.getErrorCode()).isEqualTo(ErrorCode.COMMON_403);
                    assertThat(biz.getMessage()).doesNotContain("RPM-2026");
                });
    }

    @Test
    void tcPrj035MissingProjectReturnsProj001() {
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.detail(PROJECT_ID, auth))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.PROJ_001));
        verify(projectQueryRepository, never()).findDetailSummary(anyString(), any(), anyString());
    }

    @Test
    void detailUnauthorizedReturnsCommon403() {
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project()));
        when(projectMemberRepository.findActiveByProjectId(anyString(), any())).thenReturn(List.of());
        when(authorizationService.check(any(), any(), any()))
                .thenReturn(AuthDecision.deny(ErrorCode.COMMON_403));

        assertThatThrownBy(() -> service.detail(PROJECT_ID, auth))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.COMMON_403));
        verify(authorizationService, never()).resolveProjectScope(any(), any());
    }
}
