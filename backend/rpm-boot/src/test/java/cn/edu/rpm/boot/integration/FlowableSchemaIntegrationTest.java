package cn.edu.rpm.boot.integration;

import cn.edu.rpm.workflow.infrastructure.config.FlowableConfig;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.output.MigrateResult;
import org.flowable.engine.HistoryService;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.common.engine.impl.history.HistoryLevel;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * 真实 PostgreSQL 16 上的迁移、Flowable 引擎与数据库约束验证。
 *
 * <p>覆盖 Batch-6 因本机无数据库而延后的验证项：
 * <ul>
 *   <li>V01～V09 在空库上可完整执行（V09 的 SQL 语法与列引用真实被数据库解析）；</li>
 *   <li>{@code flowable} schema 与 7.2.0 版本元数据可让引擎在 {@code database-schema-update=false} 下启动；</li>
 *   <li>测试流程 {@code rpmTestApproval} 可部署、启动、两节点通过；</li>
 *   <li>部分唯一索引、幂等键、CHECK 约束（含拒绝 {@code COMPLETED}）按 DDL 生效。</li>
 * </ul>
 *
 * <p>数据库使用 Zonky 嵌入式 PostgreSQL 16（test 作用域，不进入运行时），无需 Docker。
 * 本测试属于代码生成阶段的引擎/ Schema 冒烟，不替代 T-WF-016 的业务审批流集成测试。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FlowableSchemaIntegrationTest {

    private static final int EXPECTED_FLOWABLE_TABLE_COUNT = 45;
    private static final String EXPECTED_SCHEMA_VERSION = "7.2.0.2";

    private EmbeddedPostgres postgres;
    private DataSource dataSource;
    private MigrateResult migrateResult;
    private ProcessEngine engine;

    @BeforeAll
    void startDatabaseAndMigrate() throws Exception {
        postgres = EmbeddedPostgres.builder().setPort(0).start();
        dataSource = postgres.getPostgresDatabase();
        migrateResult = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    @AfterAll
    void stopDatabase() throws Exception {
        if (engine != null) {
            engine.close();
        }
        if (postgres != null) {
            postgres.close();
        }
    }

    @Test
    @Order(1)
    void flywayMigratesV01ToV09OnEmptyDatabase() throws SQLException {
        assertThat(migrateResult.success).isTrue();
        assertThat(migrateResult.migrationsExecuted).isGreaterThanOrEqualTo(9);

        Flyway flyway = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load();
        MigrationVersion current = flyway.info().current().getVersion();
        assertThat(current).isNotNull();
        assertThat(current.compareTo(MigrationVersion.fromVersion("9"))).isGreaterThanOrEqualTo(0);
        assertThat(flyway.info().applied()).hasSize(migrateResult.migrationsExecuted);
        assertThat(flyway.info().applied()).allSatisfy(info ->
                assertThat(info.getState().isApplied()).isTrue());

        assertThat(countFlowableTables()).isEqualTo(EXPECTED_FLOWABLE_TABLE_COUNT);
        assertThat(readProperty("ACT_GE_PROPERTY", "schema.version")).isEqualTo(EXPECTED_SCHEMA_VERSION);
        assertThat(readProperty("ACT_GE_PROPERTY", "common.schema.version")).isEqualTo(EXPECTED_SCHEMA_VERSION);
        assertThat(readProperty("ACT_GE_PROPERTY", "eventregistry.schema.version")).isEqualTo(EXPECTED_SCHEMA_VERSION);
        assertThat(readProperty("ACT_ID_PROPERTY", "schema.version")).isEqualTo(EXPECTED_SCHEMA_VERSION);

        // V09 补齐的共享表列必须真实存在，否则引擎运行期会报列不存在
        assertThat(columnCount("ACT_RU_TASK")).isGreaterThanOrEqualTo(30);
        assertThat(existsColumn("ACT_RU_TASK", "SCOPE_ID_")).isTrue();
        assertThat(existsColumn("ACT_HI_TASKINST", "PROPAGATED_STAGE_INST_ID_")).isTrue();
        assertThat(existsColumn("ACT_RU_ACTINST", "CALL_PROC_INST_ID_")).isTrue();
        // V03 误名已按 V09 重命名为官方表名
        assertThat(existsTable("ACT_RU_EXTERNAL_JOB")).isTrue();
        assertThat(existsTable("ACT_RU_EXTERNAL_WORKER_JOB")).isFalse();
    }

    @Test
    @Order(2)
    void engineStartsWithSchemaUpdateDisabledAndRunsTwoNodeProcess() {
        engine = buildEngine();

        RepositoryService repositoryService = engine.getRepositoryService();
        Deployment deployment = repositoryService.createDeployment()
                .name(FlowableConfig.TEST_PROCESS_DEPLOYMENT_NAME)
                .addClasspathResource(FlowableConfig.TEST_PROCESS_RESOURCE)
                .deploy();
        assertThat(deployment.getId()).isNotBlank();
        assertThat(repositoryService.createProcessDefinitionQuery()
                .deploymentId(deployment.getId())
                .processDefinitionKey("rpmTestApproval")
                .count()).isEqualTo(1);

        RuntimeService runtimeService = engine.getRuntimeService();
        TaskService taskService = engine.getTaskService();
        ProcessInstance instance = runtimeService.startProcessInstanceByKey(
                "rpmTestApproval", Map.of("assignee", "integration-user"));
        assertThat(instance.getId()).isNotBlank();

        Task formalReview = taskService.createTaskQuery()
                .processInstanceId(instance.getId())
                .singleResult();
        assertThat(formalReview).isNotNull();
        assertThat(formalReview.getTaskDefinitionKey()).isEqualTo("formalReview");
        assertThat(formalReview.getAssignee()).isEqualTo("integration-user");
        taskService.complete(formalReview.getId());

        Task decision = taskService.createTaskQuery()
                .processInstanceId(instance.getId())
                .singleResult();
        assertThat(decision).isNotNull();
        assertThat(decision.getTaskDefinitionKey()).isEqualTo("decision");
        taskService.complete(decision.getId());

        assertThat(runtimeService.createProcessInstanceQuery()
                .processInstanceId(instance.getId()).count()).isZero();
        HistoryService historyService = engine.getHistoryService();
        assertThat(historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(instance.getId()).finished().count()).isEqualTo(1);
        assertThat(historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(instance.getId()).singleResult().getEndTime()).isNotNull();
    }

    @Test
    @Order(3)
    void runningInstanceUniqueIndexRejectsDuplicateAndAllowsAfterTerminal() throws SQLException {
        Fixture fixture = seedFixture("inst");
        UUID bizId = UUID.randomUUID();
        UUID bizVersionId = UUID.randomUUID();
        UUID firstId = insertInstance(fixture, bizId, bizVersionId, "RUNNING");

        assertSqlState("23505", () -> insertInstance(fixture, bizId, bizVersionId, "RUNNING"));

        // 非 RUNNING 实例不受部分唯一索引约束
        assertThat(insertInstance(fixture, bizId, bizVersionId, "APPROVED")).isNotNull();

        execute("UPDATE wf_instance SET status = 'APPROVED', ended_at = now() WHERE id = ?", firstId);
        assertThat(insertInstance(fixture, bizId, bizVersionId, "RUNNING")).isNotNull();
    }

    @Test
    @Order(4)
    void flowableCompletedIsNotAValidBusinessStatus() throws SQLException {
        Fixture fixture = seedFixture("status");
        UUID instanceId = insertInstance(fixture, UUID.randomUUID(), UUID.randomUUID(), "RUNNING");

        // D-B6-04：任务拆解中的 COMPLETED 是 Flowable 侧流程实例状态，不是 wf_instance 业务状态
        assertSqlState("23514", () ->
                execute("UPDATE wf_instance SET status = 'COMPLETED' WHERE id = ?", instanceId));
        assertThat(readInstanceStatus(instanceId)).isEqualTo("RUNNING");

        assertThat(execute("UPDATE wf_instance SET status = 'APPROVED', ended_at = now() WHERE id = ?",
                instanceId)).isEqualTo(1);
        assertThat(readInstanceStatus(instanceId)).isEqualTo("APPROVED");
    }

    @Test
    @Order(5)
    void taskActionRequestIdIsIdempotentAndNullValuesCoexist() throws SQLException {
        Fixture fixture = seedFixture("task");
        UUID instanceId = insertInstance(fixture, UUID.randomUUID(), UUID.randomUUID(), "RUNNING");

        UUID firstTask = insertTask(instanceId, fixture.initiatorId(), "req-0001");
        assertSqlState("23505", () -> insertTask(instanceId, fixture.initiatorId(), "req-0001"));

        // 部分唯一索引 WHERE action_request_id IS NOT NULL：空值可并存
        assertThat(insertTask(instanceId, fixture.initiatorId(), null)).isNotNull();
        assertThat(insertTask(instanceId, fixture.initiatorId(), null)).isNotNull();
        assertThat(firstTask).isNotNull();
    }

    @Test
    @Order(6)
    void callbackLogRequestIdIsUnique() throws SQLException {
        Fixture fixture = seedFixture("callback");
        UUID instanceId = insertInstance(fixture, UUID.randomUUID(), UUID.randomUUID(), "RUNNING");

        insertCallbackLog(instanceId, "cb-0001");
        assertSqlState("23505", () -> insertCallbackLog(instanceId, "cb-0001"));
    }

    @Test
    @Order(7)
    void delegationRejectsSelfDelegationAndInvalidWindow() throws SQLException {
        Fixture fixture = seedFixture("delegation");

        assertSqlState("23514", () -> insertDelegation(fixture.initiatorId(), fixture.initiatorId(),
                "now()", "now() + interval '1 day'"));
        assertSqlState("23514", () -> insertDelegation(fixture.initiatorId(), fixture.otherUserId(),
                "now()", "now() - interval '1 day'"));
    }

    @Test
    @Order(8)
    void engineRestartsWithExistingSchemaAndDeployment() {
        assertThat(engine).isNotNull();
        engine.close();
        engine = buildEngine();

        assertThat(engine.getRepositoryService().createProcessDefinitionQuery()
                .processDefinitionKey("rpmTestApproval").count()).isEqualTo(1);
        assertThat(engine.getRepositoryService().createDeploymentQuery()
                .deploymentName(FlowableConfig.TEST_PROCESS_DEPLOYMENT_NAME).count()).isEqualTo(1);
    }

    // ---------------------------------------------------------------------
    // Engine
    // ---------------------------------------------------------------------

    private ProcessEngine buildEngine() {
        SpringProcessEngineConfiguration configuration = new SpringProcessEngineConfiguration();
        new FlowableConfig().configure(configuration);
        configuration.setDataSource(dataSource);
        configuration.setTransactionManager(new DataSourceTransactionManager(dataSource));
        configuration.setAsyncExecutorActivate(false);
        configuration.setHistoryLevel(HistoryLevel.FULL);
        return configuration.buildProcessEngine();
    }

    // ---------------------------------------------------------------------
    // Schema assertions
    // ---------------------------------------------------------------------

    private int countFlowableTables() throws SQLException {
        return queryInt("""
                SELECT count(*) FROM information_schema.tables
                 WHERE table_schema = 'flowable' AND table_type = 'BASE TABLE'
                """);
    }

    private boolean existsTable(String table) throws SQLException {
        // PostgreSQL 未加引号的标识符会折叠为小写，information_schema 中存储小写名
        return queryInt("""
                SELECT count(*) FROM information_schema.tables
                 WHERE table_schema = 'flowable' AND table_name = ?
                """, table.toLowerCase(Locale.ROOT)) == 1;
    }

    private boolean existsColumn(String table, String column) throws SQLException {
        return queryInt("""
                SELECT count(*) FROM information_schema.columns
                 WHERE table_schema = 'flowable' AND table_name = ? AND column_name = ?
                """, table.toLowerCase(Locale.ROOT), column.toLowerCase(Locale.ROOT)) == 1;
    }

    private int columnCount(String table) throws SQLException {
        return queryInt("""
                SELECT count(*) FROM information_schema.columns
                 WHERE table_schema = 'flowable' AND table_name = ?
                """, table.toLowerCase(Locale.ROOT));
    }

    private String readProperty(String table, String name) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT VALUE_ FROM flowable." + table + " WHERE NAME_ = ?")) {
            statement.setString(1, name);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).as("%s.%s 不存在", table, name).isTrue();
                return resultSet.getString(1);
            }
        }
    }

    // ---------------------------------------------------------------------
    // Fixtures / JDBC helpers
    // ---------------------------------------------------------------------

    private Fixture seedFixture(String suffix) throws SQLException {
        UUID orgId = UUID.randomUUID();
        UUID initiatorId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        execute("INSERT INTO org_unit (id, code, name, unit_type) VALUES (?, ?, ?, 'DEPARTMENT')",
                orgId, "org-" + suffix, "集成测试部门-" + suffix);
        execute("INSERT INTO iam_user (id, employee_no, display_name, dept_id) VALUES (?, ?, ?, ?)",
                initiatorId, "emp-i-" + suffix, "发起人-" + suffix, orgId);
        execute("INSERT INTO iam_user (id, employee_no, display_name, dept_id) VALUES (?, ?, ?, ?)",
                otherUserId, "emp-o-" + suffix, "受托人-" + suffix, orgId);

        UUID templateId = UUID.randomUUID();
        UUID templateVersionId = UUID.randomUUID();
        execute("INSERT INTO wf_template (id, code, name) VALUES (?, ?, ?)",
                templateId, "tpl-" + suffix, "模板-" + suffix);
        execute("""
                INSERT INTO wf_template_version
                    (id, template_id, version_no, definition, effective_from, status)
                VALUES (?, ?, 1, '{}'::jsonb, now(), 'PUBLISHED')
                """, templateVersionId, templateId);
        return new Fixture(initiatorId, otherUserId, templateVersionId);
    }

    private UUID insertInstance(Fixture fixture, UUID bizId, UUID bizVersionId, String status)
            throws SQLException {
        UUID id = UUID.randomUUID();
        execute("""
                INSERT INTO wf_instance
                    (id, biz_type, biz_id, biz_version_id, template_version_id, initiator_id, status)
                VALUES (?, 'APPLICATION', ?, ?, ?, ?, ?)
                """, id, bizId, bizVersionId, fixture.templateVersionId(), fixture.initiatorId(), status);
        return id;
    }

    private UUID insertTask(UUID instanceId, UUID assigneeId, String actionRequestId) throws SQLException {
        UUID id = UUID.randomUUID();
        execute("""
                INSERT INTO wf_task
                    (id, instance_id, node_key, node_name, assignee_id, action_request_id)
                VALUES (?, ?, 'formalReview', '形式审查', ?, ?)
                """, id, instanceId, assigneeId, actionRequestId);
        return id;
    }

    private void insertCallbackLog(UUID instanceId, String requestId) throws SQLException {
        execute("""
                INSERT INTO wf_callback_log (id, instance_id, request_id, action, payload)
                VALUES (?, ?, ?, 'APPROVED', '{}'::jsonb)
                """, UUID.randomUUID(), instanceId, requestId);
    }

    private void insertDelegation(UUID delegatorId, UUID delegateId, String fromExpr, String toExpr)
            throws SQLException {
        execute("INSERT INTO wf_delegation (id, delegator_id, delegate_id, valid_from, valid_to) "
                        + "VALUES (?, ?, ?, " + fromExpr + ", " + toExpr + ")",
                UUID.randomUUID(), delegatorId, delegateId);
    }

    private String readInstanceStatus(UUID instanceId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT status FROM wf_instance WHERE id = ?")) {
            statement.setObject(1, instanceId);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getString(1);
            }
        }
    }

    private int execute(String sql, Object... parameters) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                statement.setObject(i + 1, parameters[i]);
            }
            return statement.executeUpdate();
        }
    }

    private int queryInt(String sql, Object... parameters) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                statement.setObject(i + 1, parameters[i]);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getInt(1);
            }
        }
    }

    private void assertSqlState(String expectedSqlState, SqlRunnable action) {
        assertThatExceptionOfType(SQLException.class)
                .isThrownBy(action::run)
                .satisfies(exception -> assertThat(exception.getSQLState()).isEqualTo(expectedSqlState));
    }

    /**
     * 允许在断言中执行会抛 {@link SQLException} 的 JDBC 操作。
     */
    @FunctionalInterface
    private interface SqlRunnable {
        void run() throws SQLException;
    }

    /**
     * 工作流约束测试的最小数据夹具。
     *
     * @param initiatorId        发起人用户 ID
     * @param otherUserId        另一用户 ID（用于委托）
     * @param templateVersionId  已发布模板版本 ID
     */
    private record Fixture(UUID initiatorId, UUID otherUserId, UUID templateVersionId) {
    }
}
