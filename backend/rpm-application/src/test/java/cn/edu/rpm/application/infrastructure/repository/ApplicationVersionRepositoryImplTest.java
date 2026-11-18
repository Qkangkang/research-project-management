package cn.edu.rpm.application.infrastructure.repository;

import cn.edu.rpm.application.infrastructure.entity.ApplicationVersionEntity;
import cn.edu.rpm.application.infrastructure.mapper.ApplicationVersionMapper;
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

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ApplicationVersionRepositoryImpl} 纯单元测试（T-APP-004 先行测试）。
 *
 * <p>验收锚点：插入版本成功且 jsonb 表单与 SHA-256 哈希原样落库；
 * {@code uk_application_version_no (application_id, version_no)} 按二元组精确命中；
 * {@code submit_idempotency_key} 唯一查询用于提交幂等（TC-APP-008：同一幂等键
 * 返回已有版本，不生成第二行）；更新携带旧 {@code version} 返回 0 表示冲突。</p>
 */
@ExtendWith(MockitoExtension.class)
class ApplicationVersionRepositoryImplTest {

    private static final String VERSION_ID = "aaaabbbbccccddddeeeeffff11112222";
    private static final String APPLICATION_ID = "11112222333344445555666677778888";
    private static final String IDEMPOTENCY_KEY = "submit-key-2026-0001";
    private static final String CONTENT_HASH =
            "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08";

    @Mock
    private ApplicationVersionMapper applicationVersionMapper;

    @Captor
    private ArgumentCaptor<LambdaQueryWrapper<ApplicationVersionEntity>> wrapperCaptor;

    @Captor
    private ArgumentCaptor<ApplicationVersionEntity> entityCaptor;

    private ApplicationVersionRepositoryImpl repository;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ApplicationVersionEntity.class);
    }

    @BeforeEach
    void setUp() {
        repository = new ApplicationVersionRepositoryImpl(applicationVersionMapper);
    }

    private static ApplicationVersionEntity version(int versionNo, String status) {
        ApplicationVersionEntity entity = new ApplicationVersionEntity();
        entity.setApplicationId(APPLICATION_ID);
        entity.setVersionNo(versionNo);
        entity.setFormData("{\"title\":\"智能感知方法研究\",\"budget_total\":1200000.00}");
        entity.setContentHash(CONTENT_HASH);
        entity.setStatus(status);
        entity.setSubmittedBy("99998888777766665555444433332222");
        entity.setSubmittedAt(OffsetDateTime.parse("2026-02-01T10:15:30+08:00"));
        entity.setSubmitIdempotencyKey(IDEMPOTENCY_KEY);
        return entity;
    }

    @Test
    void insertDelegatesToMapperAndKeepsImmutableContent() {
        ApplicationVersionEntity submitted =
                version(1, ApplicationVersionEntity.STATUS_SUBMITTED);
        when(applicationVersionMapper.insert(ArgumentMatchers.any(ApplicationVersionEntity.class)))
                .thenReturn(1);

        repository.insert(submitted);

        verify(applicationVersionMapper).insert(entityCaptor.capture());
        ApplicationVersionEntity saved = entityCaptor.getValue();
        assertThat(saved.getApplicationId()).isEqualTo(APPLICATION_ID);
        assertThat(saved.getVersionNo()).isEqualTo(1);
        assertThat(saved.getStatus()).isEqualTo(ApplicationVersionEntity.STATUS_SUBMITTED);
        assertThat(saved.getFormData()).contains("\"budget_total\":1200000.00");
        assertThat(saved.getContentHash()).isEqualTo(CONTENT_HASH).hasSize(64);
        assertThat(saved.getSubmitIdempotencyKey()).isEqualTo(IDEMPOTENCY_KEY);
    }

    @Test
    void findByApplicationIdAndVersionNoFiltersUniquePair() {
        ApplicationVersionEntity v4 = version(4, ApplicationVersionEntity.STATUS_SUBMITTED);
        v4.setId(VERSION_ID);
        when(applicationVersionMapper.selectOne(
                ArgumentMatchers.<Wrapper<ApplicationVersionEntity>>any())).thenReturn(v4);

        Optional<ApplicationVersionEntity> result =
                repository.findByApplicationIdAndVersionNo(APPLICATION_ID, 4);

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(VERSION_ID);
        assertThat(result.get().getVersionNo()).isEqualTo(4);
        verify(applicationVersionMapper).selectOne(wrapperCaptor.capture());
        LambdaQueryWrapper<ApplicationVersionEntity> wrapper = wrapperCaptor.getValue();
        assertThat(wrapper.getSqlSegment()).contains("application_id =", "version_no =");
        assertThat(wrapper.getParamNameValuePairs().values()).contains(APPLICATION_ID, 4);
    }

    @Test
    void findByApplicationIdAndVersionNoReturnsEmptyWhenMissing() {
        when(applicationVersionMapper.selectOne(
                ArgumentMatchers.<Wrapper<ApplicationVersionEntity>>any())).thenReturn(null);

        assertThat(repository.findByApplicationIdAndVersionNo(APPLICATION_ID, 99)).isEmpty();
    }

    @Test
    void findByIdempotencyKeyReturnsFirstSubmittedVersion() {
        ApplicationVersionEntity first = version(4, ApplicationVersionEntity.STATUS_SUBMITTED);
        first.setId(VERSION_ID);
        when(applicationVersionMapper.selectOne(
                ArgumentMatchers.<Wrapper<ApplicationVersionEntity>>any())).thenReturn(first);

        Optional<ApplicationVersionEntity> result = repository.findByIdempotencyKey(IDEMPOTENCY_KEY);

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(VERSION_ID);
        assertThat(result.get().getSubmitIdempotencyKey()).isEqualTo(IDEMPOTENCY_KEY);
        verify(applicationVersionMapper).selectOne(wrapperCaptor.capture());
        LambdaQueryWrapper<ApplicationVersionEntity> wrapper = wrapperCaptor.getValue();
        assertThat(wrapper.getSqlSegment()).contains("submit_idempotency_key =");
        assertThat(wrapper.getParamNameValuePairs().values()).contains(IDEMPOTENCY_KEY);
    }

    @Test
    void findByIdempotencyKeyReturnsEmptyWhenKeyUnseen() {
        when(applicationVersionMapper.selectOne(
                ArgumentMatchers.<Wrapper<ApplicationVersionEntity>>any())).thenReturn(null);

        assertThat(repository.findByIdempotencyKey("never-used-key")).isEmpty();
    }

    @Test
    void updateReturnsZeroWhenOptimisticLockVersionIsStale() {
        ApplicationVersionEntity stale = version(2, ApplicationVersionEntity.STATUS_APPROVED);
        stale.setId(VERSION_ID);
        stale.setVersion(3);
        when(applicationVersionMapper.updateById(stale)).thenReturn(0);

        assertThat(repository.update(stale)).isZero();
    }

    @Test
    void entityMapsEveryApplicationVersionColumn() {
        TableInfo tableInfo = TableInfoHelper.getTableInfo(ApplicationVersionEntity.class);

        assertThat(tableInfo.getTableName()).isEqualTo("application_version");
        assertThat(tableInfo.getFieldList())
                .extracting(TableFieldInfo::getColumn)
                .containsExactlyInAnyOrder(
                        "application_id", "version_no", "form_data", "content_hash", "status",
                        "submitted_by", "submitted_at", "submit_idempotency_key",
                        "created_at", "updated_at", "created_by", "updated_by", "version");
        assertThat(tableInfo.getKeyColumn()).isEqualTo("id");
    }
}
