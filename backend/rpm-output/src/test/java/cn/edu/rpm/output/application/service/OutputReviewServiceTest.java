package cn.edu.rpm.output.application.service;

import cn.edu.rpm.audit.domain.service.AuditRecorder;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.iam.application.dto.AuthContext;
import cn.edu.rpm.iam.application.dto.AuthDecision;
import cn.edu.rpm.iam.domain.service.AuthorizationService;
import cn.edu.rpm.output.application.dto.OutputReviewResult;
import cn.edu.rpm.output.application.dto.ReviewOutputCmd;
import cn.edu.rpm.output.infrastructure.entity.OutputReviewEntity;
import cn.edu.rpm.output.infrastructure.entity.OutputVersionEntity;
import cn.edu.rpm.output.infrastructure.entity.ResearchOutputEntity;
import cn.edu.rpm.output.infrastructure.repository.OutputReviewRepository;
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
 * {@link OutputReviewService} 纯单元测试（T-OUT-011 先行测试）。
 *
 * <p>覆盖 TC-OUT-009（审核通过绑定固定版本）、TC-OUT-010（退回无意见 → 400，
 * 状态不变）、职责分离（完成人不得审核本人成果）、非提交状态 → RPM-OUT-004、
 * 审核版本不属于该成果 → RPM-COMMON-400。</p>
 */
@ExtendWith(MockitoExtension.class)
class OutputReviewServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-18T02:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneId.of("Asia/Shanghai"));

    private static final String OUTPUT_ID = "11112222333344445555666677778888";
    private static final String OWNER_ID = "99998888777766665555444433332222";
    private static final String REVIEWER_ID = "aaaabbbbccccddddeeeeffff00001111";
    private static final String DEPT_ID = "bbbbccccddddeeeeffff000011112222";
    private static final String VERSION_ID = "ccccddddeeeeffff0000111122223333";
    private static final String HASH = "a".repeat(64);

    @Mock
    private ResearchOutputRepository researchOutputRepository;

    @Mock
    private OutputVersionRepository outputVersionRepository;

    @Mock
    private OutputReviewRepository outputReviewRepository;

    @Mock
    private AuthorizationService authorizationService;

    @Mock
    private AuditRecorder auditRecorder;

    private OutputReviewService service;

    private AuthContext reviewerAuth;

    @BeforeEach
    void setUp() {
        service = new OutputReviewService(researchOutputRepository, outputVersionRepository,
                outputReviewRepository, authorizationService, auditRecorder, CLOCK);
        reviewerAuth = new AuthContext(UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeffff0000"),
                null, "E2001", Set.of("output:review"), Set.of(), NOW);
        lenient().when(authorizationService.check(any(), any(), any()))
                .thenReturn(AuthDecision.allow());
    }

    private ResearchOutputEntity submitted() {
        ResearchOutputEntity output = ResearchOutputEntity.newDraft(
                ResearchOutputEntity.TYPE_PAPER, "复杂工况下的多模态感知方法", null, null,
                OWNER_ID, DEPT_ID, LocalDate.of(2026, 10, 1));
        output.setId(OUTPUT_ID);
        output.setCurrentVersionId(VERSION_ID);
        output.setVersion(1);
        output.submit(VERSION_ID);
        return output;
    }

    private OutputVersionEntity version() {
        OutputVersionEntity version = OutputVersionEntity.create(OUTPUT_ID, 1, "{}", HASH,
                OWNER_ID, OffsetDateTime.now(CLOCK));
        version.setId(VERSION_ID);
        return version;
    }

    @Test
    void approveMarksOutputApprovedAndBindsVersion() {
        ResearchOutputEntity output = submitted();
        when(researchOutputRepository.findById(OUTPUT_ID)).thenReturn(Optional.of(output));
        when(outputVersionRepository.findById(VERSION_ID)).thenReturn(Optional.of(version()));
        when(researchOutputRepository.update(output)).thenReturn(1);

        OutputReviewResult result = service.review(OUTPUT_ID,
                new ReviewOutputCmd(VERSION_ID, "APPROVED", "材料齐全"), reviewerAuth);

        assertThat(result.status()).isEqualTo(ResearchOutputEntity.STATUS_APPROVED);
        assertThat(result.versionId()).isEqualTo(VERSION_ID);
        assertThat(result.decision()).isEqualTo(OutputReviewEntity.DECISION_APPROVED);
        assertThat(output.getStatus()).isEqualTo(ResearchOutputEntity.STATUS_APPROVED);
        assertThat(output.getCurrentVersionId()).isEqualTo(VERSION_ID);

        ArgumentCaptor<OutputReviewEntity> captor = ArgumentCaptor.forClass(OutputReviewEntity.class);
        verify(outputReviewRepository).insert(captor.capture());
        assertThat(captor.getValue().getReviewerId())
                .isEqualTo("aaaaaaaabbbbccccddddeeeeffff0000");
        assertThat(captor.getValue().getOutputVersionId()).isEqualTo(VERSION_ID);
    }

    @Test
    void returnWithoutCommentIsRejectedAndStateUnchanged() {
        ResearchOutputEntity output = submitted();
        when(researchOutputRepository.findById(OUTPUT_ID)).thenReturn(Optional.of(output));
        when(outputVersionRepository.findById(VERSION_ID)).thenReturn(Optional.of(version()));

        assertThatThrownBy(() -> service.review(OUTPUT_ID,
                new ReviewOutputCmd(VERSION_ID, "RETURNED", "  "), reviewerAuth))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.COMMON_400);

        assertThat(output.getStatus()).isEqualTo(ResearchOutputEntity.STATUS_SUBMITTED);
        verify(outputReviewRepository, never()).insert(any());
        verify(researchOutputRepository, never()).update(any());
    }

    @Test
    void returnWithCommentMarksReturnedAndKeepsVersion() {
        ResearchOutputEntity output = submitted();
        when(researchOutputRepository.findById(OUTPUT_ID)).thenReturn(Optional.of(output));
        when(outputVersionRepository.findById(VERSION_ID)).thenReturn(Optional.of(version()));
        when(researchOutputRepository.update(output)).thenReturn(1);

        OutputReviewResult result = service.review(OUTPUT_ID,
                new ReviewOutputCmd(VERSION_ID, "RETURNED", "请补充检索证明"), reviewerAuth);

        assertThat(result.status()).isEqualTo(ResearchOutputEntity.STATUS_RETURNED);
        assertThat(output.getCurrentVersionId()).isEqualTo(VERSION_ID);
    }

    @Test
    void ownerCannotReviewOwnOutput() {
        ResearchOutputEntity output = submitted();
        when(researchOutputRepository.findById(OUTPUT_ID)).thenReturn(Optional.of(output));
        AuthContext ownerAuth = new AuthContext(
                UUID.fromString("99998888-7777-6666-5555-444433332222"),
                null, "E1001", Set.of("output:review"), Set.of(), NOW);

        assertThatThrownBy(() -> service.review(OUTPUT_ID,
                new ReviewOutputCmd(VERSION_ID, "APPROVED", null), ownerAuth))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.COMMON_403);
        verify(outputReviewRepository, never()).insert(any());
    }

    @Test
    void reviewRejectsNonSubmittedOutputWithOut004() {
        ResearchOutputEntity output = submitted();
        output.setStatus(ResearchOutputEntity.STATUS_DRAFT);
        when(researchOutputRepository.findById(OUTPUT_ID)).thenReturn(Optional.of(output));

        assertThatThrownBy(() -> service.review(OUTPUT_ID,
                new ReviewOutputCmd(VERSION_ID, "APPROVED", null), reviewerAuth))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.OUT_004);
    }

    @Test
    void reviewRejectsVersionOfAnotherOutputWithCommon400() {
        ResearchOutputEntity output = submitted();
        OutputVersionEntity foreign = version();
        foreign.setOutputId("ffffffffffffffffffffffffffffffff");
        when(researchOutputRepository.findById(OUTPUT_ID)).thenReturn(Optional.of(output));
        when(outputVersionRepository.findById(VERSION_ID)).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.review(OUTPUT_ID,
                new ReviewOutputCmd(VERSION_ID, "APPROVED", null), reviewerAuth))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.COMMON_400);
    }

    @Test
    void reviewRejectsUnknownDecisionWithCommon400() {
        ResearchOutputEntity output = submitted();
        when(researchOutputRepository.findById(OUTPUT_ID)).thenReturn(Optional.of(output));
        when(outputVersionRepository.findById(VERSION_ID)).thenReturn(Optional.of(version()));

        assertThatThrownBy(() -> service.review(OUTPUT_ID,
                new ReviewOutputCmd(VERSION_ID, "MAYBE", "x"), reviewerAuth))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.COMMON_400);
    }
}
