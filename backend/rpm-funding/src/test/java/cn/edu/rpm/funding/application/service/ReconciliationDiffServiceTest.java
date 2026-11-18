package cn.edu.rpm.funding.application.service;

import cn.edu.rpm.audit.domain.service.AuditRecorder;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.funding.application.dto.CloseReconciliationDiffCmd;
import cn.edu.rpm.funding.application.dto.ReconciliationDiffView;
import cn.edu.rpm.funding.domain.model.FundingPermissions;
import cn.edu.rpm.funding.infrastructure.entity.ReconciliationBatchEntity;
import cn.edu.rpm.funding.infrastructure.entity.ReconciliationDiffEntity;
import cn.edu.rpm.funding.infrastructure.repository.ReconciliationBatchRepository;
import cn.edu.rpm.funding.infrastructure.repository.ReconciliationDiffRepository;
import cn.edu.rpm.iam.application.dto.AuthContext;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * {@link ReconciliationDiffService} 纯单元测试（T-FUND-030；TC-FUND-064～066）。
 */
@ExtendWith(MockitoExtension.class)
class ReconciliationDiffServiceTest {

    private static final String BATCH_ID = "99999999999999999999999999999999";
    private static final String DIFF_ID = "88888888888888888888888888888888";
    private static final UUID ACTOR_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final Instant NOW = Instant.parse("2026-11-18T02:00:00Z");

    @Mock
    private ReconciliationBatchRepository batchRepository;

    @Mock
    private ReconciliationDiffRepository diffRepository;

    @Mock
    private AuditRecorder auditRecorder;

    private ReconciliationDiffService service;

    private AuthContext auth;

    @BeforeEach
    void setUp() {
        service = new ReconciliationDiffService(batchRepository, diffRepository,
                auditRecorder, new ObjectMapper(), Clock.fixed(NOW, ZoneId.of("Asia/Shanghai")));
        auth = new AuthContext(ACTOR_ID, null, "EMP-001",
                Set.of(FundingPermissions.RECONCILE_CLOSE), Set.of(), NOW);
        ReconciliationBatchEntity batch = new ReconciliationBatchEntity();
        batch.setId(BATCH_ID);
        lenient().when(batchRepository.findById(BATCH_ID)).thenReturn(Optional.of(batch));
        lenient().when(diffRepository.update(any())).thenReturn(1);
    }

    @Test
    void tcFund064MissingResolutionThrows400AndKeepsOpen() {
        ReconciliationDiffEntity diff = openDiff();
        lenient().when(diffRepository.findById(DIFF_ID)).thenReturn(Optional.of(diff));

        assertThatThrownBy(() -> service.close(BATCH_ID, DIFF_ID,
                new CloseReconciliationDiffCmd(" ", "owner-1", List.of("file-1")), auth))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.COMMON_400);
        assertThat(diff.getStatus()).isEqualTo(ReconciliationDiffEntity.STATUS_OPEN);
        verify(diffRepository, never()).update(any());
    }

    @Test
    void missingEvidenceThrows400AndKeepsOpen() {
        ReconciliationDiffEntity diff = openDiff();
        lenient().when(diffRepository.findById(DIFF_ID)).thenReturn(Optional.of(diff));

        assertThatThrownBy(() -> service.close(BATCH_ID, DIFF_ID,
                new CloseReconciliationDiffCmd("已核对银行回单", "owner-1", List.of()), auth))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.COMMON_400);
        assertThat(diff.getStatus()).isEqualTo(ReconciliationDiffEntity.STATUS_OPEN);
    }

    @Test
    void tcFund065CompleteCloseWritesClosedByAndAt() {
        ReconciliationDiffEntity diff = openDiff();
        lenient().when(diffRepository.findById(DIFF_ID)).thenReturn(Optional.of(diff));

        ReconciliationDiffView view = service.close(BATCH_ID, DIFF_ID,
                new CloseReconciliationDiffCmd("已核对银行回单并调整台账",
                        "owner-1", List.of("file-1", "file-2")), auth);

        assertThat(view.status()).isEqualTo(ReconciliationDiffEntity.STATUS_CLOSED);
        assertThat(view.closedBy()).isNotBlank();
        assertThat(view.closedAt()).isNotNull();
        assertThat(view.ownerId()).isEqualTo("owner-1");
        assertThat(view.evidenceFileIds()).containsExactly("file-1", "file-2");
        assertThat(view.resolution()).isEqualTo("已核对银行回单并调整台账");
        verify(diffRepository).update(diff);
        verify(auditRecorder).record(any());
    }

    @Test
    void tcFund066ClosedDiffCannotBeClosedAgain() {
        ReconciliationDiffEntity diff = openDiff();
        diff.close("首次处置", "owner-1", java.time.OffsetDateTime.now());
        lenient().when(diffRepository.findById(DIFF_ID)).thenReturn(Optional.of(diff));

        assertThatThrownBy(() -> service.close(BATCH_ID, DIFF_ID,
                new CloseReconciliationDiffCmd("再次处置", "owner-1", List.of("file-1")), auth))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.COMMON_409);
        verify(diffRepository, never()).update(any());
    }

    private static ReconciliationDiffEntity openDiff() {
        ReconciliationDiffEntity diff = new ReconciliationDiffEntity();
        diff.setId(DIFF_ID);
        diff.setBatchId(BATCH_ID);
        diff.setDiffType(ReconciliationDiffEntity.TYPE_AMOUNT_MISMATCH);
        diff.setExternalPaymentNo("PAY-1");
        diff.setSystemAmount(new BigDecimal("70000.00"));
        diff.setFinanceAmount(new BigDecimal("80000.00"));
        diff.setStatus(ReconciliationDiffEntity.STATUS_OPEN);
        diff.setEvidenceFileIds("[]");
        diff.setVersion(1);
        return diff;
    }
}
