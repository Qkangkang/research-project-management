package cn.edu.rpm.application.application.service;

import cn.edu.rpm.application.application.dto.ExternalRegistrationResult;
import cn.edu.rpm.application.application.dto.RegisterExternalProjectCmd;
import cn.edu.rpm.application.domain.model.ProjectCreationCommand;
import cn.edu.rpm.application.domain.model.ProjectCreationResult;
import cn.edu.rpm.application.domain.port.ProjectCreationPort;
import cn.edu.rpm.application.infrastructure.entity.ApplicationEntity;
import cn.edu.rpm.application.infrastructure.entity.ApplicationVersionEntity;
import cn.edu.rpm.application.infrastructure.entity.ApplyBatchEntity;
import cn.edu.rpm.application.infrastructure.entity.ExternalProjectRegistrationEntity;
import cn.edu.rpm.application.infrastructure.repository.ApplicationRepository;
import cn.edu.rpm.application.infrastructure.repository.ApplicationVersionRepository;
import cn.edu.rpm.application.infrastructure.repository.ApplyBatchRepository;
import cn.edu.rpm.application.infrastructure.repository.ExternalProjectRegistrationRepository;
import cn.edu.rpm.audit.domain.service.AuditRecorder;
import cn.edu.rpm.common.domain.port.OutboxAppendPort;
import cn.edu.rpm.common.domain.service.NumberGenerator;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.common.util.CanonicalJsonHasher;
import cn.edu.rpm.file.domain.model.FileObject;
import cn.edu.rpm.file.domain.model.FileStatus;
import cn.edu.rpm.file.domain.model.ScanStatus;
import cn.edu.rpm.file.infrastructure.repository.FileObjectRepository;
import cn.edu.rpm.iam.application.dto.AuthContext;
import cn.edu.rpm.iam.application.dto.AuthDecision;
import cn.edu.rpm.iam.domain.service.AuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
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
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link ExternalProjectAppService} 纯单元测试（T-APP-020）。
 *
 * <p>覆盖 TC-APP-032（无批复号且无合同号）、TC-APP-033（缺核验人/证明材料）、
 * TC-APP-034（齐全时生成申报与项目）、TC-APP-035（同合同号重复 409）、
 * TC-APP-042（三个外部编号独立保存）。</p>
 */
@ExtendWith(MockitoExtension.class)
class ExternalProjectAppServiceTest {

    private static final String BATCH_ID = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final String DEPT_ID = "dddddddddddddddddddddddddddddddd";
    private static final String PI_ID = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String VERIFIED_BY = "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee";
    private static final String FILE_ID = "ffffffffffffffffffffffffffffffff";
    private static final String APP_ID = "11111111111111111111111111111111";
    private static final String VERSION_ID = "22222222222222222222222222222222";
    private static final UUID ACTOR_ID = UUID.fromString("33333333-4444-5555-6666-777777777777");
    private static final UUID REQUEST_ID = UUID.fromString("abcdabcd-abcd-abcd-abcd-abcdabcdabcd");
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-11-18T02:00:00Z"), ZoneId.of("Asia/Shanghai"));

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private ApplicationVersionRepository applicationVersionRepository;

    @Mock
    private ApplyBatchRepository applyBatchRepository;

    @Mock
    private ExternalProjectRegistrationRepository registrationRepository;

    @Mock
    private ProjectCreationPort projectCreationPort;

    @Mock
    private FileObjectRepository fileObjectRepository;

    @Mock
    private NumberGenerator numberGenerator;

    @Mock
    private OutboxAppendPort outboxAppendPort;

    @Mock
    private AuthorizationService authorizationService;

    @Mock
    private AuditRecorder auditRecorder;

    private ExternalProjectAppService service;

    private AuthContext auth;

    @BeforeEach
    void setUp() {
        CanonicalJsonHasher hasher = new CanonicalJsonHasher();
        service = new ExternalProjectAppService(applicationRepository, applicationVersionRepository,
                applyBatchRepository, registrationRepository, projectCreationPort,
                fileObjectRepository, numberGenerator, outboxAppendPort, authorizationService,
                auditRecorder, hasher, CLOCK);
        auth = new AuthContext(ACTOR_ID, null, "E3001",
                Set.of("application:external-register"), Set.of(),
                Instant.parse("2026-11-18T02:00:00Z"));
    }

    private RegisterExternalProjectCmd cmd(String approvalNo, String contractNo, String financialNo,
                                           String verifiedBy, List<String> evidence) {
        return new RegisterExternalProjectCmd(BATCH_ID, "EXTERNAL_CONTRACT", "横向合作项目",
                DEPT_ID, PI_ID, "某研究院", approvalNo, contractNo, financialNo,
                new BigDecimal("1200000.00"), LocalDate.of(2026, 12, 1),
                LocalDate.of(2028, 11, 30), verifiedBy, evidence, REQUEST_ID);
    }

    private void stubBase() {
        ApplyBatchEntity batch = new ApplyBatchEntity();
        batch.setId(BATCH_ID);
        when(applyBatchRepository.findById(BATCH_ID)).thenReturn(Optional.of(batch));
        when(authorizationService.check(any(), any(), any())).thenReturn(AuthDecision.allow());
        when(numberGenerator.next(any())).thenReturn("APP-2026-000123");
        when(applicationRepository.update(any())).thenReturn(1);
        when(projectCreationPort.createFromVersion(any())).thenReturn(
                new ProjectCreationResult("44444444444444444444444444444444", "KY-2026-000456",
                        "55555555555555555555555555555555"));
        FileObject file = FileObject.rehydrate(UUID.fromString(
                        "ffffffff-ffff-ffff-ffff-ffffffffffff"), "bucket", "key", "批复.pdf",
                "application/pdf", 1024, "a".repeat(64), FileStatus.AVAILABLE,
                ScanStatus.SKIPPED, ACTOR_ID, Instant.parse("2026-11-17T00:00:00Z"), 0);
        when(fileObjectRepository.findByIds(anyCollection())).thenReturn(List.of(file));
    }

    @Test
    void tcApp032WithoutApprovalAndContractReturnsApply005() {
        assertThatThrownBy(() -> service.register(
                cmd(null, null, null, VERIFIED_BY, List.of(FILE_ID)), auth))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.APPLY_005));
        verifyNoInteractions(applicationRepository, projectCreationPort);
    }

    @Test
    void tcApp033MissingVerifierOrEvidenceReturnsApply005() {
        assertThatThrownBy(() -> service.register(
                cmd(null, "HT-2026-0088", null, null, List.of(FILE_ID)), auth))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.APPLY_005));

        assertThatThrownBy(() -> service.register(
                cmd(null, "HT-2026-0088", null, VERIFIED_BY, List.of()), auth))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.APPLY_005));

        verify(applicationRepository, never()).insert(any());
        verifyNoInteractions(projectCreationPort);
    }

    @Test
    void tcApp034CompleteRegistrationCreatesApprovedApplicationAndProject() {
        stubBase();
        when(registrationRepository.findByContractNo("HT-2026-0088")).thenReturn(Optional.empty());
        RegisterExternalProjectCmd cmd = cmd("批复-2026-001", "HT-2026-0088", null,
                VERIFIED_BY, List.of(FILE_ID));
        ArgumentCaptor<ApplicationEntity> appCaptor = ArgumentCaptor.forClass(ApplicationEntity.class);
        ArgumentCaptor<ApplicationVersionEntity> versionCaptor =
                ArgumentCaptor.forClass(ApplicationVersionEntity.class);
        ArgumentCaptor<ProjectCreationCommand> creationCaptor =
                ArgumentCaptor.forClass(ProjectCreationCommand.class);

        ExternalRegistrationResult result = service.register(cmd, auth);

        assertThat(result.applicationId()).isNotNull();
        assertThat(result.applicationNo()).isEqualTo("APP-2026-000123");
        assertThat(result.projectId()).isEqualTo("44444444444444444444444444444444");
        assertThat(result.baselineId()).isEqualTo("55555555555555555555555555555555");

        verify(applicationRepository).insert(appCaptor.capture());
        ApplicationEntity application = appCaptor.getValue();
        assertThat(application.getSourceType()).isEqualTo(ApplicationEntity.SOURCE_EXTERNAL);
        assertThat(application.getStatus()).isEqualTo(ApplicationEntity.STATUS_APPROVED);

        verify(applicationVersionRepository).insert(versionCaptor.capture());
        assertThat(versionCaptor.getValue().getStatus())
                .isEqualTo(ApplicationVersionEntity.STATUS_APPROVED);
        assertThat(versionCaptor.getValue().getContentHash()).matches("[0-9a-f]{64}");

        verify(projectCreationPort).createFromVersion(creationCaptor.capture());
        assertThat(creationCaptor.getValue().sourceType())
                .isEqualTo(ProjectCreationCommand.SOURCE_EXTERNAL);
        assertThat(creationCaptor.getValue().externalApprovalNo()).isEqualTo("批复-2026-001");
        assertThat(creationCaptor.getValue().externalContractNo()).isEqualTo("HT-2026-0088");

        ArgumentCaptor<ExternalProjectRegistrationEntity> regCaptor =
                ArgumentCaptor.forClass(ExternalProjectRegistrationEntity.class);
        verify(registrationRepository).insert(regCaptor.capture());
        assertThat(regCaptor.getValue().getApprovalNo()).isEqualTo("批复-2026-001");
        assertThat(regCaptor.getValue().getContractNo()).isEqualTo("HT-2026-0088");
        assertThat(regCaptor.getValue().getVerifiedBy()).isEqualTo(VERIFIED_BY);
    }

    @Test
    void tcApp035DuplicateContractReturnsCommon409WithoutProject() {
        ApplyBatchEntity batch = new ApplyBatchEntity();
        batch.setId(BATCH_ID);
        when(applyBatchRepository.findById(BATCH_ID)).thenReturn(Optional.of(batch));
        when(authorizationService.check(any(), any(), any())).thenReturn(AuthDecision.allow());
        ExternalProjectRegistrationEntity existing = new ExternalProjectRegistrationEntity();
        existing.setContractNo("HT-2026-0088");
        when(registrationRepository.findByContractNo("HT-2026-0088")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.register(
                cmd(null, "HT-2026-0088", null, VERIFIED_BY, List.of(FILE_ID)), auth))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.COMMON_409));

        verify(applicationRepository, never()).insert(any());
        verifyNoInteractions(projectCreationPort);
    }

    @Test
    void tcApp042ThreeExternalNumbersAreStoredIndependently() {
        stubBase();
        when(registrationRepository.findByContractNo("HT-2026-0088")).thenReturn(Optional.empty());
        service.register(cmd("批复-2026-001", "HT-2026-0088", "CW-2026-0099",
                VERIFIED_BY, List.of(FILE_ID)), auth);

        ArgumentCaptor<ExternalProjectRegistrationEntity> regCaptor =
                ArgumentCaptor.forClass(ExternalProjectRegistrationEntity.class);
        verify(registrationRepository).insert(regCaptor.capture());
        assertThat(regCaptor.getValue().getApprovalNo()).isEqualTo("批复-2026-001");
        assertThat(regCaptor.getValue().getContractNo()).isEqualTo("HT-2026-0088");

        ArgumentCaptor<ProjectCreationCommand> creationCaptor =
                ArgumentCaptor.forClass(ProjectCreationCommand.class);
        verify(projectCreationPort).createFromVersion(creationCaptor.capture());
        assertThat(creationCaptor.getValue().financialProjectNo()).isEqualTo("CW-2026-0099");
        assertThat(creationCaptor.getValue().snapshot()).containsEntry(
                "financial_project_no", "CW-2026-0099");
    }
}
