package cn.edu.rpm.execution.application.service;

import cn.edu.rpm.audit.domain.service.AuditRecorder;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.common.util.CanonicalJsonHasher;
import cn.edu.rpm.common.util.UuidUtils;
import cn.edu.rpm.execution.application.dto.ProgressReportSubmitted;
import cn.edu.rpm.execution.application.dto.SubmitProgressReportCmd;
import cn.edu.rpm.execution.domain.port.ProgressReportRiskPort;
import cn.edu.rpm.execution.infrastructure.entity.ProgressReportEntity;
import cn.edu.rpm.execution.infrastructure.entity.ProgressReportVersionEntity;
import cn.edu.rpm.execution.infrastructure.repository.ProgressReportRepository;
import cn.edu.rpm.execution.infrastructure.repository.ProgressReportVersionRepository;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link ProgressReportSubmitAppService} 纯单元测试（T-EXEC-006）。
 *
 * <p>覆盖 TC-EXEC-008（截止日前提交 version_no=1、late_flag=false）、
 * TC-EXEC-009（逾期 2 天 late_flag=true 并生成风险）、
 * TC-EXEC-011（退回后补正 version_no=2、旧版本保留）。</p>
 */
@ExtendWith(MockitoExtension.class)
class ProgressReportSubmitAppServiceTest {

    private static final String PROJECT_ID = "8d7c6b5a4e3f4a2b9c1d0e2f3a4b5c6d";
    private static final String REPORT_ID = "11111111111111111111111111111111";
    private static final String OLD_VERSION_ID = "22222222222222222222222222222222";
    private static final UUID ACTOR_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID REQUEST_ID = UUID.fromString("abcdabcd-abcd-abcd-abcd-abcdabcdabcd");
    private static final Instant NOW = Instant.parse("2026-11-18T02:00:00Z");

    @Mock
    private ProgressReportRepository progressReportRepository;

    @Mock
    private ProgressReportVersionRepository versionRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectMemberRepository projectMemberRepository;

    @Mock
    private ProgressReportRiskPort riskPort;

    @Mock
    private AuthorizationService authorizationService;

    @Mock
    private AuditRecorder auditRecorder;

    private ProgressReportSubmitAppService service;

    private ProgressReportEntity report;

    private AuthContext auth;

    @BeforeEach
    void setUp() {
        service = new ProgressReportSubmitAppService(progressReportRepository, versionRepository,
                projectRepository, projectMemberRepository, riskPort, authorizationService,
                auditRecorder, new CanonicalJsonHasher(), Clock.fixed(NOW, ZoneId.of("Asia/Shanghai")));
        report = new ProgressReportEntity();
        report.setId(REPORT_ID);
        report.setProjectId(PROJECT_ID);
        report.setStatus(ProgressReportEntity.STATUS_DRAFT);
        report.setDueDate(LocalDate.of(2026, 11, 30));
        report.setVersion(3);
        ProjectEntity project = new ProjectEntity();
        project.setId(PROJECT_ID);
        project.setPiId("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
        project.setDeptId("cccccccccccccccccccccccccccccccc");
        project.setStatus(ProjectEntity.STATUS_EXECUTING);
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(progressReportRepository.selectForUpdate(REPORT_ID)).thenReturn(Optional.of(report));
        when(authorizationService.check(any(), any(), any())).thenReturn(AuthDecision.allow());
        ProjectMemberEntity member = new ProjectMemberEntity();
        member.setProjectId(PROJECT_ID);
        member.setUserId(UuidUtils.formatCompact(ACTOR_ID));
        member.setStatus(ProjectMemberEntity.STATUS_ACTIVE);
        lenient().when(projectMemberRepository.findActiveByProjectId(any(), any()))
                .thenReturn(List.of(member));
        lenient().when(progressReportRepository.update(any())).thenReturn(1);
        auth = new AuthContext(ACTOR_ID, null, "E1001",
                Set.of("execution:report:submit"), Set.of(), NOW);
    }

    private SubmitProgressReportCmd cmd(int expectedVersion) {
        return new SubmitProgressReportCmd(expectedVersion, Map.of(
                "work_done", "完成传感器选型与原型搭建",
                "deviation", "样机测试晚于计划两周",
                "issues", List.of("供应链延迟"),
                "next_plan", "完成小试"), List.of(), REQUEST_ID);
    }

    @Test
    void tcExec008OnTimeSubmitCreatesVersionOneNotLate() {
        when(versionRepository.findMaxVersionNo(REPORT_ID)).thenReturn(0);

        ProgressReportSubmitted result = service.submit(REPORT_ID, cmd(3), auth);

        assertThat(result.versionNo()).isEqualTo(1);
        assertThat(result.lateFlag()).isFalse();
        assertThat(result.status()).isEqualTo(ProgressReportEntity.STATUS_SUBMITTED);
        assertThat(report.getStatus()).isEqualTo(ProgressReportEntity.STATUS_SUBMITTED);
        assertThat(report.getCurrentVersionId()).isEqualTo(result.versionId());

        ArgumentCaptor<ProgressReportVersionEntity> captor =
                ArgumentCaptor.forClass(ProgressReportVersionEntity.class);
        verify(versionRepository).insert(captor.capture());
        ProgressReportVersionEntity version = captor.getValue();
        assertThat(version.getVersionNo()).isEqualTo(1);
        assertThat(version.getLateFlag()).isFalse();
        assertThat(version.getDecision()).isEqualTo(ProgressReportVersionEntity.DECISION_PENDING);
        assertThat(version.getContent()).contains("完成传感器选型与原型搭建");
        assertThat(version.getContentHash()).matches("[0-9a-f]{64}");
        assertThat(version.getSubmittedBy()).isEqualTo(UuidUtils.formatCompact(ACTOR_ID));

        verify(riskPort, never()).raiseLateReportRisk(any(), any(), any(), anyLong());
    }

    @Test
    void tcExec009LateSubmitSetsLateFlagAndRaisesRisk() {
        report.setDueDate(LocalDate.of(2026, 11, 16));
        when(versionRepository.findMaxVersionNo(REPORT_ID)).thenReturn(0);

        ProgressReportSubmitted result = service.submit(REPORT_ID, cmd(3), auth);

        assertThat(result.lateFlag()).isTrue();
        ArgumentCaptor<ProgressReportVersionEntity> captor =
                ArgumentCaptor.forClass(ProgressReportVersionEntity.class);
        verify(versionRepository).insert(captor.capture());
        assertThat(captor.getValue().getLateFlag()).isTrue();
        verify(riskPort).raiseLateReportRisk(PROJECT_ID, REPORT_ID, result.versionId(), 2L);
    }

    @Test
    void tcExec011ResubmitAfterReturnCreatesVersionTwoAndKeepsOldVersion() {
        report.setStatus(ProgressReportEntity.STATUS_RETURNED);
        report.setCurrentVersionId(OLD_VERSION_ID);
        report.setVersion(5);
        when(versionRepository.findMaxVersionNo(REPORT_ID)).thenReturn(1);

        ProgressReportSubmitted result = service.submit(REPORT_ID, cmd(5), auth);

        assertThat(result.versionNo()).isEqualTo(2);
        ArgumentCaptor<ProgressReportVersionEntity> captor =
                ArgumentCaptor.forClass(ProgressReportVersionEntity.class);
        verify(versionRepository).insert(captor.capture());
        assertThat(captor.getValue().getVersionNo()).isEqualTo(2);
        verify(versionRepository, never()).update(any());
    }

    @Test
    void staleExpectedVersionReturnsCommon409WithoutInsert() {
        report.setVersion(4);

        assertThatThrownBy(() -> service.submit(REPORT_ID, cmd(3), auth))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.COMMON_409));

        verify(versionRepository, never()).insert(any());
        verify(progressReportRepository, never()).update(any());
        verifyNoInteractions(riskPort);
    }

    @Test
    void missingWorkDoneReturnsCommon400WithoutInsert() {
        SubmitProgressReportCmd cmd = new SubmitProgressReportCmd(3,
                Map.of("deviation", "仅有偏差说明"), List.of(), REQUEST_ID);

        assertThatThrownBy(() -> service.submit(REPORT_ID, cmd, auth))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.COMMON_400));

        verify(versionRepository, never()).insert(any());
        verifyNoInteractions(riskPort);
    }

    @Test
    void nonMemberWithoutPermissionReturns403() {
        when(authorizationService.check(any(), any(), any()))
                .thenReturn(AuthDecision.deny(ErrorCode.COMMON_403));

        assertThatThrownBy(() -> service.submit(REPORT_ID, cmd(3), auth))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.COMMON_403));

        verify(versionRepository, never()).insert(any());
        verifyNoInteractions(riskPort);
    }
}
