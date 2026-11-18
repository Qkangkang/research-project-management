package cn.edu.rpm.funding.infrastructure.repository;

import cn.edu.rpm.funding.infrastructure.entity.ReconciliationDiffEntity;
import cn.edu.rpm.funding.infrastructure.mapper.ReconciliationDiffMapper;
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

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ReconciliationDiffRepositoryImpl} 与差异状态方法纯单元测试（T-FUND-010）。
 *
 * <p>覆盖：插入差异保存四类差异类型与双侧金额；关闭后状态 {@code CLOSED}；
 * 忽略后状态 {@code IGNORED}；已结差异禁止再次迁移；
 * 未结差异查询只包含 {@code OPEN}/{@code PROCESSING}。</p>
 */
@ExtendWith(MockitoExtension.class)
class ReconciliationDiffRepositoryImplTest {

    private static final String BATCH_ID = "dddddddddddddddddddddddddddddddd";
    private static final String DIFF_ID = "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee";
    private static final String PROJECT_ID = "8d7c6b5a4e3f4a2b9c1d0e2f3a4b5c6d";
    private static final String CLOSER_ID = "ffffffffffffffffffffffffffffffff";

    @Mock
    private ReconciliationDiffMapper reconciliationDiffMapper;

    @Captor
    private ArgumentCaptor<LambdaQueryWrapper<ReconciliationDiffEntity>> wrapperCaptor;

    private ReconciliationDiffRepositoryImpl repository;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ReconciliationDiffEntity.class);
    }

    @BeforeEach
    void setUp() {
        repository = new ReconciliationDiffRepositoryImpl(reconciliationDiffMapper);
    }

    @Test
    void insertPersistsDiffTypeAndBothSideAmounts() {
        ReconciliationDiffEntity diff = amountMismatchDiff();

        repository.insert(diff);

        verify(reconciliationDiffMapper).insert(diff);
        assertThat(diff.getDiffType()).isEqualTo(ReconciliationDiffEntity.TYPE_AMOUNT_MISMATCH);
        assertThat(diff.getSystemAmount()).isEqualByComparingTo("12800.00");
        assertThat(diff.getFinanceAmount()).isEqualByComparingTo("12000.00");
        assertThat(diff.getStatus()).isEqualTo(ReconciliationDiffEntity.STATUS_OPEN);
    }

    @Test
    void closeMovesOpenDiffToClosed() {
        ReconciliationDiffEntity diff = amountMismatchDiff();
        OffsetDateTime closedAt = OffsetDateTime.parse("2026-04-02T10:00:00+08:00");

        diff.close("已补录支付记录", CLOSER_ID, closedAt);

        assertThat(diff.getStatus()).isEqualTo(ReconciliationDiffEntity.STATUS_CLOSED);
        assertThat(diff.getResolution()).isEqualTo("已补录支付记录");
        assertThat(diff.getClosedBy()).isEqualTo(CLOSER_ID);
        assertThat(diff.getClosedAt()).isEqualTo(closedAt);
    }

    @Test
    void ignoreMovesProcessingDiffToIgnored() {
        ReconciliationDiffEntity diff = amountMismatchDiff();
        diff.setStatus(ReconciliationDiffEntity.STATUS_PROCESSING);

        diff.ignore("财务确认无需处理", CLOSER_ID, OffsetDateTime.parse("2026-04-02T10:00:00+08:00"));

        assertThat(diff.getStatus()).isEqualTo(ReconciliationDiffEntity.STATUS_IGNORED);
        assertThat(diff.getResolution()).isEqualTo("财务确认无需处理");
    }

    @Test
    void closedDiffRejectsFurtherTransition() {
        ReconciliationDiffEntity diff = amountMismatchDiff();
        diff.close("已处理", CLOSER_ID, OffsetDateTime.parse("2026-04-02T10:00:00+08:00"));

        assertThatThrownBy(() -> diff.close("重复关闭", CLOSER_ID, OffsetDateTime.now()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CLOSED");
        assertThatThrownBy(() -> diff.ignore("重复忽略", CLOSER_ID, OffsetDateTime.now()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CLOSED");
    }

    @Test
    void findUnresolvedByProjectIdFiltersOpenAndProcessing() {
        ReconciliationDiffEntity open = amountMismatchDiff();
        when(reconciliationDiffMapper.selectList(
                ArgumentMatchers.<Wrapper<ReconciliationDiffEntity>>any())).thenReturn(List.of(open));

        List<ReconciliationDiffEntity> result = repository.findUnresolvedByProjectId(PROJECT_ID);

        assertThat(result).containsExactly(open);
        verify(reconciliationDiffMapper).selectList(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment())
                .contains("project_id =")
                .containsIgnoringCase("status in");
        assertThat(wrapperCaptor.getValue().getParamNameValuePairs().values())
                .contains(PROJECT_ID, ReconciliationDiffEntity.STATUS_OPEN,
                        ReconciliationDiffEntity.STATUS_PROCESSING);
    }

    private ReconciliationDiffEntity amountMismatchDiff() {
        ReconciliationDiffEntity diff = new ReconciliationDiffEntity();
        diff.setId(DIFF_ID);
        diff.setBatchId(BATCH_ID);
        diff.setProjectId(PROJECT_ID);
        diff.setPaymentRecordId("88888888888888888888888888888888");
        diff.setDiffType(ReconciliationDiffEntity.TYPE_AMOUNT_MISMATCH);
        diff.setExternalPaymentNo("PAY-2026-0009");
        diff.setSystemAmount(new BigDecimal("12800.00"));
        diff.setFinanceAmount(new BigDecimal("12000.00"));
        diff.setOwnerId(CLOSER_ID);
        diff.setStatus(ReconciliationDiffEntity.STATUS_OPEN);
        return diff;
    }
}
