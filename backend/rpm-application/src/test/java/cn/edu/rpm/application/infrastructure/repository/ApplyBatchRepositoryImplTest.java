package cn.edu.rpm.application.infrastructure.repository;

import cn.edu.rpm.application.infrastructure.entity.ApplyBatchEntity;
import cn.edu.rpm.application.infrastructure.mapper.ApplyBatchMapper;
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
 * {@link ApplyBatchRepositoryImpl} 纯单元测试（T-APP-001 先行测试）。
 *
 * <p>验收锚点：插入批次成功；{@code uk_apply_batch_no} 唯一查询按 {@code batch_no}
 * 精确命中；按 {@code year + status} 查询年度开放批次（与 idx_apply_batch_year_status
 * 列序一致）；更新携带旧 {@code version} 返回 0 表示版本冲突。</p>
 *
 * <p>唯一索引冲突与 CHECK 约束由真实 PostgreSQL 兜底，属下游业务测试阶段。</p>
 */
@ExtendWith(MockitoExtension.class)
class ApplyBatchRepositoryImplTest {

    private static final String BATCH_ID = "0123456789abcdef0123456789abcdef";
    private static final OffsetDateTime START_AT = OffsetDateTime.parse("2026-01-01T00:00:00+08:00");
    private static final OffsetDateTime END_AT = OffsetDateTime.parse("2026-03-31T23:59:59+08:00");

    @Mock
    private ApplyBatchMapper applyBatchMapper;

    @Captor
    private ArgumentCaptor<LambdaQueryWrapper<ApplyBatchEntity>> wrapperCaptor;

    @Captor
    private ArgumentCaptor<ApplyBatchEntity> entityCaptor;

    private ApplyBatchRepositoryImpl repository;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ApplyBatchEntity.class);
    }

    @BeforeEach
    void setUp() {
        repository = new ApplyBatchRepositoryImpl(applyBatchMapper);
    }

    private static ApplyBatchEntity batch(String status) {
        ApplyBatchEntity entity = new ApplyBatchEntity();
        entity.setBatchNo("BATCH-2026-01");
        entity.setYear(2026);
        entity.setName("2026 年度院计划项目申报");
        entity.setStartAt(START_AT);
        entity.setEndAt(END_AT);
        entity.setStatus(status);
        entity.setRules("{\"maxProjectsPerApplicant\":1}");
        return entity;
    }

    @Test
    void insertDelegatesToMapperAndKeepsBatchIdentity() {
        ApplyBatchEntity entity = batch(ApplyBatchEntity.STATUS_OPEN);
        when(applyBatchMapper.insert(ArgumentMatchers.any(ApplyBatchEntity.class))).thenReturn(1);

        repository.insert(entity);

        verify(applyBatchMapper).insert(entityCaptor.capture());
        ApplyBatchEntity saved = entityCaptor.getValue();
        assertThat(saved.getBatchNo()).isEqualTo("BATCH-2026-01");
        assertThat(saved.getYear()).isEqualTo(2026);
        assertThat(saved.getStatus()).isEqualTo(ApplyBatchEntity.STATUS_OPEN);
        assertThat(saved.getStartAt()).isEqualTo(START_AT);
        assertThat(saved.getEndAt()).isEqualTo(END_AT);
        assertThat(saved.getRules()).contains("maxProjectsPerApplicant");
    }

    @Test
    void findByBatchNoFiltersUniqueBatchNo() {
        ApplyBatchEntity open = batch(ApplyBatchEntity.STATUS_OPEN);
        open.setId(BATCH_ID);
        when(applyBatchMapper.selectOne(ArgumentMatchers.<Wrapper<ApplyBatchEntity>>any()))
                .thenReturn(open);

        Optional<ApplyBatchEntity> result = repository.findByBatchNo("BATCH-2026-01");

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(BATCH_ID);
        assertThat(result.get().getBatchNo()).isEqualTo("BATCH-2026-01");
        verify(applyBatchMapper).selectOne(wrapperCaptor.capture());
        LambdaQueryWrapper<ApplyBatchEntity> wrapper = wrapperCaptor.getValue();
        assertThat(wrapper.getSqlSegment()).contains("batch_no =");
        assertThat(wrapper.getParamNameValuePairs().values()).contains("BATCH-2026-01");
    }

    @Test
    void findByBatchNoReturnsEmptyWhenMissing() {
        when(applyBatchMapper.selectOne(ArgumentMatchers.<Wrapper<ApplyBatchEntity>>any()))
                .thenReturn(null);

        assertThat(repository.findByBatchNo("BATCH-NOT-EXIST")).isEmpty();
    }

    @Test
    void findByYearAndStatusFiltersOpenBatchesOfYear() {
        ApplyBatchEntity open = batch(ApplyBatchEntity.STATUS_OPEN);
        open.setId(BATCH_ID);
        when(applyBatchMapper.selectList(ArgumentMatchers.<Wrapper<ApplyBatchEntity>>any()))
                .thenReturn(List.of(open));

        List<ApplyBatchEntity> result = repository.findByYearAndStatus(2026, ApplyBatchEntity.STATUS_OPEN);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(ApplyBatchEntity.STATUS_OPEN);
        verify(applyBatchMapper).selectList(wrapperCaptor.capture());
        LambdaQueryWrapper<ApplyBatchEntity> wrapper = wrapperCaptor.getValue();
        assertThat(wrapper.getSqlSegment()).contains("year =", "status =");
        assertThat(wrapper.getParamNameValuePairs().values()).contains(2026, ApplyBatchEntity.STATUS_OPEN);
    }

    @Test
    void findByYearAndStatusReturnsEmptyWhenNoOpenBatch() {
        when(applyBatchMapper.selectList(ArgumentMatchers.<Wrapper<ApplyBatchEntity>>any()))
                .thenReturn(List.of());

        assertThat(repository.findByYearAndStatus(2027, ApplyBatchEntity.STATUS_OPEN)).isEmpty();
    }

    @Test
    void updateReturnsZeroWhenOptimisticLockVersionIsStale() {
        ApplyBatchEntity stale = batch(ApplyBatchEntity.STATUS_CLOSED);
        stale.setId(BATCH_ID);
        stale.setVersion(2);
        when(applyBatchMapper.updateById(stale)).thenReturn(0);

        assertThat(repository.update(stale)).isZero();
    }

    @Test
    void entityMapsEveryApplyBatchColumn() {
        TableInfo tableInfo = TableInfoHelper.getTableInfo(ApplyBatchEntity.class);

        assertThat(tableInfo.getTableName()).isEqualTo("apply_batch");
        assertThat(tableInfo.getFieldList())
                .extracting(TableFieldInfo::getColumn)
                .containsExactlyInAnyOrder(
                        "batch_no", "year", "name", "start_at", "end_at", "status", "rules",
                        "created_at", "updated_at", "created_by", "updated_by", "version");
        assertThat(tableInfo.getKeyColumn()).isEqualTo("id");
    }
}
