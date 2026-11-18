package cn.edu.rpm.workflow.infrastructure.repository;

import cn.edu.rpm.workflow.infrastructure.entity.WfTemplateEntity;
import cn.edu.rpm.workflow.infrastructure.mapper.WfTemplateMapper;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link WfTemplateRepositoryImpl} 纯单元测试（T-WF-002 先行测试）。
 *
 * <p>验收锚点：插入模板成功；按 code 唯一查询（uk_wf_template_code）；
 * 按项目类型查询；实体列与 V01 DDL 逐列对齐。</p>
 */
@ExtendWith(MockitoExtension.class)
class WfTemplateRepositoryImplTest {

    private static final String TEMPLATE_ID = "0123456789abcdef0123456789abcdef";

    @Mock
    private WfTemplateMapper wfTemplateMapper;

    @Captor
    private ArgumentCaptor<LambdaQueryWrapper<WfTemplateEntity>> wrapperCaptor;

    @Captor
    private ArgumentCaptor<WfTemplateEntity> entityCaptor;

    private WfTemplateRepositoryImpl repository;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, WfTemplateEntity.class);
    }

    @BeforeEach
    void setUp() {
        repository = new WfTemplateRepositoryImpl(wfTemplateMapper);
    }

    private static WfTemplateEntity newTemplate() {
        WfTemplateEntity template = new WfTemplateEntity();
        template.setCode("WF-APPLY-DEFAULT");
        template.setName("申报默认审批模板");
        template.setProjectType("GENERAL");
        template.setConditionSchema("{\"budget_total\":{\"GTE\":0}}");
        template.setStatus(WfTemplateEntity.STATUS_DRAFT);
        return template;
    }

    @Test
    void insertDelegatesToMapperAndKeepsEveryBusinessColumn() {
        when(wfTemplateMapper.insert(ArgumentMatchers.any(WfTemplateEntity.class))).thenReturn(1);

        repository.insert(newTemplate());

        verify(wfTemplateMapper).insert(entityCaptor.capture());
        WfTemplateEntity saved = entityCaptor.getValue();
        assertThat(saved.getCode()).isEqualTo("WF-APPLY-DEFAULT");
        assertThat(saved.getName()).isEqualTo("申报默认审批模板");
        assertThat(saved.getProjectType()).isEqualTo("GENERAL");
        assertThat(saved.getConditionSchema()).isEqualTo("{\"budget_total\":{\"GTE\":0}}");
        assertThat(saved.getStatus()).isEqualTo(WfTemplateEntity.STATUS_DRAFT);
    }

    @Test
    void findByCodeFiltersUniqueCodeColumn() {
        WfTemplateEntity template = newTemplate();
        template.setId(TEMPLATE_ID);
        when(wfTemplateMapper.selectOne(ArgumentMatchers.<Wrapper<WfTemplateEntity>>any()))
                .thenReturn(template);

        Optional<WfTemplateEntity> result = repository.findByCode("WF-APPLY-DEFAULT");

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(TEMPLATE_ID);
        assertThat(result.get().getCode()).isEqualTo("WF-APPLY-DEFAULT");
        verify(wfTemplateMapper).selectOne(wrapperCaptor.capture());
        LambdaQueryWrapper<WfTemplateEntity> wrapper = wrapperCaptor.getValue();
        assertThat(wrapper.getSqlSegment()).contains("code =");
        assertThat(wrapper.getParamNameValuePairs().values()).contains("WF-APPLY-DEFAULT");
    }

    @Test
    void findByCodeReturnsEmptyWhenNoTemplateMatches() {
        when(wfTemplateMapper.selectOne(ArgumentMatchers.<Wrapper<WfTemplateEntity>>any()))
                .thenReturn(null);

        assertThat(repository.findByCode("NOT-EXISTS")).isEmpty();
    }

    @Test
    void findByIdReturnsEmptyWhenMissing() {
        when(wfTemplateMapper.selectById("ffffffffffffffffffffffffffffffff")).thenReturn(null);

        assertThat(repository.findById("ffffffffffffffffffffffffffffffff")).isEmpty();
    }

    @Test
    void findByProjectTypeFiltersProjectTypeAndReturnsAllMatches() {
        when(wfTemplateMapper.selectList(ArgumentMatchers.<Wrapper<WfTemplateEntity>>any()))
                .thenReturn(List.of(newTemplate(), newTemplate()));

        List<WfTemplateEntity> result = repository.findByProjectType("GENERAL");

        assertThat(result).hasSize(2);
        verify(wfTemplateMapper).selectList(wrapperCaptor.capture());
        LambdaQueryWrapper<WfTemplateEntity> wrapper = wrapperCaptor.getValue();
        assertThat(wrapper.getSqlSegment()).contains("project_type =");
        assertThat(wrapper.getParamNameValuePairs().values()).contains("GENERAL");
    }

    @Test
    void findByProjectTypeReturnsEmptyListWhenNoMatch() {
        when(wfTemplateMapper.selectList(ArgumentMatchers.<Wrapper<WfTemplateEntity>>any()))
                .thenReturn(List.of());

        assertThat(repository.findByProjectType("UNKNOWN")).isEmpty();
    }

    @Test
    void insertUsesSameInstancePassedByCaller() {
        WfTemplateEntity template = newTemplate();
        when(wfTemplateMapper.insert(same(template))).thenReturn(1);

        repository.insert(template);

        verify(wfTemplateMapper).insert(template);
    }

    @Test
    void entityMapsEveryWfTemplateColumn() {
        TableInfo tableInfo = TableInfoHelper.getTableInfo(WfTemplateEntity.class);

        assertThat(tableInfo.getTableName()).isEqualTo("wf_template");
        assertThat(tableInfo.getFieldList())
                .extracting(TableFieldInfo::getColumn)
                .containsExactlyInAnyOrder(
                        "code", "name", "project_type", "condition_schema", "status",
                        "current_version_id", "created_at", "updated_at",
                        "created_by", "updated_by", "version");
        assertThat(tableInfo.getKeyColumn()).isEqualTo("id");
    }
}
