package cn.edu.rpm.application.application.service;

import cn.edu.rpm.application.application.dto.ApplicationDraftSaved;
import cn.edu.rpm.application.application.dto.SaveDraftCmd;
import cn.edu.rpm.application.domain.model.ApplicationPermissions;
import cn.edu.rpm.application.domain.service.ApplicationContentHasher;
import cn.edu.rpm.application.infrastructure.entity.ApplicationEntity;
import cn.edu.rpm.application.infrastructure.entity.ApplicationVersionEntity;
import cn.edu.rpm.application.infrastructure.repository.ApplicationRepository;
import cn.edu.rpm.application.infrastructure.repository.ApplicationVersionRepository;
import cn.edu.rpm.audit.domain.model.AuditEvent;
import cn.edu.rpm.audit.domain.service.AuditRecorder;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.iam.application.dto.AuthContext;
import cn.edu.rpm.iam.application.dto.AuthDecision;
import cn.edu.rpm.iam.domain.service.AuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link ApplicationDraftAppService} 纯单元测试（TC-APP-003 先行测试）。
 *
 * <p>主用例验证 {@code expected_version=3} 而数据库 {@code version=4} 时返回
 * {@code RPM-APPLY-002} 且不覆盖草稿；另覆盖草稿行新建/原地更新、状态与授权拒绝。</p>
 */
@ExtendWith(MockitoExtension.class)
class ApplicationDraftAppServiceTest {

    private static final String APP_ID = "aaaabbbbccccddddeeeeffff00001111";
    private static final String DEPT_ID = "dddddddddddddddddddddddddddddddd";
    private static final UUID APPLICANT_ID = UUID.fromString("11112222-3333-4444-5555-666677778888");

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-11-18T02:00:00Z"), ZoneId.of("Asia/Shanghai"));

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private ApplicationVersionRepository applicationVersionRepository;

    @Mock
    private AuthorizationService authorizationService;

    @Mock
    private AuditRecorder auditRecorder;

    private ApplicationDraftAppService service;

    private AuthContext auth;

    @BeforeEach
    void setUp() {
        service = new ApplicationDraftAppService(applicationRepository, applicationVersionRepository,
                new ApplicationContentHasher(), authorizationService, auditRecorder, CLOCK);
        auth = new AuthContext(APPLICANT_ID, null, "E1001",
                Set.of(ApplicationPermissions.EDIT), Set.of(), Instant.parse("2026-11-18T02:00:00Z"));
    }

    @Test
    void tcApp003ExpectedVersionMismatchDoesNotOverwriteDraft() {
        ApplicationEntity application = application(4);
        when(applicationRepository.selectForUpdate(APP_ID)).thenReturn(Optional.of(application));
        when(authorizationService.check(any(), any(), any())).thenReturn(AuthDecision.allow());

        SaveDraftCmd cmd = new SaveDraftCmd(3, "新的标题",
                Map.of("title", "新的标题", "budget", 200000));

        assertThatThrownBy(() -> service.save(APP_ID, cmd, auth))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.APPLY_002));

        verifyNoInteractions(applicationVersionRepository, auditRecorder);
        verify(applicationRepository, never()).update(any());
        assertThat(application.getTitle()).isEqualTo("原申报标题");
        assertThat(application.getVersion()).isEqualTo(4);
    }

    @Test
    void updatesExistingDraftVersionInPlace() {
        ApplicationEntity application = application(4);
        ApplicationVersionEntity draft = draft(1);
        when(applicationRepository.selectForUpdate(APP_ID)).thenReturn(Optional.of(application));
        when(authorizationService.check(any(), any(), any())).thenReturn(AuthDecision.allow());
        when(applicationVersionRepository.findDraftByApplicationId(APP_ID))
                .thenReturn(Optional.of(draft));
        when(applicationVersionRepository.update(draft)).thenReturn(1);
        when(applicationRepository.update(application)).thenReturn(1);

        ApplicationDraftSaved result = service.save(APP_ID,
                new SaveDraftCmd(4, "  修订后的标题  ", Map.of("title", "修订后的标题")), auth);

        assertThat(result.applicationId()).isEqualTo(APP_ID);
        assertThat(result.status()).isEqualTo(ApplicationEntity.STATUS_DRAFT);
        assertThat(result.version()).isEqualTo(5);
        assertThat(result.draftVersionId()).isEqualTo(draft.getId());
        assertThat(result.draftVersionNo()).isEqualTo(1);
        assertThat(application.getTitle()).isEqualTo("修订后的标题");
        assertThat(draft.getStatus()).isEqualTo(ApplicationVersionEntity.STATUS_DRAFT);
        assertThat(draft.getFormData()).isEqualTo("{\"title\":\"修订后的标题\"}");
        assertThat(draft.getContentHash()).hasSize(64).matches("[0-9a-f]{64}");
        verify(applicationVersionRepository, never()).insert(any());

        ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditRecorder).recordNonCritical(auditCaptor.capture());
        assertThat(auditCaptor.getValue().action()).isEqualTo("APPLICATION_DRAFT_SAVED");
        assertThat(auditCaptor.getValue().objectId())
                .isEqualTo(UUID.fromString("aaaabbbb-cccc-dddd-eeee-ffff00001111"));
    }

    @Test
    void createsDraftRowWhenNoDraftExists() {
        ApplicationEntity application = application(4);
        when(applicationRepository.selectForUpdate(APP_ID)).thenReturn(Optional.of(application));
        when(authorizationService.check(any(), any(), any())).thenReturn(AuthDecision.allow());
        when(applicationVersionRepository.findDraftByApplicationId(APP_ID))
                .thenReturn(Optional.empty());
        when(applicationVersionRepository.findMaxVersionNo(APP_ID)).thenReturn(2);
        when(applicationRepository.update(application)).thenReturn(1);

        ApplicationDraftSaved result = service.save(APP_ID,
                new SaveDraftCmd(4, "补正后的申报", Map.of("title", "补正后的申报")), auth);

        ArgumentCaptor<ApplicationVersionEntity> versionCaptor =
                ArgumentCaptor.forClass(ApplicationVersionEntity.class);
        verify(applicationVersionRepository).insert(versionCaptor.capture());
        ApplicationVersionEntity inserted = versionCaptor.getValue();
        assertThat(inserted.getApplicationId()).isEqualTo(APP_ID);
        assertThat(inserted.getVersionNo()).isEqualTo(3);
        assertThat(inserted.getStatus()).isEqualTo(ApplicationVersionEntity.STATUS_DRAFT);
        assertThat(result.draftVersionNo()).isEqualTo(3);
        assertThat(result.version()).isEqualTo(5);
    }

    @Test
    void rejectsSaveWhenStatusIsInReview() {
        ApplicationEntity application = application(4);
        application.setStatus(ApplicationEntity.STATUS_IN_REVIEW);
        when(applicationRepository.selectForUpdate(APP_ID)).thenReturn(Optional.of(application));
        when(authorizationService.check(any(), any(), any())).thenReturn(AuthDecision.allow());

        assertThatThrownBy(() -> service.save(APP_ID,
                new SaveDraftCmd(4, "标题", Map.of("title", "标题")), auth))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.APPLY_004));
        verifyNoInteractions(applicationVersionRepository);
    }

    @Test
    void rejectsSaveWhenAuthorizationDenied() {
        ApplicationEntity application = application(4);
        when(applicationRepository.selectForUpdate(APP_ID)).thenReturn(Optional.of(application));
        when(authorizationService.check(any(), any(), any()))
                .thenReturn(AuthDecision.deny(ErrorCode.COMMON_403));

        assertThatThrownBy(() -> service.save(APP_ID,
                new SaveDraftCmd(4, "标题", Map.of("title", "标题")), auth))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.COMMON_403));
        verifyNoInteractions(applicationVersionRepository);
    }

    private static ApplicationEntity application(int version) {
        ApplicationEntity application = new ApplicationEntity();
        application.setId(APP_ID);
        application.setApplicationNo("APP-2026-000123");
        application.setBatchId("aaaabbbbccccddddeeeeffff00002222");
        application.setProjectType("YEAR_PLAN");
        application.setApplicantId(APPLICANT_ID.toString().replace("-", ""));
        application.setDeptId(DEPT_ID);
        application.setTitle("原申报标题");
        application.setSourceType(ApplicationEntity.SOURCE_INTERNAL);
        application.setStatus(ApplicationEntity.STATUS_DRAFT);
        application.setVersion(version);
        return application;
    }

    private static ApplicationVersionEntity draft(int versionNo) {
        ApplicationVersionEntity draft = new ApplicationVersionEntity();
        draft.setId("12341234123412341234123412341234");
        draft.setApplicationId(APP_ID);
        draft.setVersionNo(versionNo);
        draft.setFormData("{\"title\":\"原申报标题\"}");
        draft.setContentHash("9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08");
        draft.setStatus(ApplicationVersionEntity.STATUS_DRAFT);
        draft.setVersion(0);
        return draft;
    }
}
