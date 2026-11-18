package cn.edu.rpm.application.infrastructure.repository;

import cn.edu.rpm.application.infrastructure.entity.ExternalProjectRegistrationEntity;
import cn.edu.rpm.application.infrastructure.mapper.ExternalProjectRegistrationMapper;
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

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ExternalProjectRegistrationRepositoryImpl} 纯单元测试（T-APP-007 先行测试）。
 *
 * <p>验收锚点：插入外部项目登记成功（批复号/合同号、资助方、金额 BigDecimal、
 * 核验人与证据摘要原样落库）；按 {@code application_id} 查询命中
 * （uk_external_registration_application 唯一）；更新携带旧 {@code version} 返回 0
 * 表示冲突。唯一约束与「批复号/合同号至少一个」CHECK 由真实 PostgreSQL 兜底，
 * 属下游业务测试阶段。</p>
 */
@ExtendWith(MockitoExtension.class)
class ExternalProjectRegistrationRepositoryImplTest {

    private static final String REGISTRATION_ID = "abcdef0123456789abcdef0123456789";
    private static final String APPLICATION_ID = "11112222333344445555666677778888";
    private static final String VERIFIED_BY = "99998888777766665555444433332222";

    @Mock
    private ExternalProjectRegistrationMapper externalProjectRegistrationMapper;

    @Captor
    private ArgumentCaptor<LambdaQueryWrapper<ExternalProjectRegistrationEntity>> wrapperCaptor;

    @Captor
    private ArgumentCaptor<ExternalProjectRegistrationEntity> entityCaptor;

    private ExternalProjectRegistrationRepositoryImpl repository;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ExternalProjectRegistrationEntity.class);
    }

    @BeforeEach
    void setUp() {
        repository = new ExternalProjectRegistrationRepositoryImpl(externalProjectRegistrationMapper);
    }

    private static ExternalProjectRegistrationEntity registration() {
        ExternalProjectRegistrationEntity entity = new ExternalProjectRegistrationEntity();
        entity.setApplicationId(APPLICATION_ID);
        entity.setApprovalNo("批复-2026-001");
        entity.setContractNo("HT-2026-0088");
        entity.setFunderName("某研究院");
        entity.setApprovedAmount(new BigDecimal("1200000.00"));
        entity.setVerifiedBy(VERIFIED_BY);
        entity.setVerifiedAt(OffsetDateTime.parse("2026-12-01T09:00:00+08:00"));
        entity.setEvidenceSummary("[\"file-uuid-1\",\"file-uuid-2\"]");
        return entity;
    }

    @Test
    void insertDelegatesToMapperAndKeepsRegistrationEvidence() {
        ExternalProjectRegistrationEntity entity = registration();
        when(externalProjectRegistrationMapper.insert(
                ArgumentMatchers.any(ExternalProjectRegistrationEntity.class))).thenReturn(1);

        repository.insert(entity);

        verify(externalProjectRegistrationMapper).insert(entityCaptor.capture());
        ExternalProjectRegistrationEntity saved = entityCaptor.getValue();
        assertThat(saved.getApplicationId()).isEqualTo(APPLICATION_ID);
        assertThat(saved.getApprovalNo()).isEqualTo("批复-2026-001");
        assertThat(saved.getContractNo()).isEqualTo("HT-2026-0088");
        assertThat(saved.getFunderName()).isEqualTo("某研究院");
        assertThat(saved.getApprovedAmount()).isEqualByComparingTo(new BigDecimal("1200000.00"));
        assertThat(saved.getVerifiedBy()).isEqualTo(VERIFIED_BY);
        assertThat(saved.getEvidenceSummary()).contains("file-uuid-1", "file-uuid-2");
    }

    @Test
    void findByApplicationIdFiltersUniqueApplication() {
        ExternalProjectRegistrationEntity entity = registration();
        entity.setId(REGISTRATION_ID);
        when(externalProjectRegistrationMapper.selectOne(
                ArgumentMatchers.<Wrapper<ExternalProjectRegistrationEntity>>any())).thenReturn(entity);

        Optional<ExternalProjectRegistrationEntity> result =
                repository.findByApplicationId(APPLICATION_ID);

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(REGISTRATION_ID);
        assertThat(result.get().getFunderName()).isEqualTo("某研究院");
        verify(externalProjectRegistrationMapper).selectOne(wrapperCaptor.capture());
        LambdaQueryWrapper<ExternalProjectRegistrationEntity> wrapper = wrapperCaptor.getValue();
        assertThat(wrapper.getSqlSegment()).contains("application_id =");
        assertThat(wrapper.getParamNameValuePairs().values()).contains(APPLICATION_ID);
    }

    @Test
    void findByApplicationIdReturnsEmptyWhenNotRegistered() {
        when(externalProjectRegistrationMapper.selectOne(
                ArgumentMatchers.<Wrapper<ExternalProjectRegistrationEntity>>any())).thenReturn(null);

        assertThat(repository.findByApplicationId(APPLICATION_ID)).isEmpty();
    }

    @Test
    void updateReturnsZeroWhenOptimisticLockVersionIsStale() {
        ExternalProjectRegistrationEntity stale = registration();
        stale.setId(REGISTRATION_ID);
        stale.setVersion(2);
        when(externalProjectRegistrationMapper.updateById(stale)).thenReturn(0);

        assertThat(repository.update(stale)).isZero();
    }

    @Test
    void entityMapsEveryExternalProjectRegistrationColumn() {
        TableInfo tableInfo = TableInfoHelper.getTableInfo(ExternalProjectRegistrationEntity.class);

        assertThat(tableInfo.getTableName()).isEqualTo("external_project_registration");
        assertThat(tableInfo.getFieldList())
                .extracting(TableFieldInfo::getColumn)
                .containsExactlyInAnyOrder(
                        "application_id", "approval_no", "contract_no", "funder_name",
                        "approved_amount", "verified_by", "verified_at", "evidence_summary",
                        "created_at", "updated_at", "version");
        assertThat(tableInfo.getKeyColumn()).isEqualTo("id");
    }
}
