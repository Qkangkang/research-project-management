package cn.edu.rpm.output.infrastructure.repository;

import cn.edu.rpm.output.infrastructure.entity.OutputReviewEntity;
import cn.edu.rpm.output.infrastructure.mapper.OutputReviewMapper;
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

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link OutputReviewRepositoryImpl} 纯单元测试（T-OUT-004 验收）。
 *
 * <p>覆盖：审核记录插入成功；退回意见原样保存；按版本查询（审核时间倒序）；
 * 多版本批量查询禁止空集合 SQL（RULE-DB2）。</p>
 */
@ExtendWith(MockitoExtension.class)
class OutputReviewRepositoryImplTest {

    private static final String VERSION_ID = "aaaabbbbccccddddeeeeffff00001111";
    private static final String REVIEWER_ID = "99998888777766665555444433332222";

    @Mock
    private OutputReviewMapper outputReviewMapper;

    @SuppressWarnings("unchecked")
    private final ArgumentCaptor<LambdaQueryWrapper<OutputReviewEntity>> wrapperCaptor =
            ArgumentCaptor.forClass(LambdaQueryWrapper.class);

    private OutputReviewRepositoryImpl repository;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, OutputReviewEntity.class);
    }

    @BeforeEach
    void setUp() {
        repository = new OutputReviewRepositoryImpl(outputReviewMapper);
    }

    private static OutputReviewEntity review(String decision, String comment) {
        return OutputReviewEntity.create(VERSION_ID, REVIEWER_ID, decision, comment,
                OffsetDateTime.parse("2026-11-18T10:00:00+08:00"));
    }

    @Test
    void insertStoresReturnedCommentVerbatim() {
        OutputReviewEntity review = review(OutputReviewEntity.DECISION_RETURNED, "请补充检索证明");
        when(outputReviewMapper.insert(review)).thenReturn(1);

        repository.insert(review);

        verify(outputReviewMapper).insert(review);
        assertThat(review.getComment()).isEqualTo("请补充检索证明");
        assertThat(review.getDecision()).isEqualTo(OutputReviewEntity.DECISION_RETURNED);
    }

    @Test
    void findByOutputVersionIdOrdersByReviewedAtDescending() {
        when(outputReviewMapper.selectList(ArgumentMatchers.<Wrapper<OutputReviewEntity>>any()))
                .thenReturn(List.of(review(OutputReviewEntity.DECISION_APPROVED, "材料齐全")));

        List<OutputReviewEntity> reviews = repository.findByOutputVersionId(VERSION_ID);

        assertThat(reviews).hasSize(1);
        verify(outputReviewMapper).selectList(wrapperCaptor.capture());
        LambdaQueryWrapper<OutputReviewEntity> wrapper = wrapperCaptor.getValue();
        assertThat(wrapper.getSqlSegment()).contains("output_version_id =");
        assertThat(wrapper.getSqlSegment()).containsIgnoringCase("order by reviewed_at desc");
    }

    @Test
    void findByOutputVersionIdsShortCircuitsForEmptyInput() {
        assertThat(repository.findByOutputVersionIds(List.of())).isEmpty();
        verify(outputReviewMapper, never()).selectList(ArgumentMatchers.any());
    }

    @Test
    void findByOutputVersionIdsUsesSingleInQuery() {
        when(outputReviewMapper.selectList(ArgumentMatchers.<Wrapper<OutputReviewEntity>>any()))
                .thenReturn(List.of(review(OutputReviewEntity.DECISION_APPROVED, null)));

        List<OutputReviewEntity> reviews = repository.findByOutputVersionIds(List.of(VERSION_ID));

        assertThat(reviews).hasSize(1);
        verify(outputReviewMapper).selectList(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment())
                .containsIgnoringCase("output_version_id in");
    }
}
