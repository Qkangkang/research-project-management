package cn.edu.rpm.application.infrastructure.repository;

import cn.edu.rpm.application.infrastructure.entity.ApplicationEntity;
import cn.edu.rpm.application.infrastructure.mapper.ApplicationMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ApplicationRepositoryImpl} 纯单元测试（T-APP-003 先行测试）。
 *
 * <p>验收锚点：插入申报成功（草稿 status=DRAFT）；{@code uk_application_no} 查询按
 * {@code application_no} 精确命中；按 {@code applicant_id + status} 查询我的申报
 * （与 idx_application_applicant_status 对齐，按 updated_at 倒序）；
 * {@code submit} 链路的 {@code SELECT ... FOR UPDATE} 悲观锁由 Mapper XML 承载，
 * 单测断言 XML 确实包含锁子句、Repository 委托到专用方法。</p>
 *
 * <p>真实行锁的并发行为（两事务竞争）依赖 PostgreSQL，归下游业务测试阶段（TC-APP-009）。</p>
 */
@ExtendWith(MockitoExtension.class)
class ApplicationRepositoryImplTest {

    private static final String APPLICATION_ID = "11112222333344445555666677778888";
    private static final String APPLICANT_ID = "99998888777766665555444433332222";
    private static final String BATCH_ID = "aaaabbbbccccddddeeeeffff00001111";

    @Mock
    private ApplicationMapper applicationMapper;

    @Captor
    private ArgumentCaptor<LambdaQueryWrapper<ApplicationEntity>> wrapperCaptor;

    @Captor
    private ArgumentCaptor<ApplicationEntity> entityCaptor;

    private ApplicationRepositoryImpl repository;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ApplicationEntity.class);
    }

    @BeforeEach
    void setUp() {
        repository = new ApplicationRepositoryImpl(applicationMapper);
    }

    private static ApplicationEntity application(String status) {
        ApplicationEntity entity = new ApplicationEntity();
        entity.setApplicationNo("APP-2026-000123");
        entity.setBatchId(BATCH_ID);
        entity.setProjectType("YEAR_PLAN");
        entity.setApplicantId(APPLICANT_ID);
        entity.setDeptId("dddddddddddddddddddddddddddddddd");
        entity.setTitle("面向复杂工况的智能感知方法研究");
        entity.setSourceType(ApplicationEntity.SOURCE_INTERNAL);
        entity.setStatus(status);
        return entity;
    }

    @Test
    void insertDelegatesToMapperAndKeepsDraftIdentity() {
        ApplicationEntity draft = application(ApplicationEntity.STATUS_DRAFT);
        when(applicationMapper.insert(ArgumentMatchers.any(ApplicationEntity.class))).thenReturn(1);

        repository.insert(draft);

        verify(applicationMapper).insert(entityCaptor.capture());
        ApplicationEntity saved = entityCaptor.getValue();
        assertThat(saved.getApplicationNo()).isEqualTo("APP-2026-000123");
        assertThat(saved.getBatchId()).isEqualTo(BATCH_ID);
        assertThat(saved.getApplicantId()).isEqualTo(APPLICANT_ID);
        assertThat(saved.getProjectType()).isEqualTo("YEAR_PLAN");
        assertThat(saved.getSourceType()).isEqualTo(ApplicationEntity.SOURCE_INTERNAL);
        assertThat(saved.getStatus()).isEqualTo(ApplicationEntity.STATUS_DRAFT);
        assertThat(saved.getCurrentVersionId()).isNull();
    }

    @Test
    void findByApplicationNoFiltersUniqueApplicationNo() {
        ApplicationEntity draft = application(ApplicationEntity.STATUS_DRAFT);
        draft.setId(APPLICATION_ID);
        when(applicationMapper.selectOne(ArgumentMatchers.<Wrapper<ApplicationEntity>>any()))
                .thenReturn(draft);

        Optional<ApplicationEntity> result = repository.findByApplicationNo("APP-2026-000123");

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(APPLICATION_ID);
        verify(applicationMapper).selectOne(wrapperCaptor.capture());
        LambdaQueryWrapper<ApplicationEntity> wrapper = wrapperCaptor.getValue();
        assertThat(wrapper.getSqlSegment()).contains("application_no =");
        assertThat(wrapper.getParamNameValuePairs().values()).contains("APP-2026-000123");
    }

    @Test
    void findByApplicationNoReturnsEmptyWhenMissing() {
        when(applicationMapper.selectOne(ArgumentMatchers.<Wrapper<ApplicationEntity>>any()))
                .thenReturn(null);

        assertThat(repository.findByApplicationNo("APP-2026-999999")).isEmpty();
    }

    @Test
    void findByApplicantIdAndStatusFiltersOwnerAndStatusOrderedByUpdatedAt() {
        ApplicationEntity draft = application(ApplicationEntity.STATUS_DRAFT);
        draft.setId(APPLICATION_ID);
        when(applicationMapper.selectList(ArgumentMatchers.<Wrapper<ApplicationEntity>>any()))
                .thenReturn(List.of(draft));

        List<ApplicationEntity> result =
                repository.findByApplicantIdAndStatus(APPLICANT_ID, ApplicationEntity.STATUS_DRAFT);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getApplicantId()).isEqualTo(APPLICANT_ID);
        verify(applicationMapper).selectList(wrapperCaptor.capture());
        LambdaQueryWrapper<ApplicationEntity> wrapper = wrapperCaptor.getValue();
        assertThat(wrapper.getSqlSegment()).contains("applicant_id =", "status =");
        assertThat(wrapper.getSqlSegment()).containsIgnoringCase("order by updated_at desc");
        assertThat(wrapper.getParamNameValuePairs().values())
                .contains(APPLICANT_ID, ApplicationEntity.STATUS_DRAFT);
    }

    @Test
    void findByApplicantIdAndStatusReturnsEmptyWhenNoMatch() {
        when(applicationMapper.selectList(ArgumentMatchers.<Wrapper<ApplicationEntity>>any()))
                .thenReturn(List.of());

        assertThat(repository.findByApplicantIdAndStatus(APPLICANT_ID, ApplicationEntity.STATUS_APPROVED))
                .isEmpty();
    }

    @Test
    void selectForUpdateDelegatesToLockingMapperMethod() {
        ApplicationEntity locked = application(ApplicationEntity.STATUS_DRAFT);
        locked.setId(APPLICATION_ID);
        when(applicationMapper.selectByIdForUpdate(APPLICATION_ID)).thenReturn(locked);

        Optional<ApplicationEntity> result = repository.selectForUpdate(APPLICATION_ID);

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(APPLICATION_ID);
        assertThat(result.get().getStatus()).isEqualTo(ApplicationEntity.STATUS_DRAFT);
        verify(applicationMapper).selectByIdForUpdate(APPLICATION_ID);
    }

    @Test
    void selectForUpdateReturnsEmptyWhenRowVanished() {
        when(applicationMapper.selectByIdForUpdate("ffffffffffffffffffffffffffffffff")).thenReturn(null);

        assertThat(repository.selectForUpdate("ffffffffffffffffffffffffffffffff")).isEmpty();
    }

    @Test
    void mapperXmlCarriesForUpdatePessimisticLock() throws IOException {
        String xml;
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream("mapper/ApplicationMapper.xml")) {
            assertThat(in).as("Mapper XML 必须随模块打包").isNotNull();
            xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(xml).contains("selectByIdForUpdate");
        assertThat(xml).containsIgnoringCase("from application");
        assertThat(xml).containsIgnoringCase("for update");
    }

    @Test
    void updateReturnsZeroWhenOptimisticLockVersionIsStale() {
        ApplicationEntity stale = application(ApplicationEntity.STATUS_IN_REVIEW);
        stale.setId(APPLICATION_ID);
        stale.setVersion(5);
        when(applicationMapper.updateById(stale)).thenReturn(0);

        assertThat(repository.update(stale)).isZero();
    }

    @Test
    void entityMapsEveryApplicationColumn() {
        TableInfo tableInfo = TableInfoHelper.getTableInfo(ApplicationEntity.class);

        assertThat(tableInfo.getTableName()).isEqualTo("application");
        assertThat(tableInfo.getFieldList())
                .extracting(TableFieldInfo::getColumn)
                .containsExactlyInAnyOrder(
                        "application_no", "batch_id", "project_type", "applicant_id", "dept_id",
                        "title", "source_type", "status", "current_version_id",
                        "created_at", "updated_at", "created_by", "updated_by", "version");
        assertThat(tableInfo.getKeyColumn()).isEqualTo("id");
    }
}
