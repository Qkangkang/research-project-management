package cn.edu.rpm.output.application.service;

import cn.edu.rpm.audit.domain.model.AuditEvent;
import cn.edu.rpm.audit.domain.service.AuditRecorder;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.iam.application.dto.AuthContext;
import cn.edu.rpm.iam.application.dto.AuthDecision;
import cn.edu.rpm.iam.domain.service.AuthorizationService;
import cn.edu.rpm.output.application.dto.OutputSubmission;
import cn.edu.rpm.output.infrastructure.entity.OutputVersionEntity;
import cn.edu.rpm.output.infrastructure.entity.ResearchOutputEntity;
import cn.edu.rpm.output.infrastructure.repository.OutputEvidenceRepository;
import cn.edu.rpm.output.infrastructure.repository.OutputVersionRepository;
import cn.edu.rpm.output.infrastructure.repository.ResearchOutputRepository;
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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link OutputSubmitAppService} 纯单元测试（T-OUT-009 先行测试；TC-OUT-005）。
 *
 * <p>覆盖：无证据提交返回 {@code RPM-OUT-002} 且成果保持 DRAFT（不写库）；
 * 有证据提交成功且固定当前版本；非草稿状态返回 {@code RPM-OUT-004}。</p>
 */
@ExtendWith(MockitoExtension.class)
class OutputSubmitAppServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-18T02:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneId.of("Asia/Shanghai"));

    private static final String OUTPUT_ID = "11112222333344445555666677778888";
    private static final String OWNER_ID = "99998888777766665555444433332222";
    private static final String DEPT_ID = "aaaabbbbccccddddeeeeffff00001111";
    private static final String VERSION_ID = "ccccddddeeeeffff0000111122223333";
    private static final String HASH = "a".repeat(64);

    @Mock
    private ResearchOutputRepository researchOutputRepository;

    @Mock
    private OutputVersionRepository outputVersionRepository;

    @Mock
    private OutputEvidenceRepository outputEvidenceRepository;

    @Mock
    private AuthorizationService authorizationService;

    @Mock
    private AuditRecorder auditRecorder;

    private OutputSubmitAppService service;

    private AuthContext auth;

    @BeforeEach
    void setUp() {
        service = new OutputSubmitAppService(researchOutputRepository, outputVersionRepository,
                outputEvidenceRepository, authorizationService, auditRecorder, CLOCK);
        auth = new AuthContext(UUID.fromString("99998888-7777-6666-5555-444433332222"),
                null, "E1001", Set.of("output:submit"), Set.of(), NOW);
        lenient().when(authorizationService.check(any(), any(), any()))
                .thenReturn(AuthDecision.allow());
    }

    private ResearchOutputEntity draft() {
        ResearchOutputEntity output = ResearchOutputEntity.newDraft(
                ResearchOutputEntity.TYPE_PAPER, "复杂工况下的多模态感知方法", null, null,
                OWNER_ID, DEPT_ID, LocalDate.of(2026, 10, 15));
        output.setId(OUTPUT_ID);
        output.setCurrentVersionId(VERSION_ID);
        output.setVersion(1);
        return output;
    }

    private OutputVersionEntity version() {
        OutputVersionEntity version = OutputVersionEntity.create(OUTPUT_ID, 1, "{}", HASH,
                OWNER_ID, OffsetDateTime.now(CLOCK));
        version.setId(VERSION_ID);
        return version;
    }

    @Test
    void submitWithoutEvidenceReturnsOut002AndKeepsDraft() {
        ResearchOutputEntity output = draft();
        when(researchOutputRepository.findById(OUTPUT_ID)).thenReturn(Optional.of(output));
        when(outputVersionRepository.findById(VERSION_ID)).thenReturn(Optional.of(version()));
        when(outputEvidenceRepository.countByOutputVersionId(VERSION_ID)).thenReturn(0L);

        assertThatThrownBy(() -> service.submit(OUTPUT_ID, auth))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.OUT_002);

        assertThat(output.getStatus()).isEqualTo(ResearchOutputEntity.STATUS_DRAFT);
        verify(researchOutputRepository, never()).update(any());
        verify(auditRecorder, never()).record(any());
    }

    @Test
    void submitWithEvidenceMarksSubmittedAndPinsCurrentVersion() {
        ResearchOutputEntity output = draft();
        when(researchOutputRepository.findById(OUTPUT_ID)).thenReturn(Optional.of(output));
        when(outputVersionRepository.findById(VERSION_ID)).thenReturn(Optional.of(version()));
        when(outputEvidenceRepository.countByOutputVersionId(VERSION_ID)).thenReturn(1L);
        when(researchOutputRepository.update(output)).thenReturn(1);

        OutputSubmission submission = service.submit(OUTPUT_ID, auth);

        assertThat(submission.status()).isEqualTo(ResearchOutputEntity.STATUS_SUBMITTED);
        assertThat(submission.versionId()).isEqualTo(VERSION_ID);
        assertThat(submission.versionNo()).isEqualTo(1);
        assertThat(output.getStatus()).isEqualTo(ResearchOutputEntity.STATUS_SUBMITTED);
        assertThat(output.getCurrentVersionId()).isEqualTo(VERSION_ID);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditRecorder).record(captor.capture());
        assertThat(captor.getValue().action()).isEqualTo("OUTPUT_SUBMITTED");
        assertThat(captor.getValue().afterDigest()).isEqualTo(HASH);
    }

    @Test
    void submitRejectsNonDraftOutputWithOut004() {
        ResearchOutputEntity output = draft();
        output.setStatus(ResearchOutputEntity.STATUS_SUBMITTED);
        when(researchOutputRepository.findById(OUTPUT_ID)).thenReturn(Optional.of(output));

        assertThatThrownBy(() -> service.submit(OUTPUT_ID, auth))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.OUT_004);
    }

    @Test
    void submitRejectsUnknownOutputWithCommon404() {
        when(researchOutputRepository.findById(OUTPUT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submit(OUTPUT_ID, auth))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.COMMON_404);
    }
}
