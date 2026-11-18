package cn.edu.rpm.output.infrastructure.repository;

import cn.edu.rpm.output.infrastructure.entity.OutputEvidenceEntity;
import cn.edu.rpm.output.infrastructure.mapper.OutputEvidenceMapper;
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
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link OutputEvidenceRepositoryImpl} 纯单元测试（T-OUT-003 验收）。
 *
 * <p>覆盖：证据插入成功且文件哈希原样保存；按版本查询；空批次不访问数据库；
 * 计数空结果归一为 0；草稿版本证据替换删除。</p>
 */
@ExtendWith(MockitoExtension.class)
class OutputEvidenceRepositoryImplTest {

    private static final String VERSION_ID = "aaaabbbbccccddddeeeeffff00001111";
    private static final String FILE_ID = "11112222333344445555666677778888";
    private static final String HASH = "b".repeat(64);

    @Mock
    private OutputEvidenceMapper outputEvidenceMapper;

    @SuppressWarnings("unchecked")
    private final ArgumentCaptor<LambdaQueryWrapper<OutputEvidenceEntity>> wrapperCaptor =
            ArgumentCaptor.forClass(LambdaQueryWrapper.class);

    private OutputEvidenceRepositoryImpl repository;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, OutputEvidenceEntity.class);
    }

    @BeforeEach
    void setUp() {
        repository = new OutputEvidenceRepositoryImpl(outputEvidenceMapper);
    }

    private static OutputEvidenceEntity evidence() {
        return OutputEvidenceEntity.create(VERSION_ID, FILE_ID, "PROOF", HASH,
                OffsetDateTime.parse("2026-11-18T10:00:00+08:00"));
    }

    @Test
    void insertBatchInsertsEveryEvidenceWithHashPreserved() {
        OutputEvidenceEntity evidence = evidence();
        when(outputEvidenceMapper.insert(evidence)).thenReturn(1);

        repository.insertBatch(List.of(evidence));

        verify(outputEvidenceMapper).insert(evidence);
        assertThat(evidence.getSha256()).isEqualTo(HASH);
        assertThat(evidence.getEvidenceType()).isEqualTo("PROOF");
    }

    @Test
    void insertBatchIgnoresEmptyInput() {
        repository.insertBatch(List.of());

        verify(outputEvidenceMapper, never()).insert(ArgumentMatchers.any(OutputEvidenceEntity.class));
    }

    @Test
    void insertBatchWritesEveryEvidenceExactlyOnce() {
        List<OutputEvidenceEntity> evidences = new ArrayList<>();
        for (int i = 0; i < 501; i++) {
            OutputEvidenceEntity item = evidence();
            item.setId("id-" + i);
            evidences.add(item);
        }

        repository.insertBatch(evidences);

        verify(outputEvidenceMapper, times(501))
                .insert(ArgumentMatchers.any(OutputEvidenceEntity.class));
    }

    @Test
    void findByOutputVersionIdFiltersVersion() {
        when(outputEvidenceMapper.selectList(ArgumentMatchers.<Wrapper<OutputEvidenceEntity>>any()))
                .thenReturn(List.of(evidence()));

        assertThat(repository.findByOutputVersionId(VERSION_ID)).hasSize(1);
        verify(outputEvidenceMapper).selectList(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment()).contains("output_version_id =");
    }

    @Test
    void countNormalizesNullToZero() {
        when(outputEvidenceMapper.selectCount(ArgumentMatchers.<Wrapper<OutputEvidenceEntity>>any()))
                .thenReturn(null);

        assertThat(repository.countByOutputVersionId(VERSION_ID)).isZero();
    }

    @Test
    void deleteByOutputVersionIdDelegates() {
        when(outputEvidenceMapper.delete(ArgumentMatchers.<Wrapper<OutputEvidenceEntity>>any()))
                .thenReturn(2);

        assertThat(repository.deleteByOutputVersionId(VERSION_ID)).isEqualTo(2);
    }
}
