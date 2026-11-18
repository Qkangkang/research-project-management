package cn.edu.rpm.funding.infrastructure.repository;

import cn.edu.rpm.funding.infrastructure.entity.ReconciliationBatchEntity;
import cn.edu.rpm.funding.infrastructure.mapper.ReconciliationBatchMapper;
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
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ReconciliationBatchRepositoryImpl} 纯单元测试（T-FUND-009）。
 *
 * <p>覆盖：插入对账批次成功；{@code uk_reconciliation_batch_no} 冲突；
 * 按对账期间查询批次。</p>
 */
@ExtendWith(MockitoExtension.class)
class ReconciliationBatchRepositoryImplTest {

    private static final String BATCH_ID = "dddddddddddddddddddddddddddddddd";
    private static final LocalDate PERIOD_START = LocalDate.of(2026, 3, 1);
    private static final LocalDate PERIOD_END = LocalDate.of(2026, 3, 31);

    @Mock
    private ReconciliationBatchMapper reconciliationBatchMapper;

    @Captor
    private ArgumentCaptor<LambdaQueryWrapper<ReconciliationBatchEntity>> wrapperCaptor;

    private ReconciliationBatchRepositoryImpl repository;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ReconciliationBatchEntity.class);
    }

    @BeforeEach
    void setUp() {
        repository = new ReconciliationBatchRepositoryImpl(reconciliationBatchMapper);
    }

    @Test
    void insertPersistsPeriodTotalsAndRunningStatus() {
        ReconciliationBatchEntity batch = runningBatch();

        repository.insert(batch);

        verify(reconciliationBatchMapper).insert(batch);
        assertThat(batch.getBatchNo()).isEqualTo("RECON-2026-03");
        assertThat(batch.getPeriodStart()).isEqualTo(PERIOD_START);
        assertThat(batch.getPeriodEnd()).isEqualTo(PERIOD_END);
        assertThat(batch.getStatus()).isEqualTo(ReconciliationBatchEntity.STATUS_RUNNING);
        assertThat(batch.getTotalFinanceAmount()).isEqualByComparingTo("128000.00");
        assertThat(batch.getDiffCount()).isZero();
    }

    @Test
    void duplicateBatchNoPropagatesUniqueConstraintFailure() {
        ReconciliationBatchEntity batch = runningBatch();
        when(reconciliationBatchMapper.insert(batch))
                .thenThrow(new DuplicateKeyException("uk_reconciliation_batch_no"));

        assertThatThrownBy(() -> repository.insert(batch))
                .isInstanceOf(DuplicateKeyException.class)
                .hasMessageContaining("uk_reconciliation_batch_no");
    }

    @Test
    void findByPeriodQueriesPeriodStartAndEnd() {
        ReconciliationBatchEntity batch = runningBatch();
        when(reconciliationBatchMapper.selectList(
                ArgumentMatchers.<Wrapper<ReconciliationBatchEntity>>any()))
                .thenReturn(List.of(batch));

        List<ReconciliationBatchEntity> result = repository.findByPeriod(PERIOD_START, PERIOD_END);

        assertThat(result).containsExactly(batch);
        verify(reconciliationBatchMapper).selectList(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment())
                .contains("period_start =", "period_end =");
        assertThat(wrapperCaptor.getValue().getParamNameValuePairs().values())
                .contains(PERIOD_START, PERIOD_END);
    }

    @Test
    void findByIdReturnsEmptyWhenAbsent() {
        when(reconciliationBatchMapper.selectById(BATCH_ID)).thenReturn(null);

        Optional<ReconciliationBatchEntity> result = repository.findById(BATCH_ID);

        assertThat(result).isEmpty();
    }

    private ReconciliationBatchEntity runningBatch() {
        ReconciliationBatchEntity batch = new ReconciliationBatchEntity();
        batch.setId(BATCH_ID);
        batch.setBatchNo("RECON-2026-03");
        batch.setPeriodStart(PERIOD_START);
        batch.setPeriodEnd(PERIOD_END);
        batch.setSourceBatchNo("FIN-2026-03");
        batch.setStatus(ReconciliationBatchEntity.STATUS_RUNNING);
        batch.setTotalFinanceAmount(new BigDecimal("128000.00"));
        batch.setTotalSystemAmount(new BigDecimal("128000.00"));
        batch.setDiffCount(0);
        batch.setStartedAt(OffsetDateTime.parse("2026-04-01T08:00:00+08:00"));
        batch.setCreatedBy("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        return batch;
    }
}
