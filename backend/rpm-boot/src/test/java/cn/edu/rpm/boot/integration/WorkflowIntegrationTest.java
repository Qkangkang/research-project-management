package cn.edu.rpm.boot.integration;

import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.common.util.UuidUtils;
import cn.edu.rpm.iam.application.dto.AuthContext;
import cn.edu.rpm.iam.domain.service.SeparationOfDutyGuard;
import cn.edu.rpm.workflow.application.dto.ActTaskCmd;
import cn.edu.rpm.workflow.application.dto.WithdrawCmd;
import cn.edu.rpm.workflow.application.registry.ApprovalResultHandlerRegistry;
import cn.edu.rpm.workflow.application.service.WorkflowTaskAppService;
import cn.edu.rpm.workflow.domain.dto.ApprovalAction;
import cn.edu.rpm.workflow.domain.dto.ApprovalResult;
import cn.edu.rpm.workflow.domain.dto.StartWorkflowCmd;
import cn.edu.rpm.workflow.domain.dto.TaskActionResult;
import cn.edu.rpm.workflow.domain.dto.WorkflowInstance;
import cn.edu.rpm.workflow.domain.port.ApprovalResultHandler;
import cn.edu.rpm.workflow.infrastructure.adapter.FlowableWorkflowAdapter;
import cn.edu.rpm.workflow.infrastructure.config.FlowableConfig;
import cn.edu.rpm.workflow.infrastructure.entity.WfCallbackLogEntity;
import cn.edu.rpm.workflow.infrastructure.entity.WfDelegationEntity;
import cn.edu.rpm.workflow.infrastructure.entity.WfInstanceEntity;
import cn.edu.rpm.workflow.infrastructure.entity.WfTaskEntity;
import cn.edu.rpm.workflow.infrastructure.repository.WfCallbackLogRepository;
import cn.edu.rpm.workflow.infrastructure.repository.WfDelegationRepository;
import cn.edu.rpm.workflow.infrastructure.repository.WfInstanceRepository;
import cn.edu.rpm.workflow.infrastructure.repository.WfTaskRepository;
import cn.edu.rpm.workflow.infrastructure.repository.WorkflowTaskRow;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.flowable.common.engine.impl.history.HistoryLevel;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.RepositoryService;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 完整审批流集成测试（T-WF-016；E2E-01）。
 *
 * <p>真实 PostgreSQL 16 + 真实 Flowable 7.2.0 引擎 + {@link FlowableWorkflowAdapter} +
 * {@link WorkflowTaskAppService} + 业务回调处理器；{@code wf_*} 业务表使用内存仓储实现，
 * 以便在无 Spring 容器的情况下精确观察任务/实例状态（真实 SQL 由各 Repository 单测与
 * Batch-6 Schema 集成测试覆盖）。</p>
 *
 * <p>覆盖 TC-APP-019～TC-APP-027 的主链路：启动 → 第一节点通过（产生下一节点任务）→
 * 第二节点通过（实例 APPROVED，回调 2 次）；重复 {@code request_id} 返回首次结果；
 * 撤回后任务 CANCELLED 且不可再提交；拒绝终止流程实例。</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WorkflowIntegrationTest {

    private static final String PROCESS_KEY = "rpmTestApproval";

    private EmbeddedPostgres postgres;

    private ProcessEngine engine;

    private WorkflowTaskAppService appService;

    private FlowableWorkflowAdapter adapter;

    private RecordingHandler handler;

    private final InMemoryInstanceRepository instanceRepository = new InMemoryInstanceRepository();

    private final InMemoryTaskRepository taskRepository = new InMemoryTaskRepository(instanceRepository);

    private final InMemoryCallbackLogRepository callbackLogRepository = new InMemoryCallbackLogRepository();

    private final UUID initiatorId = UUID.randomUUID();

    private final UUID assigneeId = UUID.randomUUID();

    private WorkflowInstance firstInstance;

    private WfTaskEntity firstTask;

    private UUID firstRequestId;

    @BeforeAll
    void startEngine() throws Exception {
        postgres = EmbeddedPostgres.builder().setPort(0).start();
        DataSource dataSource = postgres.getPostgresDatabase();
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();

        SpringProcessEngineConfiguration configuration = new SpringProcessEngineConfiguration();
        new FlowableConfig().configure(configuration);
        configuration.setDataSource(dataSource);
        configuration.setTransactionManager(new DataSourceTransactionManager(dataSource));
        configuration.setAsyncExecutorActivate(false);
        configuration.setHistoryLevel(HistoryLevel.FULL);
        engine = configuration.buildProcessEngine();

        RepositoryService repositoryService = engine.getRepositoryService();
        repositoryService.createDeployment()
                .name(FlowableConfig.TEST_PROCESS_DEPLOYMENT_NAME)
                .addClasspathResource(FlowableConfig.TEST_PROCESS_RESOURCE)
                .deploy();

        handler = new RecordingHandler("APPLICATION");
        adapter = new FlowableWorkflowAdapter(engine.getRuntimeService(), engine.getTaskService(),
                instanceRepository, taskRepository, callbackLogRepository,
                new InMemoryDelegationRepository(), new ObjectMapper());
        appService = new WorkflowTaskAppService(adapter, taskRepository, instanceRepository,
                callbackLogRepository, new InMemoryDelegationRepository(),
                new ApprovalResultHandlerRegistry(List.of(handler)),
                new SeparationOfDutyGuard((bizType, bizId, expertId) -> List.of()),
                new ObjectMapper());
    }

    @AfterAll
    void stopEngine() throws Exception {
        if (engine != null) {
            engine.close();
        }
        if (postgres != null) {
            postgres.close();
        }
    }

    @Test
    @Order(1)
    void startCreatesRunningInstanceAndFirstTask() {
        firstInstance = adapter.start(startCmd("inst-1"));

        assertThat(firstInstance.status()).isEqualTo(WfInstanceEntity.STATUS_RUNNING);
        assertThat(firstInstance.flowableInstanceId()).isNotBlank();
        assertThat(firstInstance.bizType()).isEqualTo("APPLICATION");
        firstTask = taskRepository.findByInstance(firstInstance.instanceId().toString().replace("-", ""))
                .stream().filter(task -> WfTaskEntity.STATUS_PENDING.equals(task.getStatus()))
                .findFirst().orElseThrow();
        assertThat(firstTask.getNodeKey()).isEqualTo("formalReview");
        assertThat(firstTask.getAssigneeId()).isEqualTo(UuidUtils.formatCompact(assigneeId));
        assertThat(handler.approved.get()).isZero();
    }

    @Test
    @Order(2)
    void tcApp020ApproveFirstNodeProducesDecisionTask() {
        firstRequestId = UUID.randomUUID();

        TaskActionResult result = appService.act(UUID.fromString(canonical(firstTask.getId())),
                new ActTaskCmd(firstRequestId, firstTask.getVersion(), ApprovalAction.APPROVED,
                        "形式审查通过", null, null),
                assigneeAuth());

        assertThat(result.taskStatus()).isEqualTo(WfTaskEntity.STATUS_APPROVED);
        assertThat(result.instanceStatus()).isEqualTo(WfInstanceEntity.STATUS_RUNNING);
        assertThat(result.nextTaskIds()).hasSize(1);
        assertThat(handler.approved.get()).isEqualTo(1);
        WfTaskEntity decisionTask = taskRepository.findById(
                UuidUtils.formatCompact(result.nextTaskIds().get(0))).orElseThrow();
        assertThat(decisionTask.getNodeKey()).isEqualTo("decision");
    }

    @Test
    @Order(3)
    void tcApp023DuplicateRequestIdDoesNotCallHandlerTwice() {
        WfTaskEntity approvedTask = taskRepository.findById(firstTask.getId()).orElseThrow();
        int before = handler.approved.get();

        TaskActionResult replay = appService.act(UUID.fromString(canonical(firstTask.getId())),
                new ActTaskCmd(firstRequestId, 0, ApprovalAction.APPROVED, "重复提交", null, null),
                assigneeAuth());

        assertThat(replay.taskStatus()).isEqualTo(WfTaskEntity.STATUS_APPROVED);
        assertThat(handler.approved.get()).isEqualTo(before);
        assertThat(approvedTask.getVersion()).isEqualTo(1);
        assertThat(callbackLogRepository.findByRequestId(
                UuidUtils.formatCompact(firstRequestId))).isPresent();
    }

    @Test
    @Order(4)
    void secondNodeApprovalFinishesInstanceAndFiresSecondCallback() {
        WfTaskEntity decisionTask = pendingTask("decision");
        TaskActionResult result = appService.act(UUID.fromString(canonical(decisionTask.getId())),
                new ActTaskCmd(UUID.randomUUID(), decisionTask.getVersion(),
                        ApprovalAction.APPROVED, "决策通过", null, null),
                assigneeAuth());

        assertThat(result.taskStatus()).isEqualTo(WfTaskEntity.STATUS_APPROVED);
        assertThat(result.instanceStatus()).isEqualTo(WfInstanceEntity.STATUS_APPROVED);
        assertThat(result.nextTaskIds()).isEmpty();
        assertThat(handler.approved.get()).isEqualTo(2);
        assertThat(engine.getRuntimeService().createProcessInstanceQuery()
                .processInstanceId(firstInstance.flowableInstanceId()).count()).isZero();
        assertThat(engine.getHistoryService().createHistoricProcessInstanceQuery()
                .processInstanceId(firstInstance.flowableInstanceId()).finished().count()).isEqualTo(1);
    }

    @Test
    @Order(5)
    void tcApp027WithdrawCancelsTaskAndBlocksFurtherSubmission() {
        WorkflowInstance instance = adapter.start(startCmd("inst-2"));
        WfTaskEntity pending = taskRepository
                .findByInstance(UuidUtils.formatCompact(instance.instanceId()))
                .stream().filter(task -> WfTaskEntity.STATUS_PENDING.equals(task.getStatus()))
                .findFirst().orElseThrow();

        appService.withdraw(new WithdrawCmd(instance.instanceId(), "申请人撤回", UUID.randomUUID()),
                initiatorAuth());

        assertThat(pending.getStatus()).isEqualTo(WfTaskEntity.STATUS_CANCELLED);
        assertThat(instanceRepository.findById(UuidUtils.formatCompact(instance.instanceId()))
                .orElseThrow().getStatus()).isEqualTo(WfInstanceEntity.STATUS_CANCELLED);
        assertThat(handler.withdrawn.get()).isEqualTo(1);
        assertThatThrownBy(() -> appService.act(UUID.fromString(canonical(pending.getId())),
                new ActTaskCmd(UUID.randomUUID(), pending.getVersion(), ApprovalAction.APPROVED,
                        "撤回后提交", null, null),
                assigneeAuth()))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.WF_004));
    }

    @Test
    @Order(6)
    void tcApp022RejectTerminatesProcessAndFiresRejectedCallback() {
        WorkflowInstance instance = adapter.start(startCmd("inst-3"));
        WfTaskEntity pending = taskRepository
                .findByInstance(UuidUtils.formatCompact(instance.instanceId()))
                .stream().filter(task -> WfTaskEntity.STATUS_PENDING.equals(task.getStatus()))
                .findFirst().orElseThrow();

        TaskActionResult result = appService.act(UUID.fromString(canonical(pending.getId())),
                new ActTaskCmd(UUID.randomUUID(), pending.getVersion(), ApprovalAction.REJECTED,
                        "不符合申报指南", null, null),
                assigneeAuth());

        assertThat(result.instanceStatus()).isEqualTo(WfInstanceEntity.STATUS_REJECTED);
        assertThat(handler.rejected.get()).isEqualTo(1);
        assertThat(engine.getRuntimeService().createProcessInstanceQuery()
                .processInstanceId(instance.flowableInstanceId()).count()).isZero();
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private StartWorkflowCmd startCmd(String bizSuffix) {
        return new StartWorkflowCmd("APPLICATION",
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                PROCESS_KEY, initiatorId, Map.of("assignee", UuidUtils.formatCompact(assigneeId),
                "biz", bizSuffix));
    }

    private WfTaskEntity pendingTask(String nodeKey) {
        return taskRepository.findByInstance(UuidUtils.formatCompact(firstInstance.instanceId()))
                .stream().filter(task -> WfTaskEntity.STATUS_PENDING.equals(task.getStatus()))
                .filter(task -> nodeKey.equals(task.getNodeKey()))
                .findFirst().orElseThrow();
    }

    private AuthContext assigneeAuth() {
        return new AuthContext(assigneeId, null, "emp-assignee", Set.of(), Set.of(), Instant.now());
    }

    private AuthContext initiatorAuth() {
        return new AuthContext(initiatorId, null, "emp-initiator", Set.of(), Set.of(), Instant.now());
    }

    private static String canonical(String compact) {
        return compact.substring(0, 8) + "-" + compact.substring(8, 12) + "-"
                + compact.substring(12, 16) + "-" + compact.substring(16, 20) + "-"
                + compact.substring(20);
    }

    /**
     * 记录回调次数的业务处理器。
     */
    private static final class RecordingHandler implements ApprovalResultHandler {

        private final String bizType;

        private final AtomicInteger approved = new AtomicInteger();

        private final AtomicInteger rejected = new AtomicInteger();

        private final AtomicInteger returned = new AtomicInteger();

        private final AtomicInteger withdrawn = new AtomicInteger();

        private RecordingHandler(String bizType) {
            this.bizType = bizType;
        }

        @Override
        public String bizType() {
            return bizType;
        }

        @Override
        public void onApproved(ApprovalResult result) {
            approved.incrementAndGet();
        }

        @Override
        public void onRejected(ApprovalResult result) {
            rejected.incrementAndGet();
        }

        @Override
        public void onReturned(ApprovalResult result) {
            returned.incrementAndGet();
        }

        @Override
        public void onWithdrawn(ApprovalResult result) {
            withdrawn.incrementAndGet();
        }
    }

    /**
     * 内存版 {@link WfInstanceRepository}。
     */
    private static final class InMemoryInstanceRepository implements WfInstanceRepository {

        private final Map<String, WfInstanceEntity> instances = new LinkedHashMap<>();

        @Override
        public void insert(WfInstanceEntity instance) {
            if (instance.getId() == null) {
                instance.setId(UuidUtils.formatCompact(UUID.randomUUID()));
            }
            instances.put(instance.getId(), instance);
        }

        @Override
        public Optional<WfInstanceEntity> findById(String id) {
            return Optional.ofNullable(instances.get(id));
        }

        @Override
        public Optional<WfInstanceEntity> findRunningByBiz(String bizType, String bizId,
                                                           String bizVersionId) {
            return instances.values().stream()
                    .filter(instance -> WfInstanceEntity.STATUS_RUNNING.equals(instance.getStatus()))
                    .filter(instance -> bizType.equals(instance.getBizType()))
                    .filter(instance -> bizId.equals(instance.getBizId()))
                    .filter(instance -> bizVersionId.equals(instance.getBizVersionId()))
                    .findFirst();
        }

        @Override
        public Optional<WfInstanceEntity> findLatestByBiz(String bizType, String bizId,
                                                          String bizVersionId) {
            return instances.values().stream()
                    .filter(instance -> bizType.equals(instance.getBizType()))
                    .filter(instance -> bizId.equals(instance.getBizId()))
                    .filter(instance -> bizVersionId.equals(instance.getBizVersionId()))
                    .reduce((first, second) -> second);
        }

        @Override
        public List<WfInstanceEntity> findInitiatedPage(String initiatorId, long offset, int limit) {
            throw new UnsupportedOperationException("本集成测试不覆盖我发起的分页");
        }

        @Override
        public long countInitiated(String initiatorId) {
            throw new UnsupportedOperationException("本集成测试不覆盖我发起的分页");
        }

        @Override
        public int update(WfInstanceEntity instance) {
            if (!instances.containsKey(instance.getId())) {
                return 0;
            }
            instance.setVersion(instance.getVersion() + 1);
            return 1;
        }
    }

    /**
     * 内存版 {@link WfTaskRepository}。
     */
    private static final class InMemoryTaskRepository implements WfTaskRepository {

        private final Map<String, WfTaskEntity> tasks = new LinkedHashMap<>();

        private final InMemoryInstanceRepository instanceRepository;

        private InMemoryTaskRepository(InMemoryInstanceRepository instanceRepository) {
            this.instanceRepository = instanceRepository;
        }

        @Override
        public void insert(WfTaskEntity task) {
            if (task.getId() == null) {
                task.setId(UuidUtils.formatCompact(UUID.randomUUID()));
            }
            if (task.getVersion() == null) {
                task.setVersion(0);
            }
            tasks.put(task.getId(), task);
        }

        @Override
        public Optional<WfTaskEntity> findById(String id) {
            return Optional.ofNullable(tasks.get(id));
        }

        @Override
        public List<WfTaskEntity> findPendingByAssignee(String assigneeId) {
            return tasks.values().stream()
                    .filter(task -> WfTaskEntity.STATUS_PENDING.equals(task.getStatus()))
                    .filter(task -> assigneeId.equals(task.getAssigneeId()))
                    .toList();
        }

        @Override
        public List<WfTaskEntity> findByInstance(String instanceId) {
            return tasks.values().stream()
                    .filter(task -> instanceId.equals(task.getInstanceId()))
                    .sorted((left, right) -> Integer.compare(
                            left.getRoundNo() == null ? 1 : left.getRoundNo(),
                            right.getRoundNo() == null ? 1 : right.getRoundNo()))
                    .toList();
        }

        @Override
        public Optional<WfTaskEntity> findByActionRequestId(String actionRequestId) {
            return tasks.values().stream()
                    .filter(task -> actionRequestId.equals(task.getActionRequestId()))
                    .findFirst();
        }

        @Override
        public int update(WfTaskEntity task) {
            if (!tasks.containsKey(task.getId())) {
                return 0;
            }
            task.setVersion(task.getVersion() + 1);
            return 1;
        }

        @Override
        public List<WorkflowTaskRow> findTaskPage(String assigneeId, String status, String bizType,
                                                  long offset, int limit) {
            return new ArrayList<>();
        }

        @Override
        public long countTasks(String assigneeId, String status, String bizType) {
            return 0;
        }

        @Override
        public int cancelPendingTasks(String instanceId, OffsetDateTime at) {
            int cancelled = 0;
            for (WfTaskEntity task : findByIds(instanceId)) {
                if (WfTaskEntity.STATUS_PENDING.equals(task.getStatus())
                        || WfTaskEntity.STATUS_PROCESSING.equals(task.getStatus())) {
                    task.cancel(at);
                    update(task);
                    cancelled++;
                }
            }
            return cancelled;
        }

        private List<WfTaskEntity> findByIds(String instanceId) {
            return tasks.values().stream()
                    .filter(task -> instanceId.equals(task.getInstanceId()))
                    .toList();
        }
    }

    /**
     * 内存版 {@link WfCallbackLogRepository}。
     */
    private static final class InMemoryCallbackLogRepository implements WfCallbackLogRepository {

        private final Map<String, WfCallbackLogEntity> logs = new LinkedHashMap<>();

        @Override
        public void insert(WfCallbackLogEntity log) {
            if (logs.containsKey(log.getRequestId())) {
                throw new IllegalStateException("duplicate request_id: " + log.getRequestId());
            }
            logs.put(log.getRequestId(), log);
        }

        @Override
        public Optional<WfCallbackLogEntity> findByRequestId(String requestId) {
            return Optional.ofNullable(logs.get(requestId));
        }
    }

    /**
     * 空委托仓储（本次集成测试不覆盖代理审批）。
     */
    private static final class InMemoryDelegationRepository implements WfDelegationRepository {

        @Override
        public void insert(WfDelegationEntity delegation) {
        }

        @Override
        public List<WfDelegationEntity> findValidByDelegate(String delegateId, OffsetDateTime at) {
            return List.of();
        }
    }
}
