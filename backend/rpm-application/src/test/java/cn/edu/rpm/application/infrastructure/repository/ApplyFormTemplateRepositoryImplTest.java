package cn.edu.rpm.application.infrastructure.repository;

import cn.edu.rpm.application.infrastructure.entity.ApplyFormTemplateEntity;
import cn.edu.rpm.application.infrastructure.mapper.ApplyFormTemplateMapper;
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
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ApplyFormTemplateRepositoryImpl} 纯单元测试（T-APP-002 先行测试）。
 *
 * <p>验收锚点：插入表单模板成功；{@code uk_apply_form_type_version} 的唯一键查询
 * 按 {@code project_type + version_no} 精确命中；按类型查询生效模板时以
 * {@code status = PUBLISHED} 为准，并按 {@code [effective_from, effective_to)} 生效窗口过滤，
 * 多版本命中取 {@code version_no} 最大者（当前有效模板）。</p>
 */
@ExtendWith(MockitoExtension.class)
class ApplyFormTemplateRepositoryImplTest {

    private static final String TEMPLATE_ID = "abcdef0123456789abcdef0123456789";
    private static final String FORM_SCHEMA = "{\"fields\":[{\"key\":\"title\",\"required\":true}]}";

    @Mock
    private ApplyFormTemplateMapper applyFormTemplateMapper;

    @Captor
    private ArgumentCaptor<LambdaQueryWrapper<ApplyFormTemplateEntity>> wrapperCaptor;

    @Captor
    private ArgumentCaptor<ApplyFormTemplateEntity> entityCaptor;

    private ApplyFormTemplateRepositoryImpl repository;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ApplyFormTemplateEntity.class);
    }

    @BeforeEach
    void setUp() {
        repository = new ApplyFormTemplateRepositoryImpl(applyFormTemplateMapper);
    }

    private static ApplyFormTemplateEntity template(int versionNo, String status) {
        ApplyFormTemplateEntity entity = new ApplyFormTemplateEntity();
        entity.setProjectType("YEAR_PLAN");
        entity.setVersionNo(versionNo);
        entity.setFormSchema(FORM_SCHEMA);
        entity.setStatus(status);
        entity.setPublishedBy("11111111111111111111111111111111");
        entity.setPublishedAt(OffsetDateTime.parse("2025-11-01T09:00:00+08:00"));
        return entity;
    }

    @Test
    void insertDelegatesToMapperAndKeepsSchema() {
        ApplyFormTemplateEntity entity = template(1, ApplyFormTemplateEntity.STATUS_PUBLISHED);
        when(applyFormTemplateMapper.insert(ArgumentMatchers.any(ApplyFormTemplateEntity.class)))
                .thenReturn(1);

        repository.insert(entity);

        verify(applyFormTemplateMapper).insert(entityCaptor.capture());
        ApplyFormTemplateEntity saved = entityCaptor.getValue();
        assertThat(saved.getProjectType()).isEqualTo("YEAR_PLAN");
        assertThat(saved.getVersionNo()).isEqualTo(1);
        assertThat(saved.getStatus()).isEqualTo(ApplyFormTemplateEntity.STATUS_PUBLISHED);
        assertThat(saved.getFormSchema()).contains("\"key\":\"title\"").contains("\"required\":true");
    }

    @Test
    void findByProjectTypeAndVersionNoFiltersUniquePair() {
        ApplyFormTemplateEntity published = template(2, ApplyFormTemplateEntity.STATUS_PUBLISHED);
        published.setId(TEMPLATE_ID);
        when(applyFormTemplateMapper.selectOne(ArgumentMatchers.<Wrapper<ApplyFormTemplateEntity>>any()))
                .thenReturn(published);

        Optional<ApplyFormTemplateEntity> result =
                repository.findByProjectTypeAndVersionNo("YEAR_PLAN", 2);

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(TEMPLATE_ID);
        assertThat(result.get().getVersionNo()).isEqualTo(2);
        verify(applyFormTemplateMapper).selectOne(wrapperCaptor.capture());
        LambdaQueryWrapper<ApplyFormTemplateEntity> wrapper = wrapperCaptor.getValue();
        assertThat(wrapper.getSqlSegment()).contains("project_type =", "version_no =");
        assertThat(wrapper.getParamNameValuePairs().values()).contains("YEAR_PLAN", 2);
    }

    @Test
    void findByProjectTypeAndVersionNoReturnsEmptyWhenMissing() {
        when(applyFormTemplateMapper.selectOne(ArgumentMatchers.<Wrapper<ApplyFormTemplateEntity>>any()))
                .thenReturn(null);

        assertThat(repository.findByProjectTypeAndVersionNo("YEAR_PLAN", 99)).isEmpty();
    }

    @Test
    void findEffectiveByProjectTypeFiltersPublishedStatusAndEffectiveWindow() {
        ApplyFormTemplateEntity latest = template(3, ApplyFormTemplateEntity.STATUS_PUBLISHED);
        latest.setId(TEMPLATE_ID);
        ApplyFormTemplateEntity older = template(2, ApplyFormTemplateEntity.STATUS_PUBLISHED);
        older.setId("22222222222222222222222222222222");
        when(applyFormTemplateMapper.selectList(ArgumentMatchers.<Wrapper<ApplyFormTemplateEntity>>any()))
                .thenReturn(List.of(latest, older));

        Optional<ApplyFormTemplateEntity> result = repository.findEffectiveByProjectType("YEAR_PLAN");

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(TEMPLATE_ID);
        assertThat(result.get().getVersionNo()).isEqualTo(3);
        verify(applyFormTemplateMapper).selectList(wrapperCaptor.capture());
        LambdaQueryWrapper<ApplyFormTemplateEntity> wrapper = wrapperCaptor.getValue();
        assertThat(wrapper.getSqlSegment()).contains(
                "project_type =", "status =",
                "effective_from IS NULL", "effective_from <=",
                "effective_to IS NULL", "effective_to >");
        assertThat(wrapper.getSqlSegment()).containsIgnoringCase("order by version_no desc");
        Collection<Object> params = wrapper.getParamNameValuePairs().values();
        assertThat(params).contains("YEAR_PLAN", ApplyFormTemplateEntity.STATUS_PUBLISHED);
        assertThat(params).filteredOn(OffsetDateTime.class::isInstance).hasSize(2);
    }

    @Test
    void findEffectiveByProjectTypeReturnsEmptyWhenNoPublishedTemplate() {
        when(applyFormTemplateMapper.selectList(ArgumentMatchers.<Wrapper<ApplyFormTemplateEntity>>any()))
                .thenReturn(List.of());

        assertThat(repository.findEffectiveByProjectType("EXTERNAL_CONTRACT")).isEmpty();
    }

    @Test
    void updateReturnsZeroWhenOptimisticLockVersionIsStale() {
        ApplyFormTemplateEntity stale = template(2, ApplyFormTemplateEntity.STATUS_RETIRED);
        stale.setId(TEMPLATE_ID);
        stale.setVersion(4);
        when(applyFormTemplateMapper.updateById(stale)).thenReturn(0);

        assertThat(repository.update(stale)).isZero();
    }

    @Test
    void entityMapsEveryApplyFormTemplateColumn() {
        TableInfo tableInfo = TableInfoHelper.getTableInfo(ApplyFormTemplateEntity.class);

        assertThat(tableInfo.getTableName()).isEqualTo("apply_form_template");
        assertThat(tableInfo.getFieldList())
                .extracting(TableFieldInfo::getColumn)
                .containsExactlyInAnyOrder(
                        "project_type", "version_no", "form_schema", "status",
                        "effective_from", "effective_to", "published_by", "published_at",
                        "created_at", "updated_at", "created_by", "updated_by", "version");
        assertThat(tableInfo.getKeyColumn()).isEqualTo("id");
    }
}
