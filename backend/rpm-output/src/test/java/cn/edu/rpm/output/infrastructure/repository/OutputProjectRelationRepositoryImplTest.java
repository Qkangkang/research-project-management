package cn.edu.rpm.output.infrastructure.repository;

import cn.edu.rpm.output.infrastructure.entity.OutputProjectRelationEntity;
import cn.edu.rpm.output.infrastructure.mapper.OutputProjectRelationMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link OutputProjectRelationRepositoryImpl} 纯单元测试（T-OUT-005 验收）。
 *
 * <p>覆盖：插入关联成功（ACTIVE）；同成果同项目唯一键口径查询；按项目+状态
 * 查询；撤销后状态 REVOKED 且记录保留（更新而非删除）；有效关联计数。</p>
 */
@ExtendWith(MockitoExtension.class)
class OutputProjectRelationRepositoryImplTest {

    private static final String OUTPUT_ID = "11112222333344445555666677778888";
    private static final String PROJECT_ID = "aaaabbbbccccddddeeeeffff00001111";
    private static final String USER_ID = "99998888777766665555444433332222";

    @Mock
    private OutputProjectRelationMapper relationMapper;

    @SuppressWarnings("unchecked")
    private final ArgumentCaptor<LambdaQueryWrapper<OutputProjectRelationEntity>> wrapperCaptor =
            ArgumentCaptor.forClass(LambdaQueryWrapper.class);

    private OutputProjectRelationRepositoryImpl repository;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, OutputProjectRelationEntity.class);
    }

    @BeforeEach
    void setUp() {
        repository = new OutputProjectRelationRepositoryImpl(relationMapper);
    }

    private static OutputProjectRelationEntity activeRelation() {
        OutputProjectRelationEntity relation = OutputProjectRelationEntity.create(
                OUTPUT_ID, PROJECT_ID, "负责核心算法设计与验证", new BigDecimal("0.60"), USER_ID);
        relation.setId("ccccddddeeeeffff0000111122223333");
        return relation;
    }

    @Test
    void insertStoresActiveRelation() {
        OutputProjectRelationEntity relation = activeRelation();
        when(relationMapper.insert(relation)).thenReturn(1);

        repository.insert(relation);

        verify(relationMapper).insert(relation);
        assertThat(relation.getStatus()).isEqualTo(OutputProjectRelationEntity.STATUS_ACTIVE);
        assertThat(relation.getAttributionRatio()).isEqualByComparingTo("0.60");
    }

    @Test
    void createRejectsBlankContribution() {
        assertThatThrownBy(() -> OutputProjectRelationEntity.create(
                OUTPUT_ID, PROJECT_ID, "  ", null, USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("contributionDesc");
    }

    @Test
    void findByOutputIdAndProjectIdFiltersUniqueKeyColumns() {
        when(relationMapper.selectOne(ArgumentMatchers.<Wrapper<OutputProjectRelationEntity>>any()))
                .thenReturn(activeRelation());

        assertThat(repository.findByOutputIdAndProjectId(OUTPUT_ID, PROJECT_ID)).isPresent();
        verify(relationMapper).selectOne(wrapperCaptor.capture());
        LambdaQueryWrapper<OutputProjectRelationEntity> wrapper = wrapperCaptor.getValue();
        assertThat(wrapper.getSqlSegment()).contains("output_id =", "project_id =");
    }

    @Test
    void findByProjectIdAndStatusFiltersProjectAndStatus() {
        when(relationMapper.selectList(ArgumentMatchers.<Wrapper<OutputProjectRelationEntity>>any()))
                .thenReturn(List.of(activeRelation()));

        List<OutputProjectRelationEntity> relations = repository.findByProjectIdAndStatus(
                PROJECT_ID, OutputProjectRelationEntity.STATUS_ACTIVE);

        assertThat(relations).hasSize(1);
        verify(relationMapper).selectList(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment())
                .contains("project_id =", "status =");
    }

    @Test
    void revokeKeepsRowAndMarksRevoked() {
        OutputProjectRelationEntity relation = activeRelation();
        relation.revoke(USER_ID, OffsetDateTime.parse("2026-11-18T10:00:00+08:00"));
        when(relationMapper.updateById(relation)).thenReturn(1);

        assertThat(repository.update(relation)).isEqualTo(1);

        assertThat(relation.getStatus()).isEqualTo(OutputProjectRelationEntity.STATUS_REVOKED);
        assertThat(relation.getRevokedBy()).isEqualTo(USER_ID);
        assertThat(relation.getRevokedAt()).isNotNull();
    }

    @Test
    void countNormalizesNullToZero() {
        when(relationMapper.selectCount(ArgumentMatchers.<Wrapper<OutputProjectRelationEntity>>any()))
                .thenReturn(null);

        assertThat(repository.countByOutputIdAndStatus(
                OUTPUT_ID, OutputProjectRelationEntity.STATUS_ACTIVE)).isZero();
    }
}
