package cn.edu.rpm.project.infrastructure.repository;

import cn.edu.rpm.project.infrastructure.entity.ChangeRequestVersionEntity;
import cn.edu.rpm.project.infrastructure.mapper.ChangeRequestVersionMapper;
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
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ChangeRequestVersionRepositoryImpl} 纯单元测试（T-PRJ-008 先行测试）。
 *
 * <p>版本行不可变：DDL 只有 {@code created_at}，无审计/乐观锁字段。</p>
 */
@ExtendWith(MockitoExtension.class)
class ChangeRequestVersionRepositoryImplTest {

    private static final String CHANGE_ID = "cccccccccccccccccccccccccccccccc";

    @Mock
    private ChangeRequestVersionMapper changeRequestVersionMapper;

    @Captor
    private ArgumentCaptor<LambdaQueryWrapper<ChangeRequestVersionEntity>> wrapperCaptor;

    private ChangeRequestVersionRepositoryImpl repository;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ChangeRequestVersionEntity.class);
    }

    @BeforeEach
    void setUp() {
        repository = new ChangeRequestVersionRepositoryImpl(changeRequestVersionMapper);
    }

    @Test
    void insertKeepsPayloadAndBaselineReferences() {
        ChangeRequestVersionEntity version = new ChangeRequestVersionEntity();
        version.setChangeRequestId(CHANGE_ID);
        version.setVersionNo(1);
        version.setPayload("{\"newEndDate\":\"2029-11-30\"}");
        version.setReason("研究周期调整");

        repository.insert(version);

        verify(changeRequestVersionMapper).insert(version);
        assertThat(version.getVersionNo()).isEqualTo(1);
    }

    @Test
    void findMaxVersionNoReturnsZeroForNoVersion() {
        when(changeRequestVersionMapper.selectList(
                ArgumentMatchers.<Wrapper<ChangeRequestVersionEntity>>any()))
                .thenReturn(List.of());

        assertThat(repository.findMaxVersionNo(CHANGE_ID)).isZero();
    }

    @Test
    void findMaxVersionNoPicksLargest() {
        ChangeRequestVersionEntity first = new ChangeRequestVersionEntity();
        first.setVersionNo(1);
        ChangeRequestVersionEntity third = new ChangeRequestVersionEntity();
        third.setVersionNo(3);
        when(changeRequestVersionMapper.selectList(
                ArgumentMatchers.<Wrapper<ChangeRequestVersionEntity>>any()))
                .thenReturn(List.of(first, third));

        assertThat(repository.findMaxVersionNo(CHANGE_ID)).isEqualTo(3);
    }

    @Test
    void findByChangeRequestIdOrdersDesc() {
        when(changeRequestVersionMapper.selectList(
                ArgumentMatchers.<Wrapper<ChangeRequestVersionEntity>>any()))
                .thenReturn(List.of());

        repository.findByChangeRequestId(CHANGE_ID);

        verify(changeRequestVersionMapper).selectList(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment())
                .contains("change_request_id =")
                .containsIgnoringCase("order by version_no desc");
    }

    @Test
    void attachNewBaselineStoresApprovedBaselineReference() {
        ChangeRequestVersionEntity version = new ChangeRequestVersionEntity();

        version.attachNewBaseline("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");

        assertThat(version.getNewBaselineId()).isEqualTo("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
    }
}
