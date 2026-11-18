package cn.edu.rpm.output.application.service;

import cn.edu.rpm.audit.domain.model.AuditEvent;
import cn.edu.rpm.audit.domain.service.AuditRecorder;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.common.util.CanonicalJsonHasher;
import cn.edu.rpm.file.domain.model.FileObject;
import cn.edu.rpm.file.domain.model.FileStatus;
import cn.edu.rpm.file.domain.model.ScanStatus;
import cn.edu.rpm.file.domain.port.FileObjectQueryPort;
import cn.edu.rpm.iam.application.dto.AuthContext;
import cn.edu.rpm.iam.application.dto.AuthDecision;
import cn.edu.rpm.iam.domain.service.AuthorizationService;
import cn.edu.rpm.output.application.dto.CreateOutputCmd;
import cn.edu.rpm.output.application.dto.OutputCreated;
import cn.edu.rpm.output.application.dto.OutputDraftSaved;
import cn.edu.rpm.output.application.dto.SaveOutputDraftCmd;
import cn.edu.rpm.output.domain.model.DuplicateMatchPolicy;
import cn.edu.rpm.output.domain.service.OutputDuplicateService;
import cn.edu.rpm.output.domain.service.OutputTypeTemplateResolver;
import cn.edu.rpm.output.infrastructure.entity.OutputDuplicateCandidateEntity;
import cn.edu.rpm.output.infrastructure.entity.OutputEvidenceEntity;
import cn.edu.rpm.output.infrastructure.entity.OutputVersionEntity;
import cn.edu.rpm.output.infrastructure.entity.ResearchOutputEntity;
import cn.edu.rpm.output.infrastructure.repository.OutputEvidenceRepository;
import cn.edu.rpm.output.infrastructure.repository.OutputVersionRepository;
import cn.edu.rpm.output.infrastructure.repository.ResearchOutputRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link OutputAppService} 纯单元测试（T-OUT-008 先行测试）。
 *
 * <p>覆盖 TC-OUT-001（登记生成 DRAFT + 版本 1）、TC-OUT-002（缺标题 → 422）、
 * TC-OUT-003（DOI 非法 → 400）、TC-OUT-004（未来完成日期 → 400）、
 * TC-OUT-006（DOI 重复仍保存并返回 RPM-OUT-001）、TC-OUT-011（修订生成
 * {@code version_no=2}，旧版本不删不改）。仓库、授权、审计、文件端口均 mock，
 * 类型模板与摘要使用真实实现。</p>
 */
@ExtendWith(MockitoExtension.class)
class OutputAppServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-18T02:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneId.of("Asia/Shanghai"));
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 18);

    private static final String OUTPUT_ID = "11112222333344445555666677778888";
    private static final String OWNER_ID = "99998888777766665555444433332222";
    private static final String DEPT_ID = "aaaabbbbccccddddeeeeffff00001111";
    private static final String FILE_ID = "bbbbccccddddeeeeffff000011112222";
    private static final String VERSION_ID = "ccccddddeeeeffff0000111122223333";
    private static final String HASH = "a".repeat(64);

    @Mock
    private ResearchOutputRepository researchOutputRepository;

    @Mock
    private OutputVersionRepository outputVersionRepository;

    @Mock
    private OutputEvidenceRepository outputEvidenceRepository;

    @Mock
    private OutputDuplicateService duplicateService;

    @Mock
    private FileObjectQueryPort fileObjectQueryPort;

    @Mock
    private AuthorizationService authorizationService;

    @Mock
    private AuditRecorder auditRecorder;

    private OutputAppService service;

    private AuthContext auth;

    @BeforeEach
    void setUp() {
        service = new OutputAppService(researchOutputRepository, outputVersionRepository,
                outputEvidenceRepository, new OutputTypeTemplateResolver(), duplicateService,
                fileObjectQueryPort, authorizationService, auditRecorder,
                new CanonicalJsonHasher(), CLOCK);
        auth = new AuthContext(UUID.fromString("99998888-7777-6666-5555-444433332222"),
                null, "E1001", Set.of("output:create", "output:edit"), Set.of(), NOW);
        lenient().when(authorizationService.check(any(), any(), any()))
                .thenReturn(AuthDecision.allow());
    }

    private static CreateOutputCmd createCmd(String title, String doi, LocalDate completedAt,
                                             List<String> evidenceFileIds) {
        return new CreateOutputCmd("PAPER", title, doi, null, completedAt, null, DEPT_ID,
                Map.of("journal", "某学报"), evidenceFileIds);
    }

    private void stubAvailableFile() {
        UUID fileId = UUID.fromString("bbbbcccc-dddd-eeee-ffff-000011112222");
        when(fileObjectQueryPort.findById(fileId)).thenReturn(Optional.of(FileObject.rehydrate(
                fileId, "rpm", "outputs/evidence.pdf", "evidence.pdf", "application/pdf",
                1024L, HASH, FileStatus.AVAILABLE, ScanStatus.PASSED,
                UUID.fromString("99998888-7777-6666-5555-444433332222"), NOW, 0)));
    }

    private static ResearchOutputEntity existing(String status, String currentVersionId) {
        ResearchOutputEntity output = ResearchOutputEntity.newDraft(
                ResearchOutputEntity.TYPE_PAPER, "复杂工况下的多模态感知方法",
                "10.1000/example.2026.001", null, OWNER_ID, DEPT_ID, TODAY.minusDays(3));
        output.setId(OUTPUT_ID);
        output.setStatus(status);
        output.setCurrentVersionId(currentVersionId);
        output.setVersion(2);
        return output;
    }

    @Test
    void createDraftPersistsDraftWithVersionOneAndEvidence() {
        stubAvailableFile();
        when(duplicateService.findCandidates(anyString(), any())).thenReturn(List.of());

        OutputCreated created = service.createDraft(
                createCmd("复杂工况下的多模态感知方法", "10.1000/example.2026.001",
                        TODAY.minusDays(3), List.of(FILE_ID)), auth);

        assertThat(created.status()).isEqualTo(ResearchOutputEntity.STATUS_DRAFT);
        assertThat(created.versionNo()).isEqualTo(1);
        assertThat(created.warningCode()).isNull();

        ArgumentCaptor<ResearchOutputEntity> outputCaptor =
                ArgumentCaptor.forClass(ResearchOutputEntity.class);
        verify(researchOutputRepository).insert(outputCaptor.capture());
        ResearchOutputEntity saved = outputCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo(ResearchOutputEntity.STATUS_DRAFT);
        assertThat(saved.getOwnerId()).isEqualTo(OWNER_ID);
        assertThat(saved.getCurrentVersionId()).isEqualTo(created.versionId());

        ArgumentCaptor<OutputVersionEntity> versionCaptor =
                ArgumentCaptor.forClass(OutputVersionEntity.class);
        verify(outputVersionRepository).insert(versionCaptor.capture());
        assertThat(versionCaptor.getValue().getVersionNo()).isEqualTo(1);
        assertThat(versionCaptor.getValue().getContentHash()).hasSize(64);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<OutputEvidenceEntity>> evidenceCaptor = ArgumentCaptor.forClass(List.class);
        verify(outputEvidenceRepository).insertBatch(evidenceCaptor.capture());
        assertThat(evidenceCaptor.getValue()).hasSize(1);
        assertThat(evidenceCaptor.getValue().get(0).getSha256()).isEqualTo(HASH);

        ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditRecorder).record(auditCaptor.capture());
        assertThat(auditCaptor.getValue().action()).isEqualTo("OUTPUT_CREATED");
    }

    @Test
    void createDraftRejectsMissingTitleWithCommon422() {
        assertThatThrownBy(() -> service.createDraft(
                createCmd("  ", null, TODAY.minusDays(1), List.of()), auth))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.COMMON_422);
        verify(researchOutputRepository, never()).insert(any());
    }

    @Test
    void createDraftRejectsMalformedDoiWithCommon400() {
        assertThatThrownBy(() -> service.createDraft(
                createCmd("复杂工况下的多模态感知方法", "not-a-doi", TODAY.minusDays(1), List.of()), auth))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.COMMON_400);
        verify(researchOutputRepository, never()).insert(any());
    }

    @Test
    void createDraftRejectsFutureCompletedAtWithCommon400() {
        assertThatThrownBy(() -> service.createDraft(
                createCmd("复杂工况下的多模态感知方法", null, TODAY.plusDays(1), List.of()), auth))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.COMMON_400);
    }

    @Test
    void createDraftReturnsOut001WarningWhenDuplicateDoiDetected() {
        when(duplicateService.findCandidates(anyString(), any())).thenReturn(List.of(
                OutputDuplicateCandidateEntity.create("9999aaaabbbbccccddddeeeeffff0000",
                        "8888aaaabbbbccccddddeeeeffff0000",
                        OutputDuplicateCandidateEntity.MATCH_DOI, BigDecimal.ONE,
                        OffsetDateTime.now(CLOCK))));

        OutputCreated created = service.createDraft(
                createCmd("复杂工况下的多模态感知方法", "10.1000/example.2026.001",
                        TODAY.minusDays(3), List.of()), auth);

        assertThat(created.warningCode()).isEqualTo(ErrorCode.OUT_001.getCode());
        assertThat(created.warningMessage()).contains("疑似重复");
        assertThat(created.status()).isEqualTo(ResearchOutputEntity.STATUS_DRAFT);
        verify(researchOutputRepository).insert(any(ResearchOutputEntity.class));
    }

    @Test
    void createDraftRejectsUnavailableEvidenceFileWithFile001() {
        UUID fileId = UUID.fromString("bbbbcccc-dddd-eeee-ffff-000011112222");
        when(fileObjectQueryPort.findById(fileId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createDraft(
                createCmd("复杂工况下的多模态感知方法", null, TODAY.minusDays(1), List.of(FILE_ID)), auth))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FILE_001);
    }

    @Test
    void saveDraftUpdatesCurrentDraftVersionInPlace() {
        ResearchOutputEntity draft = existing(ResearchOutputEntity.STATUS_DRAFT, VERSION_ID);
        when(researchOutputRepository.findById(OUTPUT_ID)).thenReturn(Optional.of(draft));
        when(outputVersionRepository.findMaxVersionNo(OUTPUT_ID)).thenReturn(1);
        OutputVersionEntity version = OutputVersionEntity.create(OUTPUT_ID, 1, "{}", HASH,
                OWNER_ID, OffsetDateTime.now(CLOCK));
        version.setId(VERSION_ID);
        when(outputVersionRepository.findByOutputIdAndVersionNo(OUTPUT_ID, 1))
                .thenReturn(Optional.of(version));
        when(researchOutputRepository.update(draft)).thenReturn(1);

        OutputDraftSaved saved = service.saveDraft(OUTPUT_ID, new SaveOutputDraftCmd(
                "修订后的标题", "10.1000/example.2026.001", null, TODAY.minusDays(2),
                Map.of(), List.of()), auth);

        assertThat(saved.versionNo()).isEqualTo(1);
        assertThat(saved.status()).isEqualTo(ResearchOutputEntity.STATUS_DRAFT);
        verify(outputVersionRepository).update(version);
        verify(outputVersionRepository, never()).insert(any());
        verify(outputEvidenceRepository).deleteByOutputVersionId(VERSION_ID);
        verify(researchOutputRepository).update(draft);
        assertThat(draft.getTitle()).isEqualTo("修订后的标题");
    }

    @Test
    void reviseApprovedOutputCreatesVersionTwoAndKeepsOldVersion() {
        ResearchOutputEntity approved = existing(ResearchOutputEntity.STATUS_APPROVED, VERSION_ID);
        when(researchOutputRepository.findById(OUTPUT_ID)).thenReturn(Optional.of(approved));
        when(outputVersionRepository.findMaxVersionNo(OUTPUT_ID)).thenReturn(1);
        when(researchOutputRepository.update(approved)).thenReturn(1);

        OutputDraftSaved saved = service.revise(OUTPUT_ID, new SaveOutputDraftCmd(
                "修订后的标题", null, null, TODAY.minusDays(2), Map.of(), List.of()), auth);

        assertThat(saved.versionNo()).isEqualTo(2);
        assertThat(saved.status()).isEqualTo(ResearchOutputEntity.STATUS_DRAFT);
        assertThat(approved.getStatus()).isEqualTo(ResearchOutputEntity.STATUS_DRAFT);
        assertThat(approved.getCurrentVersionId()).isEqualTo(saved.versionId());

        ArgumentCaptor<OutputVersionEntity> versionCaptor =
                ArgumentCaptor.forClass(OutputVersionEntity.class);
        verify(outputVersionRepository).insert(versionCaptor.capture());
        assertThat(versionCaptor.getValue().getVersionNo()).isEqualTo(2);
        verify(outputVersionRepository, never()).update(any());
        verify(outputEvidenceRepository, never()).deleteByOutputVersionId(anyString());
    }

    @Test
    void saveDraftRejectsSubmittedOutputWithOut004() {
        ResearchOutputEntity submitted = existing(ResearchOutputEntity.STATUS_SUBMITTED, VERSION_ID);
        when(researchOutputRepository.findById(OUTPUT_ID)).thenReturn(Optional.of(submitted));

        assertThatThrownBy(() -> service.saveDraft(OUTPUT_ID, new SaveOutputDraftCmd(
                "标题", null, null, TODAY.minusDays(1), Map.of(), List.of()), auth))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.OUT_004);
    }

    @Test
    void saveDraftRejectsMissingOutputWithCommon404() {
        when(researchOutputRepository.findById(eq(OUTPUT_ID))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.saveDraft(OUTPUT_ID, new SaveOutputDraftCmd(
                "标题", null, null, TODAY.minusDays(1), Map.of(), List.of()), auth))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.COMMON_404);
    }
}
