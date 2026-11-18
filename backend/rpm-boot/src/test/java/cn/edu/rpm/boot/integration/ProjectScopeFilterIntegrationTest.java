package cn.edu.rpm.boot.integration;

import cn.edu.rpm.project.application.dto.ProjectListItem;
import cn.edu.rpm.project.application.dto.ProjectListQuery;
import cn.edu.rpm.project.infrastructure.mapper.ProjectQueryMapper;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import javax.sql.DataSource;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 行级数据权限（scopeFilter）集成测试。
 *
 * <p>背景：行级过滤是本项目安全生命线（列表/详情/附件/导出同一逻辑），但
 * repository 单测全部 mock 掉了 mapper，动态 SQL 只有「看起来对」。本测试用
 * 真实 PostgreSQL 16 + 真实 Flyway 迁移 + 真实 {@code ProjectQueryMapper.xml}
 * 验证五种数据范围口径：</p>
 * <ul>
 *   <li>全院（scopeAll）：可见全部；</li>
 *   <li>PI 本人（scopePiId）：仅本人负责项目；</li>
 *   <li>部门（scopeDeptIds）：仅授权部门项目；</li>
 *   <li>成员（scopeUserId + project_member）：仅参与中项目（过期成员不可见）；</li>
 *   <li>临时授权（scopeGrantedIds）：指定项目可见。</li>
 * </ul>
 *
 * <p>纯 MyBatis 手工装配（不经 Spring 容器），与 {@link WorkflowIntegrationTest}
 * 同一测试基建风格。</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProjectScopeFilterIntegrationTest {

    private static final String DEPT_CS = "aaaa0001-0000-0000-0000-000000000001";
    private static final String DEPT_PHYS = "aaaa0001-0000-0000-0000-000000000002";
    private static final String USER_ADMIN = "bbbb0001-0000-0000-0000-000000000001";
    private static final String USER_PI_CS = "bbbb0001-0000-0000-0000-000000000002";
    private static final String USER_MEMBER = "bbbb0001-0000-0000-0000-000000000003";
    private static final String USER_MEMBER_EXPIRED = "bbbb0001-0000-0000-0000-000000000004";
    private static final String USER_OUTSIDER = "bbbb0001-0000-0000-0000-000000000005";
    private static final String PRJ_CS_1 = "cccc0001-0000-0000-0000-000000000001";
    private static final String PRJ_CS_2 = "cccc0001-0000-0000-0000-000000000002";
    private static final String PRJ_PHYS_1 = "cccc0001-0000-0000-0000-000000000003";

    private EmbeddedPostgres postgres;

    private SqlSessionFactory sqlSessionFactory;

    @BeforeAll
    void startDatabase() throws Exception {
        postgres = EmbeddedPostgres.builder().setPort(0).start();
        // stringtype=unspecified：与应用数据源同一口径（String 参数按目标列类型推断，
        // uuid 列比较/写入才能直接接收 String 占位符，见 application-dev.yml）
        String baseUrl = postgres.getJdbcUrl("postgres", "postgres");
        org.postgresql.ds.PGSimpleDataSource dataSource = new org.postgresql.ds.PGSimpleDataSource();
        dataSource.setURL(baseUrl + (baseUrl.contains("?") ? "&" : "?") + "stringtype=unspecified");
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        seedData(dataSource);
        sqlSessionFactory = buildSessionFactory(dataSource);
    }

    @AfterAll
    void stopDatabase() throws Exception {
        if (postgres != null) {
            postgres.close();
        }
    }

    @Test
    void scopeAllSeesEveryProject() {
        List<ProjectListItem> items = search(true, null, null, null, null);
        assertThat(items).extracting(ProjectListItem::getProjectNo)
                .containsExactlyInAnyOrder("KY-CS-001", "KY-CS-002", "KY-PHYS-001");
    }

    @Test
    void piScopeSeesOnlyOwnProjects() {
        List<ProjectListItem> items = search(false, USER_PI_CS, USER_PI_CS, null, null);
        assertThat(items).extracting(ProjectListItem::getProjectNo)
                .containsExactlyInAnyOrder("KY-CS-001", "KY-CS-002");
    }

    @Test
    void deptScopeSeesOnlyAuthorizedDeptProjects() {
        List<ProjectListItem> items = search(false, null, USER_OUTSIDER, List.of(DEPT_CS), null);
        assertThat(items).extracting(ProjectListItem::getProjectNo)
                .containsExactlyInAnyOrder("KY-CS-001", "KY-CS-002");
    }

    @Test
    void memberScopeSeesActiveMembershipOnly() {
        // USER_MEMBER 是 KY-CS-001 在期成员、KY-CS-002 已过期成员，且非任何项目 PI
        List<ProjectListItem> items = search(false, null, USER_MEMBER, null, null);
        assertThat(items).extracting(ProjectListItem::getProjectNo)
                .containsExactly("KY-CS-001");
    }

    @Test
    void grantedScopeSeesTemporarilyGrantedProject() {
        List<ProjectListItem> items = search(false, null, USER_OUTSIDER, null, List.of(PRJ_PHYS_1));
        assertThat(items).extracting(ProjectListItem::getProjectNo)
                .containsExactly("KY-PHYS-001");
    }

    @Test
    void expiredMemberSeesNothing() {
        // 成员关系已过期（valid_to < now）：即使仍是 ACTIVE 状态的成员记录也不可见
        List<ProjectListItem> items = search(false, null, USER_MEMBER_EXPIRED, null, null);
        assertThat(items).isEmpty();
    }

    @Test
    void outsiderWithoutAnyScopeSeesNothing() {
        List<ProjectListItem> items = search(false, null, USER_OUTSIDER, null, null);
        assertThat(items).isEmpty();
    }

    // ─────────── 装配与种子数据 ───────────

    private List<ProjectListItem> search(boolean scopeAll, String scopePiId, String scopeUserId,
                                         List<String> deptIds, List<String> grantedIds) {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            ProjectQueryMapper mapper = session.getMapper(ProjectQueryMapper.class);
            return mapper.search(new ProjectListQuery(), scopeAll, scopePiId, scopeUserId,
                    deptIds, grantedIds, 0, 50);
        }
    }

    private SqlSessionFactory buildSessionFactory(DataSource dataSource) throws Exception {
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        // XML 命名空间即接口全限定名，parse() 内部会顺带注册 mapper 接口
        try (InputStream mapperXml = getClass().getResourceAsStream("/mapper/ProjectQueryMapper.xml")) {
            new XMLMapperBuilder(mapperXml, configuration, "mapper/ProjectQueryMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
        org.apache.ibatis.mapping.Environment environment =
                new org.apache.ibatis.mapping.Environment("test",
                        new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory(),
                        dataSource);
        configuration.setEnvironment(environment);
        return new SqlSessionFactoryBuilder().build(configuration);
    }

    private static void seedData(DataSource dataSource) throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            exec(conn, "INSERT INTO org_unit (id, code, name, unit_type, path) VALUES "
                    + "('" + DEPT_CS + "', 'CS', '计算机学院', 'DEPARTMENT', '/ROOT/CS'), "
                    + "('" + DEPT_PHYS + "', 'PHYS', '物理学院', 'DEPARTMENT', '/ROOT/PHYS')");

            exec(conn, "INSERT INTO iam_user (id, employee_no, display_name, dept_id) VALUES "
                    + "('" + USER_ADMIN + "', 'U-ADMIN', '管理员', '" + DEPT_CS + "'), "
                    + "('" + USER_PI_CS + "', 'U-PI', '计算机PI', '" + DEPT_CS + "'), "
                    + "('" + USER_MEMBER + "', 'U-MEM', '成员', '" + DEPT_PHYS + "'), "
                    + "('" + USER_MEMBER_EXPIRED + "', 'U-MEM-X', '过期成员', '" + DEPT_PHYS + "'), "
                    + "('" + USER_OUTSIDER + "', 'U-OUT', '路人', '" + DEPT_PHYS + "')");

            exec(conn, "INSERT INTO project (id, project_no, title, project_type, dept_id, pi_id, status) VALUES "
                    + "('" + PRJ_CS_1 + "', 'KY-CS-001', '面向AI的软件工程研究', 'KEY', '" + DEPT_CS + "', '" + USER_PI_CS + "', 'EXECUTING'), "
                    + "('" + PRJ_CS_2 + "', 'KY-CS-002', '量子机器学习基础研究', 'GENERAL', '" + DEPT_CS + "', '" + USER_PI_CS + "', 'PENDING_START'), "
                    + "('" + PRJ_PHYS_1 + "', 'KY-PHYS-001', '低温等离子体诊断', 'KEY', '" + DEPT_PHYS + "', '" + USER_ADMIN + "', 'EXECUTING')");

            exec(conn, "INSERT INTO project_member (id, project_id, user_id, project_role) VALUES "
                    + "(gen_random_uuid(), '" + PRJ_CS_1 + "', '" + USER_MEMBER + "', 'CORE_MEMBER')");
            // 已过期成员：valid_from/valid_to 均早于当前时间（但记录状态仍为 ACTIVE，验证时间维度过滤）
            exec(conn, "INSERT INTO project_member (id, project_id, user_id, project_role, valid_from, valid_to) VALUES "
                    + "(gen_random_uuid(), '" + PRJ_CS_2 + "', '" + USER_MEMBER_EXPIRED + "', 'CORE_MEMBER', "
                    + "now() - interval '2 days', now() - interval '1 day')");
        }
    }

    private static void exec(Connection conn, String sql) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.executeUpdate();
        }
    }
}
