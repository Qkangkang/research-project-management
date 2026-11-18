package cn.edu.rpm.workflow.infrastructure.repository;

import cn.edu.rpm.workflow.infrastructure.entity.WfTemplateVersionEntity;
import cn.edu.rpm.workflow.infrastructure.mapper.WfTemplateVersionMapper;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link WfTemplateVersionRepositoryImpl} 纯单元测试（T-WF-003 先行测试）。
 *
 * <p>验收锚点：插入模板版本成功；按 template_id + version_no 唯一查询
 * （uk_wf_template_version_no）；按 effective_from/effective_to 半开区间查询
 * 指定时点生效版本；无生效版本返回空。</p>
 */
@ExtendWith(MockitoExtension.class)
class WfTemplateVersionRepositoryImplTest {

    private static final String TEMPLATE_ID = "0123456789abcdef0123456789abcdef";
    private static final OffsetDateTime AT = OffsetDateTime.parse("2026-11-17T10:00:00+08:00");

    @Mock
    private WfTemplateVersionMapper wfTemplateVersionMapper;

    @Captor
    private ArgumentCaptor<LambdaQueryWrapper<WfTemplateVersionEntity>> wrapperCaptor;

    @Captor
    private ArgumentCaptor<WfTemplateVersionEntity> entityCaptor;

    private WfTemplateVersionRepositoryImpl repository;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, WfTemplateVersionEntity.class);
    }

    @BeforeEach
    void setUp() {
        repository = new WfTemplateVersionRepositoryImpl(wfTemplateVersionMapper);
    }

    private static WfTemplateVersionEntity version(int versionNo, String status,
                                                   OffsetDateTime from, OffsetDateTime to) {
        WfTemplateVersionEntity entity = new WfTemplateVersionEntity();
        entity.setTemplateId(TEMPLATE_ID);
        entity.setVersionNo(versionNo);
        entity.setDefinition("{\"nodes\":[{\"key\":\"formalReview\"},{\"key\":\"decision\"}]}");
        entity.setConditionExpr("{\"budget_total\":{\"GTE\":0}}");
        entity.setEffectiveFrom(from);
        entity.setEffectiveTo(to);
        entity.setStatus(status);
        entity.setPublishedBy("11111111111111111111111111111111");
        entity.setPublishedAt(from);
        return entity;
    }

    @Test
    void insertDelegatesToMapperAndKeepsDefinitionAndWindow() {
        WfTemplateVersionEntity entity = version(1, WfTemplateVersionEntity.STATUS_PUBLISHED,
                AT.minusDays(1), null);
        when(wfTemplateVersionMapper.insert(ArgumentMatchers.any(WfTemplateVersionEntity.class)))
                .thenReturn(1);

        repository.insert(entity);

        verify(wfTemplateVersionMapper).insert(entityCaptor.capture());
        WfTemplateVersionEntity saved = entityCaptor.getValue();
        assertThat(saved.getTemplateId()).isEqualTo(TEMPLATE_ID);
        assertThat(saved.getVersionNo()).isEqualTo(1);
        assertThat(saved.getDefinition()).contains("formalReview", "decision");
        assertThat(saved.getStatus()).isEqualTo(WfTemplateVersionEntity.STATUS_PUBLISHED);
        assertThat(saved.getEffectiveFrom()).isEqualTo(AT.minusDays(1));
        assertThat(saved.getEffectiveTo()).isNull();
    }

    @Test
    void findByTemplateIdAndVersionNoFiltersBothUniqueColumns() {
        WfTemplateVersionEntity entity = version(2, WfTemplateVersionEntity.STATUS_PUBLISHED,
                AT.minusDays(1), null);
        when(wfTemplateVersionMapper.selectOne(
                ArgumentMatchers.<Wrapper<WfTemplateVersionEntity>>any())).thenReturn(entity);

        Optional<WfTemplateVersionEntity> result =
                repository.findByTemplateIdAndVersionNo(TEMPLATE_ID, 2);

        assertThat(result).isPresent();
        assertThat(result.get().getVersionNo()).isEqualTo(2);
        verify(wfTemplateVersionMapper).selectOne(wrapperCaptor.capture());
        LambdaQueryWrapper<WfTemplateVersionEntity> wrapper = wrapperCaptor.getValue();
        assertThat(wrapper.getSqlSegment()).contains("template_id =", "version_no =");
        assertThat(wrapper.getParamNameValuePairs().values()).contains(TEMPLATE_ID, 2);
    }

    @Test
    void findByTemplateIdAndVersionNoReturnsEmptyWhenVersionMissing() {
        when(wfTemplateVersionMapper.selectOne(
                ArgumentMatchers.<Wrapper<WfTemplateVersionEntity>>any())).thenReturn(null);

        assertThat(repository.findByTemplateIdAndVersionNo(TEMPLATE_ID, 99)).isEmpty();
    }

    @Test
    void findEffectiveByTemplateIdFiltersPublishedStatusAndHalfOpenWindow() {
        WfTemplateVersionEntity effective = version(3, WfTemplateVersionEntity.STATUS_PUBLISHED,
                AT.minusDays(1), AT.plusDays(1));
        when(wfTemplateVersionMapper.selectList(
                ArgumentMatchers.<Wrapper<WfTemplateVersionEntity>>any()))
                .thenReturn(List.of(effective));

        Optional<WfTemplateVersionEntity> result =
                repository.findEffectiveByTemplateId(TEMPLATE_ID, AT);

        assertThat(result).isPresent();
        assertThat(result.get().getVersionNo()).isEqualTo(3);
        verify(wfTemplateVersionMapper).selectList(wrapperCaptor.capture());
        LambdaQueryWrapper<WfTemplateVersionEntity> wrapper = wrapperCaptor.getValue();
        assertThat(wrapper.getSqlSegment())
                .contains("template_id =", "status =", "effective_from <=", "effective_to IS NULL",
                        "effective_to >");
        assertThat(wrapper.getParamNameValuePairs().values())
                .contains(TEMPLATE_ID, WfTemplateVersionEntity.STATUS_PUBLISHED, AT);
        assertThat(wrapper.getSqlSegment()).contains("ORDER BY effective_from DESC");
    }

    @Test
    void findEffectiveByTemplateIdReturnsNewestWhenHistoricalWindowsOverlap() {
        WfTemplateVersionEntity older = version(1, WfTemplateVersionEntity.STATUS_PUBLISHED,
                AT.minusDays(10), null);
        WfTemplateVersionEntity newer = version(2, WfTemplateVersionEntity.STATUS_PUBLISHED,
                AT.minusDays(1), null);
        when(wfTemplateVersionMapper.selectList(
                ArgumentMatchers.<Wrapper<WfTemplateVersionEntity>>any()))
                .thenReturn(List.of(newer, older));

        Optional<WfTemplateVersionEntity> result =
                repository.findEffectiveByTemplateId(TEMPLATE_ID, AT);

        assertThat(result).isPresent();
        assertThat(result.get().getVersionNo()).isEqualTo(2);
    }

    @Test
    void findEffectiveByTemplateIdReturnsEmptyWhenNoPublishedVersionInWindow() {
        when(wfTemplateVersionMapper.selectList(
                ArgumentMatchers.<Wrapper<WfTemplateVersionEntity>>any()))
                .thenReturn(List.of());

        assertThat(repository.findEffectiveByTemplateId(TEMPLATE_ID, AT)).isEmpty();
    }

    @Test
    void entityMapsEveryWfTemplateVersionColumn() {
        TableInfo tableInfo = TableInfoHelper.getTableInfo(WfTemplateVersionEntity.class);

        assertThat(tableInfo.getTableName()).isEqualTo("wf_template_version");
        assertThat(tableInfo.getFieldList())
                .extracting(TableFieldInfo::getColumn)
                .containsExactlyInAnyOrder(
                        "template_id", "version_no", "definition", "condition_expr",
                        "effective_from", "effective_to", "status", "published_by",
                        "published_at", "created_at", "updated_at", "created_by",
                        "updated_by", "version");
        assertThat(tableInfo.getKeyColumn()).isEqualTo("id");
    }
}
