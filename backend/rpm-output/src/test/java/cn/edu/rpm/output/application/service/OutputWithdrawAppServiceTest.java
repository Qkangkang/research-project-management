package cn.edu.rpm.output.application.service;

import cn.edu.rpm.audit.domain.model.AuditEvent;
import cn.edu.rpm.audit.domain.service.AuditRecorder;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.iam.application.dto.AuthContext;
import cn.edu.rpm.iam.application.dto.AuthDecision;
import cn.edu.rpm.iam.domain.service.AuthorizationService;
import cn.edu.rpm.output.application.dto.OutputWithdrawResult;
import cn.edu.rpm.output.application.dto.WithdrawOutputCmd;
import cn.edu.rpm.output.application.dto.WithdrawReviewCmd;
import cn.edu.rpm.output.infrastructure.entity.ResearchOutputEntity;
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
 * {@link OutputWithdrawAppService} 纯单元测试（T-OUT-013 先行测试）。
 *
 * <p>覆盖 TC-OUT-101（撤回无理由 → 400）、TC-OUT-102（审核通过后状态
 * {@code REVOKED}，默认列表不可见由查询层状态过滤保证）、TC-OUT-103
 * （撤回不修改版本/证据/关联，快照引用不受影响）；另验证非所有者 403、
 * 本人不得审核本人撤回、重复撤回 {@code RPM-OUT-004}。</p>
 */
@ExtendWith(MockitoExtension.class)
class OutputWithdrawAppServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-18T02:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneId.of("Asia/Shanghai"));

    private static final String OUTPUT_ID = "11112222333344445555666677778888";
    private static final String OWNER_ID = "99998888777766665555444433332222";
    private static final String REVIEWER_ID = "aaaaaaaa-bbbb-cccc-dddd-eeeeffff0000";
    private static final String DEPT_ID = "bbbbccccddddeeeeffff000011112222";
    private static final String VERSION_ID = "ccccddddeeeeffff0000111122223333";

    @Mock
    private ResearchOutputRepository researchOutputRepository;

    @Mock
    private AuthorizationService authorizationService;

    @Mock
    private AuditRecorder auditRecorder;

    private OutputWithdrawAppService service;

    private AuthContext ownerAuth;

    private AuthContext reviewerAuth;

    @BeforeEach
    void setUp() {
        service = new OutputWithdrawAppService(researchOutputRepository, authorizationService,
                auditRecorder, CLOCK);
        ownerAuth = new AuthContext(UUID.fromString("99998888-7777-6666-5555-444433332222"),
                null, "E1001", Set.of("output:edit"), Set.of(), NOW);
        reviewerAuth = new AuthContext(UUID.fromString(REVIEWER_ID),
                null, "E2001", Set.of("output:review"), Set.of(), NOW);
        lenient().when(authorizationService.check(any(), any(), any()))
                .thenReturn(AuthDecision.allow());
    }

    private ResearchOutputEntity approved() {
        ResearchOutputEntity output = ResearchOutputEntity.newDraft(
                ResearchOutputEntity.TYPE_PAPER, "复杂工况下的多模态感知方法", null, null,
                OWNER_ID, DEPT_ID, LocalDate.of(2026, 10, 1));
        output.setId(OUTPUT_ID);
        output.setCurrentVersionId(VERSION_ID);
        output.setStatus(ResearchOutputEntity.STATUS_APPROVED);
        output.setVersion(1);
        return output;
    }

    @Test
    void withdrawWithoutReasonReturnsCommon400() {
        assertThatThrownBy(() -> service.withdraw(OUTPUT_ID, new WithdrawOutputCmd("  "), ownerAuth))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.COMMON_400);
        verify(researchOutputRepository, never()).findById(any());
    }

    @Test
    void withdrawRequestAuditsReasonAndKeepsStatus() {
        ResearchOutputEntity output = approved();
        when(researchOutputRepository.findById(OUTPUT_ID)).thenReturn(Optional.of(output));

        OutputWithdrawResult result = service.withdraw(OUTPUT_ID,
                new WithdrawOutputCmd("论文重复登记"), ownerAuth);

        assertThat(result.status()).isEqualTo(ResearchOutputEntity.STATUS_APPROVED);
        assertThat(result.withdrawReview()).isTrue();
        assertThat(output.getStatus()).isEqualTo(ResearchOutputEntity.STATUS_APPROVED);
        verify(researchOutputRepository, never()).update(any());

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditRecorder).record(captor.capture());
        assertThat(captor.getValue().action()).isEqualTo("OUTPUT_WITHDRAW_REQUESTED");
        assertThat(captor.getValue().reason()).contains("论文重复登记");
    }

    @Test
    void withdrawRejectsNonOwnerWithCommon403() {
        ResearchOutputEntity output = approved();
        when(researchOutputRepository.findById(OUTPUT_ID)).thenReturn(Optional.of(output));

        assertThatThrownBy(() -> service.withdraw(OUTPUT_ID,
                new WithdrawOutputCmd("非本人申请"), reviewerAuth))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.COMMON_403);
    }

    @Test
    void reviewApprovedMovesOutputToRevokedWithoutTouchingVersion() {
        ResearchOutputEntity output = approved();
        when(researchOutputRepository.findById(OUTPUT_ID)).thenReturn(Optional.of(output));
        when(researchOutputRepository.update(output)).thenReturn(1);

        OutputWithdrawResult result = service.reviewWithdraw(OUTPUT_ID,
                new WithdrawReviewCmd(true, "同意撤回"), reviewerAuth);

        assertThat(result.status()).isEqualTo(ResearchOutputEntity.STATUS_REVOKED);
        assertThat(result.withdrawReview()).isFalse();
        assertThat(output.getStatus()).isEqualTo(ResearchOutputEntity.STATUS_REVOKED);
        assertThat(output.getCurrentVersionId()).isEqualTo(VERSION_ID);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditRecorder).record(captor.capture());
        assertThat(captor.getValue().action()).isEqualTo("OUTPUT_WITHDRAWN");
    }

    @Test
    void reviewRejectedKeepsStatusAndDoesNotUpdate() {
        ResearchOutputEntity output = approved();
        when(researchOutputRepository.findById(OUTPUT_ID)).thenReturn(Optional.of(output));

        OutputWithdrawResult result = service.reviewWithdraw(OUTPUT_ID,
                new WithdrawReviewCmd(false, "证据充分，不同意撤回"), reviewerAuth);

        assertThat(result.status()).isEqualTo(ResearchOutputEntity.STATUS_APPROVED);
        verify(researchOutputRepository, never()).update(any());
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditRecorder).record(captor.capture());
        assertThat(captor.getValue().action()).isEqualTo("OUTPUT_WITHDRAW_REJECTED");
    }

    @Test
    void reviewRejectsSelfReviewWithCommon403() {
        ResearchOutputEntity output = approved();
        when(researchOutputRepository.findById(OUTPUT_ID)).thenReturn(Optional.of(output));

        assertThatThrownBy(() -> service.reviewWithdraw(OUTPUT_ID,
                new WithdrawReviewCmd(true, null), ownerAuth))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.COMMON_403);
        verify(researchOutputRepository, never()).update(any());
    }

    @Test
    void reviewRejectsAlreadyRevokedOutputWithOut004() {
        ResearchOutputEntity output = approved();
        output.setStatus(ResearchOutputEntity.STATUS_REVOKED);
        when(researchOutputRepository.findById(OUTPUT_ID)).thenReturn(Optional.of(output));

        assertThatThrownBy(() -> service.reviewWithdraw(OUTPUT_ID,
                new WithdrawReviewCmd(true, null), reviewerAuth))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.OUT_004);
    }
}
