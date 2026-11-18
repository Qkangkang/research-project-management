package cn.edu.rpm.project.domain.service;

import cn.edu.rpm.audit.domain.model.AuditEvent;
import cn.edu.rpm.audit.domain.service.AuditRecorder;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.file.domain.model.FileObject;
import cn.edu.rpm.file.domain.model.FileStatus;
import cn.edu.rpm.file.domain.model.ScanStatus;
import cn.edu.rpm.file.domain.port.FileObjectQueryPort;
import cn.edu.rpm.iam.application.dto.AuthContext;
import cn.edu.rpm.iam.application.dto.AuthDecision;
import cn.edu.rpm.iam.domain.service.AuthorizationService;
import cn.edu.rpm.project.application.dto.CloseProjectRiskCmd;
import cn.edu.rpm.project.application.dto.CreateProjectRiskCmd;
import cn.edu.rpm.project.application.dto.ProjectRiskView;
import cn.edu.rpm.project.infrastructure.entity.ProjectEntity;
import cn.edu.rpm.project.infrastructure.entity.ProjectMemberEntity;
import cn.edu.rpm.project.infrastructure.entity.ProjectRiskEntity;
import cn.edu.rpm.project.infrastructure.repository.ProjectMemberRepository;
import cn.edu.rpm.project.infrastructure.repository.ProjectRepository;
import cn.edu.rpm.project.infrastructure.repository.ProjectRiskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ProjectRiskService} 纯单元测试（T-PRJ-015）。
 *
 * <p>覆盖 TC-EXEC-016～020：登记默认 {@code OPEN}/MEDIUM；关闭必须有处置结果与可用证据；
 * 逾期 1 天升级一级；截止日 23:59:59 前不提前升级。</p>
 */
@ExtendWith(MockitoExtension.class)
class ProjectRiskServiceTest {

    private static final String PROJECT_ID = "8d7c6b5a4e3f4a2b9c1d0e2f3a4b5c6d";
    private static final String RISK_ID = "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee";
    private static final String EVIDENCE_FILE_ID = "ffffffffffffffffffffffffffffffff";
    private static final String REPORT_ID = "11111111111111111111111111111111";
    private static final String REPORT_VERSION_ID = "22222222222222222222222222222222";
    private static final String OWNER_ID = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final UUID ACTOR_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final Instant NOW = Instant.parse("2026-11-18T02:00:00Z");

    @Mock
    private ProjectRiskRepository projectRiskRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectMemberRepository projectMemberRepository;

    @Mock
    private FileObjectQueryPort fileObjectQueryPort;

    @Mock
    private AuthorizationService authorizationService;

    @Mock
    private AuditRecorder auditRecorder;

    private ProjectRiskService service;

    private ProjectEntity project;

    private AuthContext auth;

    @BeforeEach
    void setUp() {
        service = new ProjectRiskService(projectRiskRepository, projectRepository,
                projectMemberRepository, fileObjectQueryPort, authorizationService, auditRecorder,
                Clock.fixed(NOW, ZoneId.of("Asia/Shanghai")));
        project = new ProjectEntity();
        project.setId(PROJECT_ID);
        project.setPiId("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        project.setDeptId("dddddddddddddddddddddddddddddddd");
        project.setStatus(ProjectEntity.STATUS_EXECUTING);
        auth = new AuthContext(ACTOR_ID, null, "E001",
                Set.of("project:risk:create", "project:risk:close"), Set.of(), NOW);
    }

    private void allowPermission() {
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findActiveByProjectId(anyString(), any())).thenReturn(List.of());
        when(authorizationService.check(any(), any(), any())).thenReturn(AuthDecision.allow());
    }

    private CreateProjectRiskCmd createCmd(String level) {
        return new CreateProjectRiskCmd(OWNER_ID, "PROGRESS", "关键设备到货延迟",
                "供应商产能不足", level, LocalDate.of(2026, 12, 31), null);
    }

    private ProjectRiskEntity openRisk(String level, LocalDate dueDate) {
        ProjectRiskEntity risk = new ProjectRiskEntity();
        risk.setId(RISK_ID);
        risk.setProjectId(PROJECT_ID);
        risk.setOwnerId(OWNER_ID);
        risk.setRiskType("PROGRESS");
        risk.setTitle("关键设备到货延迟");
        risk.setRiskLevel(level);
        risk.setDueDate(dueDate);
        risk.setStatus(ProjectRiskEntity.STATUS_OPEN);
        return risk;
    }

    private FileObject availableFile() {
        return FileObject.rehydrate(UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"),
                "rpm", "risk/evidence.pdf", "evidence.pdf", "application/pdf", 1024L,
                null, FileStatus.AVAILABLE, ScanStatus.PASSED, ACTOR_ID, NOW, 0);
    }

    @Test
    void tcExec016CreateRiskDefaultsToOpenMedium() {
        allowPermission();
        doAnswer(invocation -> {
            ((ProjectRiskEntity) invocation.getArgument(0)).setId(RISK_ID);
            return null;
        }).when(projectRiskRepository).insert(any());

        ProjectRiskView view = service.create(PROJECT_ID, createCmd(null), auth);

        assertThat(view.riskId()).isEqualTo(RISK_ID);
        assertThat(view.status()).isEqualTo(ProjectRiskEntity.STATUS_OPEN);
        assertThat(view.riskLevel()).isEqualTo(ProjectRiskEntity.LEVEL_MEDIUM);
        ArgumentCaptor<AuditEvent> audit = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditRecorder).record(audit.capture());
        assertThat(audit.getValue().action()).isEqualTo(ProjectRiskService.AUDIT_RISK_CREATED);
    }

    @Test
    void createRejectsMissingOwner() {
        allowPermission();
        CreateProjectRiskCmd cmd = new CreateProjectRiskCmd(" ", "PROGRESS", "标题",
                null, "HIGH", null, null);

        assertThatThrownBy(() -> service.create(PROJECT_ID, cmd, auth))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.COMMON_400));
        verify(projectRiskRepository, never()).insert(any());
    }

    @Test
    void createRejectsUnknownRiskLevel() {
        allowPermission();

        assertThatThrownBy(() -> service.create(PROJECT_ID, createCmd("URGENT"), auth))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.COMMON_400));
    }

    @Test
    void unauthorizedCreateReturnsCommon403() {
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findActiveByProjectId(anyString(), any())).thenReturn(List.of());
        when(authorizationService.check(any(), any(), any()))
                .thenReturn(AuthDecision.deny(ErrorCode.COMMON_403));

        assertThatThrownBy(() -> service.create(PROJECT_ID, createCmd("HIGH"), auth))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.COMMON_403));
    }

    @Test
    void tcExec017CloseWithoutResolutionReturnsCommon400() {
        when(projectRiskRepository.findById(RISK_ID))
                .thenReturn(Optional.of(openRisk(ProjectRiskEntity.LEVEL_HIGH, LocalDate.of(2026, 12, 31))));
        allowPermission();

        assertThatThrownBy(() -> service.close(RISK_ID,
                new CloseProjectRiskCmd(" ", EVIDENCE_FILE_ID), auth))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.COMMON_400));
        verify(fileObjectQueryPort, never()).findById(any());
    }

    @Test
    void tcExec018CloseWithUnavailableEvidenceReturnsFile001() {
        when(projectRiskRepository.findById(RISK_ID))
                .thenReturn(Optional.of(openRisk(ProjectRiskEntity.LEVEL_HIGH, LocalDate.of(2026, 12, 31))));
        allowPermission();
        FileObject quarantined = FileObject.rehydrate(UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"),
                "rpm", "risk/evidence.pdf", "evidence.pdf", "application/pdf", 1024L,
                null, FileStatus.QUARANTINED, ScanStatus.PENDING, ACTOR_ID, NOW, 0);
        when(fileObjectQueryPort.findById(any())).thenReturn(Optional.of(quarantined));

        assertThatThrownBy(() -> service.close(RISK_ID,
                new CloseProjectRiskCmd("已更换供应商", EVIDENCE_FILE_ID), auth))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.FILE_001));
        verify(projectRiskRepository, never()).update(any());
    }

    @Test
    void closeWithResolutionAndEvidenceClosesRisk() {
        ProjectRiskEntity risk = openRisk(ProjectRiskEntity.LEVEL_HIGH, LocalDate.of(2026, 12, 31));
        when(projectRiskRepository.findById(RISK_ID)).thenReturn(Optional.of(risk));
        allowPermission();
        when(fileObjectQueryPort.findById(any())).thenReturn(Optional.of(availableFile()));
        when(projectRiskRepository.update(risk)).thenReturn(1);

        ProjectRiskView view = service.close(RISK_ID,
                new CloseProjectRiskCmd("已更换供应商", EVIDENCE_FILE_ID), auth);

        assertThat(view.status()).isEqualTo(ProjectRiskEntity.STATUS_CLOSED);
        assertThat(risk.getResolution()).isEqualTo("已更换供应商");
        assertThat(risk.getClosedBy()).isEqualTo("cccccccccccccccccccccccccccccccc");
        ArgumentCaptor<AuditEvent> audit = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditRecorder).record(audit.capture());
        assertThat(audit.getValue().action()).isEqualTo(ProjectRiskService.AUDIT_RISK_CLOSED);
    }

    @Test
    void closeAlreadyClosedRiskReturnsCommon422() {
        ProjectRiskEntity risk = openRisk(ProjectRiskEntity.LEVEL_HIGH, LocalDate.of(2026, 12, 31));
        risk.setStatus(ProjectRiskEntity.STATUS_CLOSED);
        when(projectRiskRepository.findById(RISK_ID)).thenReturn(Optional.of(risk));
        allowPermission();
        when(fileObjectQueryPort.findById(any())).thenReturn(Optional.of(availableFile()));

        assertThatThrownBy(() -> service.close(RISK_ID,
                new CloseProjectRiskCmd("重复关闭", EVIDENCE_FILE_ID), auth))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.COMMON_422));
    }

    @Test
    void tcExec019OverdueRiskEscalatesOneLevel() {
        ProjectRiskEntity risk = openRisk(ProjectRiskEntity.LEVEL_LOW, LocalDate.of(2026, 11, 17));
        when(projectRiskRepository.findOpenDueBy(LocalDate.of(2026, 11, 18)))
                .thenReturn(List.of(risk));
        when(projectRiskRepository.update(risk)).thenReturn(1);

        assertThat(service.escalateOverdue()).isEqualTo(1);
        assertThat(risk.getStatus()).isEqualTo(ProjectRiskEntity.STATUS_ESCALATED);
        assertThat(risk.getRiskLevel()).isEqualTo(ProjectRiskEntity.LEVEL_MEDIUM);
        ArgumentCaptor<AuditEvent> audit = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditRecorder).record(audit.capture());
        assertThat(audit.getValue().action()).isEqualTo(ProjectRiskService.AUDIT_RISK_ESCALATED);
    }

    @Test
    void tcExec020DueTodayNotEscalatedBefore235959() {
        ProjectRiskEntity risk = openRisk(ProjectRiskEntity.LEVEL_LOW, LocalDate.of(2026, 11, 18));
        when(projectRiskRepository.findOpenDueBy(LocalDate.of(2026, 11, 18)))
                .thenReturn(List.of(risk));

        assertThat(service.escalateOverdue()).isZero();
        assertThat(risk.getStatus()).isEqualTo(ProjectRiskEntity.STATUS_OPEN);
        assertThat(risk.getRiskLevel()).isEqualTo(ProjectRiskEntity.LEVEL_LOW);
        verify(projectRiskRepository, never()).update(any());
    }

    @Test
    void escalateCapsCriticalLevel() {
        ProjectRiskEntity risk = openRisk(ProjectRiskEntity.LEVEL_CRITICAL, LocalDate.of(2026, 11, 10));
        when(projectRiskRepository.findOpenDueBy(LocalDate.of(2026, 11, 18)))
                .thenReturn(List.of(risk));
        when(projectRiskRepository.update(risk)).thenReturn(1);

        assertThat(service.escalateOverdue()).isEqualTo(1);
        assertThat(risk.getRiskLevel()).isEqualTo(ProjectRiskEntity.LEVEL_CRITICAL);
    }

    @Test
    void escalateConflictReturnsCommon409() {
        ProjectRiskEntity risk = openRisk(ProjectRiskEntity.LEVEL_MEDIUM, LocalDate.of(2026, 11, 10));
        when(projectRiskRepository.findOpenDueBy(LocalDate.of(2026, 11, 18)))
                .thenReturn(List.of(risk));
        when(projectRiskRepository.update(risk)).thenReturn(0);

        assertThatThrownBy(() -> service.escalateOverdue())
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.COMMON_409));
    }

    @Test
    void lateReportRaisesOpenScheduleRiskOwnedByPi() {
        when(projectRiskRepository.findBySourceRule("LATE_REPORT:" + REPORT_ID))
                .thenReturn(Optional.empty());
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        doAnswer(invocation -> {
            ((ProjectRiskEntity) invocation.getArgument(0)).setId(RISK_ID);
            return null;
        }).when(projectRiskRepository).insert(any());

        service.raiseLateReportRisk(PROJECT_ID, REPORT_ID, REPORT_VERSION_ID, 2L);

        ArgumentCaptor<ProjectRiskEntity> captor = ArgumentCaptor.forClass(ProjectRiskEntity.class);
        verify(projectRiskRepository).insert(captor.capture());
        ProjectRiskEntity risk = captor.getValue();
        assertThat(risk.getProjectId()).isEqualTo(PROJECT_ID);
        assertThat(risk.getOwnerId()).isEqualTo("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        assertThat(risk.getRiskType()).isEqualTo("SCHEDULE");
        assertThat(risk.getRiskLevel()).isEqualTo(ProjectRiskEntity.LEVEL_MEDIUM);
        assertThat(risk.getStatus()).isEqualTo(ProjectRiskEntity.STATUS_OPEN);
        assertThat(risk.getSourceRule()).isEqualTo("LATE_REPORT:" + REPORT_ID);
        assertThat(risk.getTitle()).contains("迟报 2 天");
        verify(auditRecorder).record(any(AuditEvent.class));
    }

    @Test
    void lateReportRiskIsIdempotentBySourceRule() {
        when(projectRiskRepository.findBySourceRule("LATE_REPORT:" + REPORT_ID))
                .thenReturn(Optional.of(openRisk(ProjectRiskEntity.LEVEL_MEDIUM, null)));

        service.raiseLateReportRisk(PROJECT_ID, REPORT_ID, REPORT_VERSION_ID, 2L);

        verify(projectRiskRepository, never()).insert(any());
        verify(auditRecorder, never()).record(any(AuditEvent.class));
    }
}
