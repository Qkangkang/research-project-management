package cn.edu.rpm.output.infrastructure.repository;

import cn.edu.rpm.output.infrastructure.entity.OutputVersionEntity;
import cn.edu.rpm.output.infrastructure.mapper.OutputVersionMapper;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link OutputVersionRepositoryImpl} 纯单元测试（T-OUT-002 验收）。
 *
 * <p>覆盖：插入版本成功且内容哈希 64 位；按 {@code output_id + version_no}
 * 查询；版本号倒序；最大版本号走 Mapper XML；草稿版本更新委托。
 * {@code uk_output_version_no} 冲突由数据库抛 {@code DuplicateKeyException}，
 * 仓储不做捕获（单测验证异常原样上抛语义由应用层处理）。</p>
 */
@ExtendWith(MockitoExtension.class)
class OutputVersionRepositoryImplTest {

    private static final String OUTPUT_ID = "11112222333344445555666677778888";
    private static final String VERSION_ID = "aaaabbbbccccddddeeeeffff00001111";
    private static final String HASH = "a".repeat(64);

    @Mock
    private OutputVersionMapper outputVersionMapper;

    @SuppressWarnings("unchecked")
    private final ArgumentCaptor<LambdaQueryWrapper<OutputVersionEntity>> wrapperCaptor =
            ArgumentCaptor.forClass(LambdaQueryWrapper.class);

    private OutputVersionRepositoryImpl repository;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, OutputVersionEntity.class);
    }

    @BeforeEach
    void setUp() {
        repository = new OutputVersionRepositoryImpl(outputVersionMapper);
    }

    private static OutputVersionEntity version(int versionNo) {
        OutputVersionEntity version = OutputVersionEntity.create(OUTPUT_ID, versionNo,
                "{\"journal\":\"某学报\"}", HASH, "99998888777766665555444433332222",
                OffsetDateTime.parse("2026-11-18T10:00:00+08:00"));
        version.setId(VERSION_ID);
        return version;
    }

    @Test
    void insertStoresVersionWithValidHash() {
        OutputVersionEntity version = version(1);
        when(outputVersionMapper.insert(version)).thenReturn(1);

        repository.insert(version);

        verify(outputVersionMapper).insert(version);
        assertThat(version.getContentHash()).hasSize(64);
        assertThat(version.getVersionNo()).isEqualTo(1);
    }

    @Test
    void entityRejectsMalformedContentHash() {
        assertThatThrownBy(() -> OutputVersionEntity.create(OUTPUT_ID, 1, "{}", "not-a-hash",
                "99998888777766665555444433332222", OffsetDateTime.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void findByOutputIdAndVersionNoFiltersBothColumns() {
        when(outputVersionMapper.selectOne(ArgumentMatchers.<Wrapper<OutputVersionEntity>>any()))
                .thenReturn(version(2));

        assertThat(repository.findByOutputIdAndVersionNo(OUTPUT_ID, 2)).isPresent();
        verify(outputVersionMapper).selectOne(wrapperCaptor.capture());
        LambdaQueryWrapper<OutputVersionEntity> wrapper = wrapperCaptor.getValue();
        assertThat(wrapper.getSqlSegment()).contains("output_id =", "version_no =");
    }

    @Test
    void findByOutputIdOrdersByVersionNoDescending() {
        when(outputVersionMapper.selectList(ArgumentMatchers.<Wrapper<OutputVersionEntity>>any()))
                .thenReturn(List.of(version(2), version(1)));

        List<OutputVersionEntity> versions = repository.findByOutputId(OUTPUT_ID);

        assertThat(versions).extracting(OutputVersionEntity::getVersionNo).containsExactly(2, 1);
        verify(outputVersionMapper).selectList(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment())
                .containsIgnoringCase("order by version_no desc");
    }

    @Test
    void findMaxVersionNoDelegatesToMapperXml() {
        when(outputVersionMapper.selectMaxVersionNo(OUTPUT_ID)).thenReturn(3);

        assertThat(repository.findMaxVersionNo(OUTPUT_ID)).isEqualTo(3);
        verify(outputVersionMapper).selectMaxVersionNo(OUTPUT_ID);
    }

    @Test
    void updateDelegatesDraftMetadataChange() {
        OutputVersionEntity draft = version(1);
        when(outputVersionMapper.updateById(draft)).thenReturn(1);

        assertThat(repository.update(draft)).isEqualTo(1);
    }
}
