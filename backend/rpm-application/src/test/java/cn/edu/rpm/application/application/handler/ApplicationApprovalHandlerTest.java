package cn.edu.rpm.application.application.handler;

import cn.edu.rpm.application.infrastructure.entity.ApplicationEntity;
import cn.edu.rpm.application.infrastructure.entity.ApplicationReviewEntity;
import cn.edu.rpm.application.infrastructure.entity.ApplicationVersionEntity;
import cn.edu.rpm.application.infrastructure.repository.ApplicationRepository;
import cn.edu.rpm.application.infrastructure.repository.ApplicationReviewRepository;
import cn.edu.rpm.application.infrastructure.repository.ApplicationVersionRepository;
import cn.edu.rpm.audit.domain.model.AuditEvent;
import cn.edu.rpm.audit.domain.service.AuditRecorder;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.workflow.domain.dto.ApprovalResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ApplicationApprovalHandler} 纯单元测试（T-APP-021）。
 *
 * <p>覆盖 TC-APP-021（退回后 {@code SUPPLEMENT} 且保留原意见）、TC-APP-022
 * （拒绝后 {@code REJECTED}）、TC-APP-046（同一请求重复回调不重复写意见；
 * {@code APPROVED} 不生成项目）。</p>
 */
@ExtendWith(MockitoExtension.class)
class ApplicationApprovalHandlerTest {

    private static final String APP_ID = "aaaabbbbccccddddeeeeffff00001111";
    private static final UUID APP_UUID = UUID.fromString("aaaabbbb-cccc-dddd-eeee-ffff00001111");
    private static final String VERSION_ID = "11111111111111111111111111111111";
    private static final UUID VERSION_UUID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OPERATOR_ID = UUID.fromString("22222222-3333-4444-5555-666666666666");
    private static final UUID REQUEST_ID = UUID.fromString("abcdabcd-abcd-abcd-abcd-abcdabcdabcd");
    private static final Instant OCCURRED = Instant.parse("2026-11-18T02:00:00Z");
    private static final Clock CLOCK = Clock.fixed(OCCURRED, ZoneId.of("Asia/Shanghai"));

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private ApplicationVersionRepository applicationVersionRepository;

    @Mock
    private ApplicationReviewRepository applicationReviewRepository;

    @Mock
    private AuditRecorder auditRecorder;

    private ApplicationApprovalHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ApplicationApprovalHandler(applicationRepository, applicationVersionRepository,
                applicationReviewRepository, auditRecorder, CLOCK);
    }

    private ApplicationEntity application(String status) {
        ApplicationEntity application = new ApplicationEntity();
        application.setId(APP_ID);
        application.setApplicantId("99999999999999999999999999999999");
        application.setCurrentVersionId(VERSION_ID);
        application.setStatus(status);
        return application;
    }

    private ApprovalResult result(String action, String comment) {
        return new ApprovalResult(APP_UUID, "APPLICATION", APP_UUID, VERSION_UUID, action,
                OPERATOR_ID, null, comment, OffsetDateTime.now(CLOCK), REQUEST_ID, List.of());
    }

    @Test
    void tcApp021ReturnedMovesToSupplementAndWritesComment() {
        ApplicationEntity application = application(ApplicationEntity.STATUS_IN_REVIEW);
        when(applicationRepository.selectForUpdate(APP_ID)).thenReturn(Optional.of(application));
        when(applicationRepository.update(any())).thenReturn(1);

        handler.onReturned(result("RETURNED", "请补充预算说明"));

        assertThat(application.getStatus()).isEqualTo(ApplicationEntity.STATUS_SUPPLEMENT);
        ArgumentCaptor<ApplicationReviewEntity> captor =
                ArgumentCaptor.forClass(ApplicationReviewEntity.class);
        verify(applicationReviewRepository).insert(captor.capture());
        assertThat(captor.getValue().getDecision()).isEqualTo(ApplicationReviewEntity.DECISION_RETURN);
        assertThat(captor.getValue().getComment()).isEqualTo("请补充预算说明");
        verify(auditRecorder).record(any(AuditEvent.class));
    }

    @Test
    void tcApp022RejectedMovesToRejectedAndVersionRejected() {
        ApplicationEntity application = application(ApplicationEntity.STATUS_IN_REVIEW);
        ApplicationVersionEntity version = new ApplicationVersionEntity();
        version.setId(VERSION_ID);
        version.setApplicationId(APP_ID);
        version.setStatus(ApplicationVersionEntity.STATUS_SUBMITTED);
        when(applicationRepository.selectForUpdate(APP_ID)).thenReturn(Optional.of(application));
        when(applicationRepository.update(any())).thenReturn(1);
        when(applicationVersionRepository.findById(VERSION_ID)).thenReturn(Optional.of(version));
        when(applicationVersionRepository.update(any())).thenReturn(1);

        handler.onRejected(result("REJECTED", "不符合指南"));

        assertThat(application.getStatus()).isEqualTo(ApplicationEntity.STATUS_REJECTED);
        assertThat(version.getStatus()).isEqualTo(ApplicationVersionEntity.STATUS_REJECTED);
    }

    @Test
    void tcApp046RepeatedCallbackDoesNotDuplicateReviewAndApprovedDoesNotCreateProject() {
        ApplicationEntity application = application(ApplicationEntity.STATUS_IN_REVIEW);
        when(applicationRepository.selectForUpdate(APP_ID)).thenReturn(Optional.of(application));
        when(applicationRepository.update(any())).thenReturn(1);

        ApprovalResult returned = result("RETURNED", "请补充预算说明");
        handler.onReturned(returned);
        handler.onReturned(returned);

        verify(applicationReviewRepository).insert(any(ApplicationReviewEntity.class));
        verify(applicationRepository).update(any());

        handler.onApproved(result("APPROVED", "节点通过"));
        assertThat(application.getStatus()).isEqualTo(ApplicationEntity.STATUS_SUPPLEMENT);
        verify(applicationReviewRepository, times(1)).insert(any(ApplicationReviewEntity.class));
    }

    @Test
    void staleVersionThrowsWf005() {
        ApplicationEntity application = application(ApplicationEntity.STATUS_IN_REVIEW);
        application.setCurrentVersionId("ffffffffffffffffffffffffffffffff");
        when(applicationRepository.selectForUpdate(APP_ID)).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> handler.onReturned(result("RETURNED", "退回")))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.WF_005));

        verify(applicationRepository, never()).update(any());
        verify(auditRecorder).recordNonCritical(any(AuditEvent.class));
    }
}
