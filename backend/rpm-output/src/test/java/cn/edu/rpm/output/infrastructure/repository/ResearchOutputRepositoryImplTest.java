package cn.edu.rpm.output.infrastructure.repository;

import cn.edu.rpm.output.infrastructure.entity.ResearchOutputEntity;
import cn.edu.rpm.output.infrastructure.mapper.ResearchOutputMapper;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ResearchOutputRepositoryImpl} 纯单元测试（T-OUT-001 验收）。
 *
 * <p>覆盖：插入成果成功且保持 DRAFT；按 DOI/专利号/标题查询；按
 * {@code owner_id + status} 查询（updated_at 倒序）；同 DOI 可重复保存
 * （无唯一约束，查重只在应用层提示）；批量按主键查询禁止空集合 SQL；
 * 乐观锁更新委托。</p>
 */
@ExtendWith(MockitoExtension.class)
class ResearchOutputRepositoryImplTest {

    private static final String OUTPUT_ID = "11112222333344445555666677778888";
    private static final String OWNER_ID = "99998888777766665555444433332222";
    private static final String DEPT_ID = "aaaabbbbccccddddeeeeffff00001111";

    @Mock
    private ResearchOutputMapper researchOutputMapper;

    @SuppressWarnings("unchecked")
    private final ArgumentCaptor<LambdaQueryWrapper<ResearchOutputEntity>> wrapperCaptor =
            ArgumentCaptor.forClass(LambdaQueryWrapper.class);

    private ResearchOutputRepositoryImpl repository;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ResearchOutputEntity.class);
    }

    @BeforeEach
    void setUp() {
        repository = new ResearchOutputRepositoryImpl(researchOutputMapper);
    }

    private static ResearchOutputEntity output(String doi, String status) {
        ResearchOutputEntity output = ResearchOutputEntity.newDraft(
                ResearchOutputEntity.TYPE_PAPER, "复杂工况下的多模态感知方法", doi, null,
                OWNER_ID, DEPT_ID, LocalDate.of(2026, 10, 15));
        output.setId(OUTPUT_ID);
        if (status != null) {
            output.setStatus(status);
        }
        return output;
    }

    @Test
    void insertDelegatesAndKeepsDraftStatus() {
        ResearchOutputEntity draft = output("10.1000/example.2026.001", null);
        when(researchOutputMapper.insert(ArgumentMatchers.any(ResearchOutputEntity.class))).thenReturn(1);

        repository.insert(draft);

        verify(researchOutputMapper).insert(draft);
        assertThat(draft.getStatus()).isEqualTo(ResearchOutputEntity.STATUS_DRAFT);
        assertThat(draft.getOwnerId()).isEqualTo(OWNER_ID);
    }

    @Test
    void findByDoiFiltersExactDoiAndTrims() {
        when(researchOutputMapper.selectList(ArgumentMatchers.<Wrapper<ResearchOutputEntity>>any()))
                .thenReturn(List.of(output("10.1000/example.2026.001", "DRAFT")));

        List<ResearchOutputEntity> result = repository.findByDoi(" 10.1000/example.2026.001 ");

        assertThat(result).hasSize(1);
        verify(researchOutputMapper).selectList(wrapperCaptor.capture());
        LambdaQueryWrapper<ResearchOutputEntity> wrapper = wrapperCaptor.getValue();
        assertThat(wrapper.getSqlSegment()).contains("doi =");
        assertThat(wrapper.getParamNameValuePairs().values()).contains("10.1000/example.2026.001");
    }

    @Test
    void findByDoiReturnsEmptyWithoutDatabaseCallWhenBlank() {
        assertThat(repository.findByDoi("  ")).isEmpty();
        verify(researchOutputMapper, never()).selectList(ArgumentMatchers.any());
    }

    @Test
    void findByPatentNoReturnsEmptyWithoutDatabaseCallWhenNull() {
        assertThat(repository.findByPatentNo(null)).isEmpty();
        verify(researchOutputMapper, never()).selectList(ArgumentMatchers.any());
    }

    @Test
    void findByTitleUsesExactTitle() {
        when(researchOutputMapper.selectList(ArgumentMatchers.<Wrapper<ResearchOutputEntity>>any()))
                .thenReturn(List.of(output(null, "APPROVED")));

        List<ResearchOutputEntity> result = repository.findByTitle("复杂工况下的多模态感知方法");

        assertThat(result).hasSize(1);
        verify(researchOutputMapper).selectList(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment()).contains("title =");
    }

    @Test
    void findByOwnerIdAndStatusFiltersOwnerAndStatusOrderedByUpdatedAtDesc() {
        when(researchOutputMapper.selectList(ArgumentMatchers.<Wrapper<ResearchOutputEntity>>any()))
                .thenReturn(List.of(output(null, "DRAFT")));

        repository.findByOwnerIdAndStatus(OWNER_ID, ResearchOutputEntity.STATUS_DRAFT);

        verify(researchOutputMapper).selectList(wrapperCaptor.capture());
        LambdaQueryWrapper<ResearchOutputEntity> wrapper = wrapperCaptor.getValue();
        assertThat(wrapper.getSqlSegment()).contains("owner_id =", "status =");
        assertThat(wrapper.getSqlSegment()).containsIgnoringCase("order by updated_at desc");
    }

    @Test
    void findByIdsShortCircuitsForEmptyCollection() {
        assertThat(repository.findByIds(List.of())).isEmpty();
        verify(researchOutputMapper, never()).selectBatchIds(ArgumentMatchers.anyCollection());
    }

    @Test
    void findByIdsDelegatesBatchQuery() {
        ResearchOutputEntity draft = output(null, "DRAFT");
        when(researchOutputMapper.selectBatchIds(List.of(OUTPUT_ID))).thenReturn(List.of(draft));

        assertThat(repository.findByIds(List.of(OUTPUT_ID))).containsExactly(draft);
    }

    @Test
    void updateReturnsAffectedRowsForOptimisticLock() {
        ResearchOutputEntity draft = output(null, "DRAFT");
        when(researchOutputMapper.updateById(draft)).thenReturn(0);

        assertThat(repository.update(draft)).isZero();
    }

    @Test
    void entityMapsEveryResearchOutputColumn() {
        TableInfo tableInfo = TableInfoHelper.getTableInfo(ResearchOutputEntity.class);

        assertThat(tableInfo.getTableName()).isEqualTo("research_output");
        assertThat(tableInfo.getFieldList())
                .extracting(TableFieldInfo::getColumn)
                .containsExactlyInAnyOrder(
                        "output_type", "title", "doi", "patent_no", "owner_id", "dept_id",
                        "completed_at", "status", "current_version_id",
                        "created_at", "updated_at", "created_by", "updated_by", "version");
        assertThat(tableInfo.getKeyColumn()).isEqualTo("id");
    }
}
