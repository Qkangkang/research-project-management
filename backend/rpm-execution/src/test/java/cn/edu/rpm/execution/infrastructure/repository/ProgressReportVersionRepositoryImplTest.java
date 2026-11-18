package cn.edu.rpm.execution.infrastructure.repository;

import cn.edu.rpm.execution.infrastructure.entity.ProgressReportVersionEntity;
import cn.edu.rpm.execution.infrastructure.mapper.ProgressReportVersionMapper;
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
 * {@link ProgressReportVersionRepositoryImpl} 纯单元测试（T-EXEC-002 先行测试）。
 *
 * <p>不可变版本：只有 {@code created_at}，无审计/乐观锁字段；迟报事实
 * {@code late_flag} 在版本行（V05 修复 #23）。</p>
 */
@ExtendWith(MockitoExtension.class)
class ProgressReportVersionRepositoryImplTest {

    private static final String REPORT_ID = "rrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrr";

    @Mock
    private ProgressReportVersionMapper progressReportVersionMapper;

    @Captor
    private ArgumentCaptor<LambdaQueryWrapper<ProgressReportVersionEntity>> wrapperCaptor;

    private ProgressReportVersionRepositoryImpl repository;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ProgressReportVersionEntity.class);
    }

    @BeforeEach
    void setUp() {
        repository = new ProgressReportVersionRepositoryImpl(progressReportVersionMapper);
    }

    @Test
    void insertKeepsContentHashAndLateFlag() {
        ProgressReportVersionEntity version = new ProgressReportVersionEntity();
        version.setReportId(REPORT_ID);
        version.setVersionNo(1);
        version.setContent("{}");
        version.setContentHash("b".repeat(64));
        version.setLateFlag(false);
        version.setDecision(ProgressReportVersionEntity.DECISION_PENDING);

        repository.insert(version);

        verify(progressReportVersionMapper).insert(version);
        assertThat(version.getLateFlag()).isFalse();
    }

    @Test
    void findMaxVersionNoReturnsZeroWhenNoVersion() {
        when(progressReportVersionMapper.selectList(
                ArgumentMatchers.<Wrapper<ProgressReportVersionEntity>>any()))
                .thenReturn(List.of());

        assertThat(repository.findMaxVersionNo(REPORT_ID)).isZero();
    }

    @Test
    void findByReportIdOrdersByVersionDesc() {
        when(progressReportVersionMapper.selectList(
                ArgumentMatchers.<Wrapper<ProgressReportVersionEntity>>any()))
                .thenReturn(List.of());

        repository.findByReportId(REPORT_ID);

        verify(progressReportVersionMapper).selectList(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment())
                .contains("report_id =")
                .containsIgnoringCase("order by version_no desc");
    }

    @Test
    void decideWritesReturnDecisionAndComment() {
        ProgressReportVersionEntity version = new ProgressReportVersionEntity();

        version.decide(ProgressReportVersionEntity.DECISION_RETURN, "补充经费执行说明");

        assertThat(version.getDecision()).isEqualTo(ProgressReportVersionEntity.DECISION_RETURN);
        assertThat(version.getReviewComment()).isEqualTo("补充经费执行说明");
    }

    @Test
    void updateDelegatesToMapper() {
        ProgressReportVersionEntity version = new ProgressReportVersionEntity();
        when(progressReportVersionMapper.updateById(version)).thenReturn(1);

        assertThat(repository.update(version)).isEqualTo(1);
    }
}
