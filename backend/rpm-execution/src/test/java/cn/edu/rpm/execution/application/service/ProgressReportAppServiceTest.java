package cn.edu.rpm.execution.application.service;

import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.common.util.UuidUtils;
import cn.edu.rpm.execution.application.dto.CreateProgressReportCmd;
import cn.edu.rpm.execution.application.dto.ProgressReportCreated;
import cn.edu.rpm.execution.application.dto.ProgressReportView;
import cn.edu.rpm.execution.domain.model.ExecutionPermissions;
import cn.edu.rpm.execution.infrastructure.entity.ProgressReportEntity;
import cn.edu.rpm.execution.infrastructure.repository.ProgressReportRepository;
import cn.edu.rpm.iam.application.dto.AuthContext;
import cn.edu.rpm.iam.application.dto.AuthDecision;
import cn.edu.rpm.iam.domain.service.AuthorizationService;
import cn.edu.rpm.project.infrastructure.entity.ProjectEntity;
import cn.edu.rpm.project.infrastructure.entity.ProjectMemberEntity;
import cn.edu.rpm.project.infrastructure.repository.ProjectMemberRepository;
import cn.edu.rpm.project.infrastructure.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ProgressReportAppService} 纯单元测试（T-EXEC-010）。
 *
 * <p>覆盖报告列表映射、空列表、创建授权与拒绝；创建规则由
 * {@link ProgressReportServiceTest} 覆盖。</p>
 */
@ExtendWith(MockitoExtension.class)
class ProgressReportAppServiceTest {

    private static final String PROJECT_ID = "8d7c6b5a4e3f4a2b9c1d0e2f3a4b5c6d";
    private static final UUID ACTOR_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID PI_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID DEPT_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final Instant NOW = Instant.parse("2026-11-18T02:00:00Z");

    @Mock
    private ProgressReportService progressReportService;

    @Mock
    private ProgressReportRepository progressReportRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectMemberRepository projectMemberRepository;

    @Mock
    private AuthorizationService authorizationService;

    private ProgressReportAppService service;

    private AuthContext auth;

    @BeforeEach
    void setUp() {
        service = new ProgressReportAppService(progressReportService, progressReportRepository,
                projectRepository, projectMemberRepository, authorizationService,
                Clock.fixed(NOW, ZoneId.of("Asia/Shanghai")));
        ProjectEntity project = new ProjectEntity();
        project.setId(PROJECT_ID);
        project.setPiId(UuidUtils.formatCompact(PI_ID));
        project.setDeptId(UuidUtils.formatCompact(DEPT_ID));
        project.setStatus(ProjectEntity.STATUS_EXECUTING);
        lenient().when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        ProjectMemberEntity member = new ProjectMemberEntity();
        member.setProjectId(PROJECT_ID);
        member.setUserId(UuidUtils.formatCompact(ACTOR_ID));
        member.setStatus(ProjectMemberEntity.STATUS_ACTIVE);
        lenient().when(projectMemberRepository.findActiveByProjectId(any(), any()))
                .thenReturn(List.of(member));
        lenient().when(authorizationService.check(any(), any(), any()))
                .thenReturn(AuthDecision.allow());
        auth = new AuthContext(ACTOR_ID, null, "E1001",
                Set.of(ExecutionPermissions.VIEW, ExecutionPermissions.REPORT_EDIT),
                Set.of(), NOW);
    }

    @Test
    void createDelegatesToReportServiceAfterAuthorization() {
        when(progressReportService.create(PROJECT_ID, "MONTH", "2026-11"))
                .thenReturn(new ProgressReportCreated("r-1", "MONTH", "2026-11", "DRAFT",
                        LocalDate.of(2026, 12, 5)));

        ProgressReportCreated created = service.create(PROJECT_ID,
                new CreateProgressReportCmd("MONTH", "2026-11"), auth);

        assertThat(created.reportId()).isEqualTo("r-1");
        assertThat(created.dueDate()).isEqualTo(LocalDate.of(2026, 12, 5));
    }

    @Test
    void createWithoutPermissionReturns403() {
        when(authorizationService.check(any(), any(), any()))
                .thenReturn(AuthDecision.deny(ErrorCode.COMMON_403));

        assertThatThrownBy(() -> service.create(PROJECT_ID,
                new CreateProgressReportCmd("MONTH", "2026-11"), auth))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.COMMON_403));

        verify(progressReportService, never()).create(any(), any(), any());
    }

    @Test
    void listMapsReports() {
        ProgressReportEntity report = new ProgressReportEntity();
        report.setId("11111111111111111111111111111111");
        report.setProjectId(PROJECT_ID);
        report.setPeriodType(ProgressReportEntity.PERIOD_MONTH);
        report.setPeriodKey("2026-11");
        report.setDueDate(LocalDate.of(2026, 12, 5));
        report.setStatus(ProgressReportEntity.STATUS_DRAFT);
        report.setVersion(2);
        when(progressReportRepository.findByProjectId(PROJECT_ID)).thenReturn(List.of(report));

        List<ProgressReportView> views = service.list(PROJECT_ID, auth);

        assertThat(views).hasSize(1);
        assertThat(views.get(0).reportId()).isEqualTo(report.getId());
        assertThat(views.get(0).periodKey()).isEqualTo("2026-11");
        assertThat(views.get(0).status()).isEqualTo("DRAFT");
        verify(authorizationService).check(any(),
                eq(cn.edu.rpm.iam.application.dto.Action.of(ExecutionPermissions.VIEW)), any());
    }

    @Test
    void listReturnsEmptyWhenNoReports() {
        when(progressReportRepository.findByProjectId(PROJECT_ID)).thenReturn(List.of());

        assertThat(service.list(PROJECT_ID, auth)).isEmpty();
    }
}
