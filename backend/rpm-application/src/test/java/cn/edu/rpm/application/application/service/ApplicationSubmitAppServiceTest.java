package cn.edu.rpm.application.application.service;

import cn.edu.rpm.application.application.dto.ApplicationSubmission;
import cn.edu.rpm.application.application.dto.SubmitApplicationCmd;
import cn.edu.rpm.application.domain.model.ApplicationPermissions;
import cn.edu.rpm.application.domain.model.FormFieldRule;
import cn.edu.rpm.application.domain.model.ResolvedFormTemplate;
import cn.edu.rpm.application.domain.service.ApplyFormTemplateResolver;
import cn.edu.rpm.application.domain.service.ApplicationContentHasher;
import cn.edu.rpm.application.domain.port.ApplicationWorkflowLauncher;
import cn.edu.rpm.application.domain.port.ApplicationWorkflowStart;
import cn.edu.rpm.application.domain.port.WorkflowLaunch;
import cn.edu.rpm.application.infrastructure.entity.ApplicationEntity;
import cn.edu.rpm.application.infrastructure.entity.ApplicationMemberEntity;
import cn.edu.rpm.application.infrastructure.entity.ApplicationVersionEntity;
import cn.edu.rpm.application.infrastructure.repository.ApplicationMemberRepository;
import cn.edu.rpm.application.infrastructure.repository.ApplicationRepository;
import cn.edu.rpm.application.infrastructure.repository.ApplicationVersionRepository;
import cn.edu.rpm.audit.domain.model.AuditEvent;
import cn.edu.rpm.audit.domain.service.AuditRecorder;
import cn.edu.rpm.common.domain.model.OutboxEvent;
import cn.edu.rpm.common.domain.port.OutboxAppendPort;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
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

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link ApplicationSubmitAppService} 纯单元测试（T-APP-012 先行测试）。
 *
 * <p>覆盖 TC-APP-004（提交成功）、TC-APP-005（版本冲突）、TC-APP-006（必填附件缺失）、
 * TC-APP-007（协作人仅查看权限）、TC-APP-008（幂等键重复返回首次结果）、
 * TC-APP-009（两线程并发最多 1 个成功），并补充 TC-APP-012 的
 * {@code SUPPLEMENT} 补正提交版本号递增断言。断言落在具体版本行、审批实例、
 * outbox 事件与审计动作上，不做空泛验证。</p>
 *
 * <p>并发用例使用内存 Fake 仓储模拟 {@code SELECT ... FOR UPDATE} 的串行化语义
 * （信号量持有至事务更新完成），其余协作者使用 Mockito 替身。</p>
 */
@ExtendWith(MockitoExtension.class)
class ApplicationSubmitAppServiceTest {

    private static final String APP_ID = "aaaabbbbccccddddeeeeffff00001111";
    private static final String DEPT_ID = "dddddddddddddddddddddddddddddddd";
    private static final String TEMPLATE_ID = "eeeeeeeeffffffff0000000011111111";
    private static final UUID APPLICANT_ID = UUID.fromString("11112222-3333-4444-5555-666677778888");
    private static final UUID COLLABORATOR_ID = UUID.fromString("99998888-7777-6666-5555-444433332222");
    private static final UUID FILE_ID = UUID.fromString("abcdabcd-abcd-abcd-abcd-abcdabcdabcd");
    private static final UUID WORKFLOW_INSTANCE_ID =
            UUID.fromString("fedcfedc-fedc-fedc-fedc-fedcfedcfedc");
    private static final String IDEMPOTENCY_KEY = "submit-key-0001";

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-11-18T02:00:00Z"), ZoneId.of("Asia/Shanghai"));
    private static final OffsetDateTime NOW = OffsetDateTime.of(
            2026, 11, 18, 10, 0, 0, 0, ZoneOffset.ofHours(8));

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private ApplicationVersionRepository applicationVersionRepository;

    @Mock
    private ApplicationMemberRepository applicationMemberRepository;

    @Mock
    private ApplyFormTemplateResolver formTemplateResolver;

    @Mock
    private FileObjectRepository fileObjectRepository;

    @Mock
    private ApplicationWorkflowLauncher workflowLauncher;

    @Mock
    private OutboxAppendPort outboxAppendPort;

    @Mock
    private AuthorizationService authorizationService;

    @Mock
    private AuditRecorder auditRecorder;

    private ApplicationContentHasher contentHasher;

    private ApplicationSubmitAppService service;

    private AuthContext auth;

    private ResolvedFormTemplate template;

    @BeforeEach
    void setUp() {
        contentHasher = new ApplicationContentHasher();
        service = new ApplicationSubmitAppService(applicationRepository, applicationVersionRepository,
                applicationMemberRepository, formTemplateResolver, contentHasher, fileObjectRepository,
                workflowLauncher, outboxAppendPort, authorizationService, auditRecorder, CLOCK);
        auth = new AuthContext(APPLICANT_ID, null, "E1001",
                Set.of(ApplicationPermissions.SUBMIT), Set.of(), Instant.parse("2026-11-18T02:00:00Z"));
        template = new ResolvedFormTemplate(TEMPLATE_ID, 1,
                Set.of("title", "budget"),
                Map.of("title", new FormFieldRule("string", 1, 512, null, null, null, null, null),
                        "budget", new FormFieldRule("number", null, null, null, null,
                                java.math.BigDecimal.ZERO, null, null)),
                Set.of("ETHICS_APPROVAL"));
    }

    @Test
    void tcApp004SubmitsDraftAndStartsWorkflow() {
        ApplicationEntity application = draftApplication(3);
        ApplicationVersionEntity draft = draftVersion(1);
        stubHappyPath(application, draft);

        ApplicationSubmission result = service.submit(APP_ID, submitCmd(3), IDEMPOTENCY_KEY, auth);

        assertThat(result.applicationId()).isEqualTo(APP_ID);
        assertThat(result.versionId()).isEqualTo(draft.getId());
        assertThat(result.versionNo()).isEqualTo(1);
        assertThat(result.status()).isEqualTo(ApplicationEntity.STATUS_IN_REVIEW);
        assertThat(result.workflowInstanceId()).isEqualTo(WORKFLOW_INSTANCE_ID.toString());

        assertThat(draft.getStatus()).isEqualTo(ApplicationVersionEntity.STATUS_SUBMITTED);
        assertThat(draft.getSubmitIdempotencyKey()).isEqualTo(IDEMPOTENCY_KEY);
        assertThat(draft.getSubmittedBy()).isEqualTo(APPLICANT_ID.toString().replace("-", ""));
        assertThat(draft.getSubmittedAt()).isEqualTo(NOW);
        assertThat(draft.getContentHash()).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(draft.getFormData()).contains("\"budget\":100000").contains("\"title\"");

        assertThat(application.getStatus()).isEqualTo(ApplicationEntity.STATUS_IN_REVIEW);
        assertThat(application.getCurrentVersionId()).isEqualTo(draft.getId());
        verify(applicationRepository).update(application);

        ArgumentCaptor<ApplicationWorkflowStart> launchCaptor =
                ArgumentCaptor.forClass(ApplicationWorkflowStart.class);
        verify(workflowLauncher).start(launchCaptor.capture());
        assertThat(launchCaptor.getValue().applicationId())
                .isEqualTo(UUID.fromString("aaaabbbb-cccc-dddd-eeee-ffff00001111"));
        assertThat(launchCaptor.getValue().versionId())
                .isEqualTo(UUID.fromString("12341234-1234-1234-1234-123412341234"));
        assertThat(launchCaptor.getValue().projectType()).isEqualTo("YEAR_PLAN");
        assertThat(launchCaptor.getValue().applicantId()).isEqualTo(APPLICANT_ID);
        assertThat(launchCaptor.getValue().at()).isEqualTo(NOW);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ApplicationMemberEntity>> memberCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(applicationMemberRepository).insertBatch(memberCaptor.capture());
        assertThat(memberCaptor.getValue()).hasSize(2);
        assertThat(memberCaptor.getValue().get(0).getUserId())
                .isEqualTo(APPLICANT_ID.toString().replace("-", ""));
        assertThat(memberCaptor.getValue().get(0).getMemberRole()).isEqualTo("PI");
        assertThat(memberCaptor.getValue().get(1).getUserId())
                .isEqualTo(COLLABORATOR_ID.toString().replace("-", ""));
        assertThat(memberCaptor.getValue()).allSatisfy(member ->
                assertThat(member.getApplicationVersionId()).isEqualTo(draft.getId()));

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxAppendPort).append(outboxCaptor.capture());
        OutboxEvent event = outboxCaptor.getValue();
        assertThat(event.aggregateType()).isEqualTo("APPLICATION");
        assertThat(event.aggregateId()).isEqualTo(UUID.fromString("aaaabbbb-cccc-dddd-eeee-ffff00001111"));
        assertThat(event.eventType()).isEqualTo("APPLICATION_SUBMITTED");
        assertThat(event.idempotencyKey()).isEqualTo("APPLICATION_SUBMITTED:" + IDEMPOTENCY_KEY);
        assertThat(event.payload()).containsEntry("version_id", draft.getId())
                .containsEntry("workflow_instance_id", WORKFLOW_INSTANCE_ID.toString())
                .containsEntry("version_no", 1);

        ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditRecorder).record(auditCaptor.capture());
        AuditEvent audit = auditCaptor.getValue();
        assertThat(audit.action()).isEqualTo("APPLICATION_SUBMITTED");
        assertThat(audit.objectType()).isEqualTo("APPLICATION");
        assertThat(audit.objectId()).isEqualTo(UUID.fromString("aaaabbbb-cccc-dddd-eeee-ffff00001111"));
        assertThat(audit.actorId()).isEqualTo(APPLICANT_ID);
    }

    @Test
    void tcApp005RejectsExpectedVersionMismatchWithoutSideEffects() {
        ApplicationEntity application = draftApplication(3);
        when(applicationRepository.selectForUpdate(APP_ID)).thenReturn(Optional.of(application));
        when(applicationVersionRepository.findByIdempotencyKey(IDEMPOTENCY_KEY))
                .thenReturn(Optional.empty());
        when(authorizationService.check(any(), any(), any())).thenReturn(AuthDecision.allow());

        assertThatThrownBy(() -> service.submit(APP_ID, submitCmd(2), IDEMPOTENCY_KEY, auth))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.APPLY_002));

        verify(applicationVersionRepository, never()).insert(any());
        verify(applicationVersionRepository, never()).update(any());
        verifyNoInteractions(workflowLauncher, outboxAppendPort, applicationMemberRepository,
                formTemplateResolver, auditRecorder);
        verify(applicationRepository, never()).update(any());
    }

    @Test
    void tcApp006RejectsMissingRequiredAttachment() {
        ApplicationEntity application = draftApplication(3);
        when(applicationRepository.selectForUpdate(APP_ID)).thenReturn(Optional.of(application));
        when(applicationVersionRepository.findByIdempotencyKey(IDEMPOTENCY_KEY))
                .thenReturn(Optional.empty());
        when(authorizationService.check(any(), any(), any())).thenReturn(AuthDecision.allow());
        when(formTemplateResolver.resolve("YEAR_PLAN")).thenReturn(template);
        when(formTemplateResolver.validate(eq(template), anyMap())).thenReturn(List.of());

        SubmitApplicationCmd withoutAttachment = new SubmitApplicationCmd(3, "请审阅",
                Map.of("title", "智能感知", "budget", 100000), List.of(),
                List.of(new SubmitApplicationCmd.CollaboratorCmd(
                        COLLABORATOR_ID.toString(), "CO_AUTHOR", "参与算法设计")));

        assertThatThrownBy(() -> service.submit(APP_ID, withoutAttachment, IDEMPOTENCY_KEY, auth))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.APPLY_001));

        verify(applicationVersionRepository, never()).insert(any());
        verify(applicationVersionRepository, never()).update(any());
        verifyNoInteractions(workflowLauncher, outboxAppendPort, applicationMemberRepository);
        verify(applicationRepository, never()).update(any());
    }

    @Test
    void rejectsAttachmentThatIsNotAvailable() {
        ApplicationEntity application = draftApplication(3);
        when(applicationRepository.selectForUpdate(APP_ID)).thenReturn(Optional.of(application));
        when(applicationVersionRepository.findByIdempotencyKey(IDEMPOTENCY_KEY))
                .thenReturn(Optional.empty());
        when(authorizationService.check(any(), any(), any())).thenReturn(AuthDecision.allow());
        when(formTemplateResolver.resolve("YEAR_PLAN")).thenReturn(template);
        when(formTemplateResolver.validate(eq(template), anyMap())).thenReturn(List.of());
        when(fileObjectRepository.findByIds(anyCollection()))
                .thenReturn(List.of(file(FileStatus.QUARANTINED)));

        assertThatThrownBy(() -> service.submit(APP_ID, submitCmd(3), IDEMPOTENCY_KEY, auth))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.FILE_001));

        verify(applicationVersionRepository, never()).insert(any());
        verify(applicationVersionRepository, never()).update(any());
        verifyNoInteractions(workflowLauncher, outboxAppendPort);
    }

    @Test
    void tcApp007RejectsCollaboratorWithOnlyViewPermission() {
        ApplicationEntity application = draftApplication(3);
        when(applicationVersionRepository.findByIdempotencyKey(IDEMPOTENCY_KEY))
                .thenReturn(Optional.empty());
        when(applicationRepository.selectForUpdate(APP_ID)).thenReturn(Optional.of(application));
        when(authorizationService.check(any(), any(), any()))
                .thenReturn(AuthDecision.deny(ErrorCode.COMMON_403));

        assertThatThrownBy(() -> service.submit(APP_ID, submitCmd(3), IDEMPOTENCY_KEY, auth))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.COMMON_403));

        verify(applicationVersionRepository, never()).insert(any());
        verify(applicationVersionRepository, never()).update(any());
        verifyNoInteractions(workflowLauncher, outboxAppendPort, applicationMemberRepository);
    }

    @Test
    void tcApp008SameIdempotencyKeyReturnsFirstVersionAndInstance() {
        ApplicationEntity application = draftApplication(3);
        ApplicationVersionEntity draft = draftVersion(1);
        stubHappyPath(application, draft);

        ApplicationSubmission first = service.submit(APP_ID, submitCmd(3), IDEMPOTENCY_KEY, auth);

        when(applicationVersionRepository.findByIdempotencyKey(IDEMPOTENCY_KEY))
                .thenReturn(Optional.of(draft));
        when(workflowLauncher.findLatest(any(), any()))
                .thenReturn(Optional.of(new WorkflowLaunch(WORKFLOW_INSTANCE_ID, "RUNNING")));

        ApplicationSubmission second = service.submit(APP_ID, submitCmd(3), IDEMPOTENCY_KEY, auth);

        assertThat(second.versionId()).isEqualTo(first.versionId());
        assertThat(second.workflowInstanceId()).isEqualTo(first.workflowInstanceId());
        assertThat(second.versionNo()).isEqualTo(1);
        verify(applicationVersionRepository, times(1)).update(draft);
        verify(applicationVersionRepository, never()).insert(any());
        verify(workflowLauncher, times(1)).start(any());
        verify(applicationRepository, times(1)).update(any());
    }

    @Test
    void supplementResubmitCreatesNextVersionNumber() {
        ApplicationEntity application = draftApplication(5);
        application.setStatus(ApplicationEntity.STATUS_SUPPLEMENT);
        when(applicationRepository.selectForUpdate(APP_ID)).thenReturn(Optional.of(application));
        when(applicationVersionRepository.findByIdempotencyKey(IDEMPOTENCY_KEY))
                .thenReturn(Optional.empty());
        when(authorizationService.check(any(), any(), any())).thenReturn(AuthDecision.allow());
        when(formTemplateResolver.resolve("YEAR_PLAN")).thenReturn(template);
        when(formTemplateResolver.validate(eq(template), anyMap())).thenReturn(List.of());
        when(fileObjectRepository.findByIds(anyCollection()))
                .thenReturn(List.of(file(FileStatus.AVAILABLE)));
        when(applicationVersionRepository.findDraftByApplicationId(APP_ID)).thenReturn(Optional.empty());
        when(applicationVersionRepository.findMaxVersionNo(APP_ID)).thenReturn(1);
        when(workflowLauncher.start(any()))
                .thenReturn(new WorkflowLaunch(WORKFLOW_INSTANCE_ID, "RUNNING"));
        when(applicationRepository.update(application)).thenReturn(1);

        ApplicationSubmission result = service.submit(APP_ID, submitCmd(5), IDEMPOTENCY_KEY, auth);

        ArgumentCaptor<ApplicationVersionEntity> versionCaptor =
                ArgumentCaptor.forClass(ApplicationVersionEntity.class);
        verify(applicationVersionRepository).insert(versionCaptor.capture());
        ApplicationVersionEntity inserted = versionCaptor.getValue();
        assertThat(inserted.getVersionNo()).isEqualTo(2);
        assertThat(inserted.getStatus()).isEqualTo(ApplicationVersionEntity.STATUS_SUBMITTED);
        assertThat(inserted.getSubmitIdempotencyKey()).isEqualTo(IDEMPOTENCY_KEY);
        assertThat(result.versionNo()).isEqualTo(2);
        assertThat(result.status()).isEqualTo(ApplicationEntity.STATUS_IN_REVIEW);
        assertThat(application.getStatus()).isEqualTo(ApplicationEntity.STATUS_IN_REVIEW);
    }

    @Test
    void tcApp009ConcurrentSubmitsAtMostOneSucceeds() throws Exception {
        ApplicationEntity application = draftApplication(3);
        ApplicationVersionEntity draft = draftVersion(1);
        FakeApplicationRepository applicationStore = new FakeApplicationRepository(application);
        FakeApplicationVersionRepository versionStore = new FakeApplicationVersionRepository(draft);
        ApplicationSubmitAppService concurrentService = new ApplicationSubmitAppService(
                applicationStore, versionStore, applicationMemberRepository, formTemplateResolver,
                contentHasher, fileObjectRepository, workflowLauncher, outboxAppendPort,
                authorizationService, auditRecorder, CLOCK);

        when(authorizationService.check(any(), any(), any())).thenReturn(AuthDecision.allow());
        when(formTemplateResolver.resolve("YEAR_PLAN")).thenReturn(template);
        when(formTemplateResolver.validate(eq(template), anyMap())).thenReturn(List.of());
        when(fileObjectRepository.findByIds(anyCollection()))
                .thenReturn(List.of(file(FileStatus.AVAILABLE)));
        when(workflowLauncher.start(any()))
                .thenReturn(new WorkflowLaunch(WORKFLOW_INSTANCE_ID, "RUNNING"));

        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (String key : List.of("concat-key-A", "concat-key-B")) {
                futures.add(pool.submit(() -> {
                    startGate.await(5, TimeUnit.SECONDS);
                    try {
                        return concurrentService.submit(APP_ID, submitCmd(3), key, auth);
                    } catch (BizException ex) {
                        return ex;
                    }
                }));
            }
            startGate.countDown();
            List<Object> results = new ArrayList<>();
            for (Future<Object> future : futures) {
                results.add(future.get(10, TimeUnit.SECONDS));
            }

            List<ApplicationSubmission> successes = results.stream()
                    .filter(ApplicationSubmission.class::isInstance)
                    .map(ApplicationSubmission.class::cast)
                    .toList();
            List<BizException> failures = results.stream()
                    .filter(BizException.class::isInstance)
                    .map(BizException.class::cast)
                    .toList();

            assertThat(successes).hasSize(1);
            assertThat(failures).hasSize(1);
            assertThat(failures.get(0).getErrorCode()).isEqualTo(ErrorCode.APPLY_002);
            assertThat(versionStore.submittedCount()).isEqualTo(1);
            verify(workflowLauncher, times(1)).start(any());
            verify(outboxAppendPort, times(1)).append(any());
        } finally {
            pool.shutdownNow();
            if (applicationStore.availablePermits() == 0) {
                applicationStore.releaseRowLock();
            }
        }
    }

    private void stubHappyPath(ApplicationEntity application, ApplicationVersionEntity draft) {
        when(applicationVersionRepository.findByIdempotencyKey(IDEMPOTENCY_KEY))
                .thenReturn(Optional.empty());
        when(applicationRepository.selectForUpdate(APP_ID)).thenReturn(Optional.of(application));
        when(authorizationService.check(any(), any(), any())).thenReturn(AuthDecision.allow());
        when(formTemplateResolver.resolve("YEAR_PLAN")).thenReturn(template);
        when(formTemplateResolver.validate(eq(template), anyMap())).thenReturn(List.of());
        when(fileObjectRepository.findByIds(anyCollection()))
                .thenReturn(List.of(file(FileStatus.AVAILABLE)));
        when(applicationVersionRepository.findDraftByApplicationId(APP_ID))
                .thenReturn(Optional.of(draft));
        when(applicationVersionRepository.update(draft)).thenReturn(1);
        when(workflowLauncher.start(any()))
                .thenReturn(new WorkflowLaunch(WORKFLOW_INSTANCE_ID, "RUNNING"));
        lenient().when(workflowLauncher.findLatest(any(), any()))
                .thenReturn(Optional.of(new WorkflowLaunch(WORKFLOW_INSTANCE_ID, "RUNNING")));
        when(applicationRepository.update(application)).thenReturn(1);
        lenient().when(applicationVersionRepository.findMaxVersionNo(APP_ID)).thenReturn(1);
    }

    private static SubmitApplicationCmd submitCmd(int expectedVersion) {
        return new SubmitApplicationCmd(expectedVersion, "请审阅",
                Map.of("title", "面向复杂工况的智能感知方法研究", "budget", 100000),
                List.of(new SubmitApplicationCmd.AttachmentCmd(
                        "ETHICS_APPROVAL", FILE_ID.toString())),
                List.of(new SubmitApplicationCmd.CollaboratorCmd(
                        COLLABORATOR_ID.toString(), "CO_AUTHOR", "参与算法设计")));
    }

    private static ApplicationEntity draftApplication(int version) {
        ApplicationEntity application = new ApplicationEntity();
        application.setId(APP_ID);
        application.setApplicationNo("APP-2026-000123");
        application.setBatchId("aaaabbbbccccddddeeeeffff00002222");
        application.setProjectType("YEAR_PLAN");
        application.setApplicantId(APPLICANT_ID.toString().replace("-", ""));
        application.setDeptId(DEPT_ID);
        application.setTitle("面向复杂工况的智能感知方法研究");
        application.setSourceType(ApplicationEntity.SOURCE_INTERNAL);
        application.setStatus(ApplicationEntity.STATUS_DRAFT);
        application.setVersion(version);
        return application;
    }

    private static ApplicationVersionEntity draftVersion(int versionNo) {
        ApplicationVersionEntity draft = new ApplicationVersionEntity();
        draft.setId("12341234123412341234123412341234");
        draft.setApplicationId(APP_ID);
        draft.setVersionNo(versionNo);
        draft.setStatus(ApplicationVersionEntity.STATUS_DRAFT);
        draft.setVersion(0);
        return draft;
    }

    private static FileObject file(FileStatus status) {
        return FileObject.rehydrate(FILE_ID, "rpm-files", "2026/11/18/file-id",
                "ethics.pdf", "application/pdf", 2048,
                "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08",
                status, status == FileStatus.AVAILABLE ? ScanStatus.PASSED : ScanStatus.PENDING,
                APPLICANT_ID, Instant.parse("2026-11-18T01:00:00Z"), 0);
    }

    /**
     * 内存申报主档仓储：以信号量模拟 {@code SELECT ... FOR UPDATE} 的行锁，
     * 锁持有到 {@link #update(ApplicationEntity)} 执行结束（对应事务提交）。
     */
    private static final class FakeApplicationRepository implements ApplicationRepository {

        private final Map<String, ApplicationEntity> store = new LinkedHashMap<>();

        private final Semaphore rowLock = new Semaphore(1);

        private FakeApplicationRepository(ApplicationEntity application) {
            store.put(application.getId(), application);
        }

        @Override
        public void insert(ApplicationEntity application) {
            synchronized (store) {
                store.put(application.getId(), application);
            }
        }

        @Override
        public synchronized Optional<ApplicationEntity> findById(String id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public synchronized Optional<ApplicationEntity> findByApplicationNo(String applicationNo) {
            return store.values().stream()
                    .filter(item -> Objects.equals(item.getApplicationNo(), applicationNo))
                    .findFirst();
        }

        @Override
        public synchronized List<ApplicationEntity> findByApplicantIdAndStatus(String applicantId,
                                                                              String status) {
            return store.values().stream()
                    .filter(item -> Objects.equals(item.getApplicantId(), applicantId)
                            && Objects.equals(item.getStatus(), status))
                    .toList();
        }

        @Override
        public synchronized ApplicationPageResult search(ApplicationSearchCriteria criteria) {
            List<ApplicationEntity> items = store.values().stream()
                    .filter(item -> criteria.status() == null
                            || Objects.equals(item.getStatus(), criteria.status()))
                    .toList();
            return new ApplicationPageResult(items, items.size());
        }

        @Override
        public Optional<ApplicationEntity> selectForUpdate(String applicationId) {
            rowLock.acquireUninterruptibly();
            return findById(applicationId);
        }

        @Override
        public synchronized int update(ApplicationEntity application) {
            try {
                ApplicationEntity stored = store.get(application.getId());
                if (stored == null || !Objects.equals(stored.getVersion(), application.getVersion())) {
                    return 0;
                }
                stored.setStatus(application.getStatus());
                stored.setCurrentVersionId(application.getCurrentVersionId());
                stored.setVersion(application.getVersion() + 1);
                return 1;
            } finally {
                rowLock.release();
            }
        }

        int availablePermits() {
            return rowLock.availablePermits();
        }

        void releaseRowLock() {
            rowLock.release();
        }
    }

    /**
     * 内存申报版本仓储：维护幂等键唯一映射，insert/update 均同步，模拟唯一索引与乐观锁。
     */
    private static final class FakeApplicationVersionRepository implements ApplicationVersionRepository {

        private final Map<String, ApplicationVersionEntity> byId = new LinkedHashMap<>();

        private final Map<String, ApplicationVersionEntity> byIdempotencyKey = new LinkedHashMap<>();

        private int sequence = 1;

        private FakeApplicationVersionRepository(ApplicationVersionEntity draft) {
            byId.put(draft.getId(), draft);
        }

        @Override
        public synchronized void insert(ApplicationVersionEntity version) {
            if (version.getId() == null) {
                version.setId(String.format("%032x", sequence++));
            }
            if (version.getSubmitIdempotencyKey() != null
                    && byIdempotencyKey.containsKey(version.getSubmitIdempotencyKey())) {
                throw new org.springframework.dao.DuplicateKeyException("幂等键重复");
            }
            version.setVersion(0);
            byId.put(version.getId(), version);
            if (version.getSubmitIdempotencyKey() != null) {
                byIdempotencyKey.put(version.getSubmitIdempotencyKey(), version);
            }
        }

        @Override
        public synchronized Optional<ApplicationVersionEntity> findById(String id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public synchronized Optional<ApplicationVersionEntity> findByApplicationIdAndVersionNo(
                String applicationId, int versionNo) {
            return byId.values().stream()
                    .filter(item -> Objects.equals(item.getApplicationId(), applicationId)
                            && Objects.equals(item.getVersionNo(), versionNo))
                    .findFirst();
        }

        @Override
        public synchronized Optional<ApplicationVersionEntity> findByIdempotencyKey(
                String idempotencyKey) {
            return Optional.ofNullable(byIdempotencyKey.get(idempotencyKey));
        }

        @Override
        public synchronized Optional<ApplicationVersionEntity> findDraftByApplicationId(
                String applicationId) {
            return byId.values().stream()
                    .filter(item -> Objects.equals(item.getApplicationId(), applicationId)
                            && ApplicationVersionEntity.STATUS_DRAFT.equals(item.getStatus()))
                    .max(Comparator.comparing(ApplicationVersionEntity::getVersionNo));
        }

        @Override
        public synchronized int findMaxVersionNo(String applicationId) {
            return byId.values().stream()
                    .filter(item -> Objects.equals(item.getApplicationId(), applicationId))
                    .map(ApplicationVersionEntity::getVersionNo)
                    .filter(Objects::nonNull)
                    .max(Integer::compareTo)
                    .orElse(0);
        }

        @Override
        public synchronized int update(ApplicationVersionEntity version) {
            ApplicationVersionEntity stored = byId.get(version.getId());
            if (stored == null || !Objects.equals(stored.getVersion(), version.getVersion())) {
                return 0;
            }
            stored.setFormData(version.getFormData());
            stored.setContentHash(version.getContentHash());
            stored.setStatus(version.getStatus());
            stored.setSubmittedBy(version.getSubmittedBy());
            stored.setSubmittedAt(version.getSubmittedAt());
            stored.setSubmitIdempotencyKey(version.getSubmitIdempotencyKey());
            stored.setVersion(version.getVersion() + 1);
            if (stored.getSubmitIdempotencyKey() != null) {
                byIdempotencyKey.put(stored.getSubmitIdempotencyKey(), stored);
            }
            return 1;
        }

        synchronized int submittedCount() {
            return (int) byId.values().stream()
                    .filter(item -> ApplicationVersionEntity.STATUS_SUBMITTED.equals(item.getStatus()))
                    .count();
        }
    }
}
