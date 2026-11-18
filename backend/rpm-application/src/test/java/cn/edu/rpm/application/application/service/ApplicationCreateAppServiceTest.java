package cn.edu.rpm.application.application.service;

import cn.edu.rpm.application.application.dto.ApplicationCreated;
import cn.edu.rpm.application.application.dto.CreateApplicationCmd;
import cn.edu.rpm.application.domain.model.ApplicationPermissions;
import cn.edu.rpm.application.infrastructure.entity.ApplicationEntity;
import cn.edu.rpm.application.infrastructure.entity.ApplyBatchEntity;
import cn.edu.rpm.application.infrastructure.entity.ApplyFormTemplateEntity;
import cn.edu.rpm.application.infrastructure.repository.ApplicationRepository;
import cn.edu.rpm.application.infrastructure.repository.ApplyBatchRepository;
import cn.edu.rpm.application.infrastructure.repository.ApplyFormTemplateRepository;
import cn.edu.rpm.audit.domain.model.AuditEvent;
import cn.edu.rpm.audit.domain.service.AuditRecorder;
import cn.edu.rpm.common.domain.service.NumberGenerator;
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
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
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
 * {@link ApplicationCreateAppService} 纯单元测试（T-APP-010 先行测试）。
 *
 * <p>逐条覆盖 TC-APP-001（开放批次创建草稿）与 TC-APP-002（批次关闭阻断），
 * 并补充批次不存在、模板缺失、授权拒绝与标题边界。断言具体业务值：状态、
 * 版本号、编号、申请人、来源类型与审计动作。</p>
 */
@ExtendWith(MockitoExtension.class)
class ApplicationCreateAppServiceTest {

    private static final String BATCH_ID = "aaaabbbbccccddddeeeeffff00001111";
    private static final String DEPT_ID = "dddddddddddddddddddddddddddddddd";
    private static final UUID APPLICANT_ID = UUID.fromString("11112222-3333-4444-5555-666677778888");

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-11-18T02:00:00Z"), ZoneId.of("Asia/Shanghai"));

    @Mock
    private ApplyBatchRepository batchRepository;

    @Mock
    private ApplyFormTemplateRepository templateRepository;

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private NumberGenerator numberGenerator;

    @Mock
    private AuthorizationService authorizationService;

    @Mock
    private AuditRecorder auditRecorder;

    private ApplicationCreateAppService service;

    private AuthContext auth;

    @BeforeEach
    void setUp() {
        service = new ApplicationCreateAppService(batchRepository, templateRepository,
                applicationRepository, numberGenerator, authorizationService, auditRecorder, CLOCK);
        auth = new AuthContext(APPLICANT_ID, null, "E1001",
                Set.of(ApplicationPermissions.CREATE), Set.of(), Instant.parse("2026-11-18T02:00:00Z"));
    }

    @Test
    void tcApp001CreatesDraftForOpenBatch() {
        when(batchRepository.findById(BATCH_ID)).thenReturn(Optional.of(batch(ApplyBatchEntity.STATUS_OPEN)));
        when(templateRepository.findEffectiveByProjectType("YEAR_PLAN")).thenReturn(Optional.of(template()));
        when(authorizationService.check(any(), any(), any())).thenReturn(AuthDecision.allow());
        when(numberGenerator.next(NumberGenerator.RULE_APPLICATION_NO)).thenReturn("APP-2026-000123");

        ApplicationCreated result = service.create(new CreateApplicationCmd(
                BATCH_ID, "YEAR_PLAN", "  面向复杂工况的智能感知方法研究  ", DEPT_ID), auth);

        assertThat(result.applicationNo()).isEqualTo("APP-2026-000123");
        assertThat(result.status()).isEqualTo(ApplicationEntity.STATUS_DRAFT);
        assertThat(result.version()).isZero();
        assertThat(result.applicationId()).isNotBlank();

        ArgumentCaptor<ApplicationEntity> entityCaptor = ArgumentCaptor.forClass(ApplicationEntity.class);
        verify(applicationRepository).insert(entityCaptor.capture());
        ApplicationEntity saved = entityCaptor.getValue();
        assertThat(saved.getId()).isEqualTo(result.applicationId());
        assertThat(saved.getApplicationNo()).isEqualTo("APP-2026-000123");
        assertThat(saved.getBatchId()).isEqualTo(BATCH_ID);
        assertThat(saved.getProjectType()).isEqualTo("YEAR_PLAN");
        assertThat(saved.getApplicantId()).isEqualTo(APPLICANT_ID.toString().replace("-", ""));
        assertThat(saved.getDeptId()).isEqualTo(DEPT_ID);
        assertThat(saved.getTitle()).isEqualTo("面向复杂工况的智能感知方法研究");
        assertThat(saved.getSourceType()).isEqualTo(ApplicationEntity.SOURCE_INTERNAL);
        assertThat(saved.getStatus()).isEqualTo(ApplicationEntity.STATUS_DRAFT);
        assertThat(saved.getVersion()).isZero();
        assertThat(saved.getCreatedBy()).isEqualTo(APPLICANT_ID.toString().replace("-", ""));

        ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditRecorder).record(auditCaptor.capture());
        AuditEvent event = auditCaptor.getValue();
        assertThat(event.action()).isEqualTo("APPLICATION_CREATED");
        assertThat(event.objectType()).isEqualTo("APPLICATION");
        assertThat(event.objectId().toString().replace("-", "")).isEqualTo(result.applicationId());
        assertThat(event.actorId()).isEqualTo(APPLICANT_ID);
    }

    @Test
    void tcApp002RejectsClosedBatchWithoutPersisting() {
        when(batchRepository.findById(BATCH_ID)).thenReturn(Optional.of(batch(ApplyBatchEntity.STATUS_CLOSED)));

        assertThatThrownBy(() -> service.create(
                new CreateApplicationCmd(BATCH_ID, "YEAR_PLAN", "标题", DEPT_ID), auth))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.APPLY_003));

        verify(applicationRepository, never()).insert(any());
        verifyNoInteractions(numberGenerator, auditRecorder);
    }

    @Test
    void rejectsMissingBatchAsNotFound() {
        when(batchRepository.findById(BATCH_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(
                new CreateApplicationCmd(BATCH_ID, "YEAR_PLAN", "标题", DEPT_ID), auth))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.COMMON_404));

        verifyNoInteractions(applicationRepository, numberGenerator, auditRecorder);
    }

    @Test
    void rejectsProjectTypeWithoutEffectiveTemplate() {
        when(batchRepository.findById(BATCH_ID)).thenReturn(Optional.of(batch(ApplyBatchEntity.STATUS_OPEN)));
        when(templateRepository.findEffectiveByProjectType("YEAR_PLAN")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(
                new CreateApplicationCmd(BATCH_ID, "YEAR_PLAN", "标题", DEPT_ID), auth))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.APPLY_001));

        verifyNoInteractions(applicationRepository, numberGenerator, auditRecorder);
    }

    @Test
    void rejectsWhenAuthorizationDenied() {
        when(batchRepository.findById(BATCH_ID)).thenReturn(Optional.of(batch(ApplyBatchEntity.STATUS_OPEN)));
        when(templateRepository.findEffectiveByProjectType("YEAR_PLAN")).thenReturn(Optional.of(template()));
        when(authorizationService.check(any(), any(), any()))
                .thenReturn(AuthDecision.deny(ErrorCode.COMMON_403));

        assertThatThrownBy(() -> service.create(
                new CreateApplicationCmd(BATCH_ID, "YEAR_PLAN", "标题", DEPT_ID), auth))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.COMMON_403));

        verifyNoInteractions(applicationRepository, numberGenerator, auditRecorder);
    }

    @Test
    void rejectsBlankTitleAfterTrim() {
        assertThatThrownBy(() -> service.create(
                new CreateApplicationCmd(BATCH_ID, "YEAR_PLAN", "   ", DEPT_ID), auth))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.APPLY_001));
        verifyNoInteractions(batchRepository, applicationRepository);
    }

    @Test
    void rejectsTitleLongerThan512Characters() {
        String title = "标".repeat(513);

        assertThatThrownBy(() -> service.create(
                new CreateApplicationCmd(BATCH_ID, "YEAR_PLAN", title, DEPT_ID), auth))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.APPLY_001));
    }

    private static ApplyBatchEntity batch(String status) {
        ApplyBatchEntity batch = new ApplyBatchEntity();
        batch.setId(BATCH_ID);
        batch.setBatchNo("BATCH-2026");
        batch.setYear(2026);
        batch.setStatus(status);
        batch.setStartAt(OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.ofHours(8)));
        batch.setEndAt(OffsetDateTime.of(2026, 12, 31, 23, 59, 59, 0, ZoneOffset.ofHours(8)));
        return batch;
    }

    private static ApplyFormTemplateEntity template() {
        ApplyFormTemplateEntity template = new ApplyFormTemplateEntity();
        template.setId(UUID.randomUUID().toString().replace("-", ""));
        template.setProjectType("YEAR_PLAN");
        template.setVersionNo(1);
        template.setStatus(ApplyFormTemplateEntity.STATUS_PUBLISHED);
        template.setFormSchema("{\"required\":[\"title\"]}");
        return template;
    }
}
