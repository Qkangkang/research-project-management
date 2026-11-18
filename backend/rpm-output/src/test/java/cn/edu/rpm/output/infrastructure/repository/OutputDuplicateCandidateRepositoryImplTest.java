package cn.edu.rpm.output.infrastructure.repository;

import cn.edu.rpm.output.infrastructure.entity.OutputDuplicateCandidateEntity;
import cn.edu.rpm.output.infrastructure.mapper.OutputDuplicateCandidateMapper;
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
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link OutputDuplicateCandidateRepositoryImpl} 纯单元测试（T-OUT-006 验收）。
 *
 * <p>覆盖：插入候选成功；按成果+状态查询待确认候选（score 倒序）；
 * 唯一键冲突按幂等返回 {@code false}；同成果自候选被实体拒绝；
 * 候选查询不删除、不合并任何成果（BR-21）。</p>
 */
@ExtendWith(MockitoExtension.class)
class OutputDuplicateCandidateRepositoryImplTest {

    private static final String OUTPUT_ID = "11112222333344445555666677778888";
    private static final String CANDIDATE_ID = "aaaabbbbccccddddeeeeffff00001111";

    @Mock
    private OutputDuplicateCandidateMapper candidateMapper;

    @SuppressWarnings("unchecked")
    private final ArgumentCaptor<LambdaQueryWrapper<OutputDuplicateCandidateEntity>> wrapperCaptor =
            ArgumentCaptor.forClass(LambdaQueryWrapper.class);

    private OutputDuplicateCandidateRepositoryImpl repository;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, OutputDuplicateCandidateEntity.class);
    }

    @BeforeEach
    void setUp() {
        repository = new OutputDuplicateCandidateRepositoryImpl(candidateMapper);
    }

    private static OutputDuplicateCandidateEntity candidate() {
        OutputDuplicateCandidateEntity candidate = OutputDuplicateCandidateEntity.create(
                OUTPUT_ID, CANDIDATE_ID, OutputDuplicateCandidateEntity.MATCH_PATENT,
                new BigDecimal("1.00"), OffsetDateTime.parse("2026-11-18T10:00:00+08:00"));
        candidate.setId("ccccddddeeeeffff0000111122223333");
        return candidate;
    }

    @Test
    void insertIfAbsentReturnsTrueWhenInserted() {
        OutputDuplicateCandidateEntity candidate = candidate();
        when(candidateMapper.insert(candidate)).thenReturn(1);

        assertThat(repository.insertIfAbsent(candidate)).isTrue();
        assertThat(candidate.getStatus()).isEqualTo(OutputDuplicateCandidateEntity.STATUS_OPEN);
        assertThat(candidate.getScore()).isEqualByComparingTo("1.00");
    }

    @Test
    void insertIfAbsentSwallowsDuplicateKeyAndReturnsFalse() {
        OutputDuplicateCandidateEntity candidate = candidate();
        when(candidateMapper.insert(candidate))
                .thenThrow(new DuplicateKeyException("uk_output_duplicate"));

        assertThat(repository.insertIfAbsent(candidate)).isFalse();
    }

    @Test
    void findByOutputIdAndStatusFiltersOpenCandidates() {
        when(candidateMapper.selectList(ArgumentMatchers.<Wrapper<OutputDuplicateCandidateEntity>>any()))
                .thenReturn(List.of(candidate()));

        List<OutputDuplicateCandidateEntity> pending = repository.findByOutputIdAndStatus(
                OUTPUT_ID, OutputDuplicateCandidateEntity.STATUS_OPEN);

        assertThat(pending).hasSize(1);
        verify(candidateMapper).selectList(wrapperCaptor.capture());
        LambdaQueryWrapper<OutputDuplicateCandidateEntity> wrapper = wrapperCaptor.getValue();
        assertThat(wrapper.getSqlSegment()).contains("output_id =", "status =");
        assertThat(wrapper.getSqlSegment()).containsIgnoringCase("order by score desc");
    }

    @Test
    void entityRejectsSelfCandidate() {
        assertThatThrownBy(() -> OutputDuplicateCandidateEntity.create(
                OUTPUT_ID, OUTPUT_ID, OutputDuplicateCandidateEntity.MATCH_DOI,
                BigDecimal.ONE, OffsetDateTime.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("自身");
    }
}
