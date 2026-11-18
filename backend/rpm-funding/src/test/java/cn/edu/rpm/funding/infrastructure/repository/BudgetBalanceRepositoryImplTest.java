package cn.edu.rpm.funding.infrastructure.repository;

import cn.edu.rpm.funding.domain.model.BudgetRebuildResult;
import cn.edu.rpm.funding.infrastructure.entity.BudgetBalanceEntity;
import cn.edu.rpm.funding.infrastructure.entity.BudgetLineEntity;
import cn.edu.rpm.funding.infrastructure.entity.BudgetVersionEntity;
import cn.edu.rpm.funding.infrastructure.entity.FundEventEntity;
import cn.edu.rpm.funding.infrastructure.mapper.BudgetBalanceMapper;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link BudgetBalanceRepositoryImpl} 纯单元测试（T-FUND-003 / T-FUND-018）。
 *
 * <p>覆盖：插入余额行保存四类金额；按科目唯一查询；{@code FOR UPDATE} 专用入口；
 * 全量重放九类事件后与在线投影误差 {@code 0.00}；在线投影漂移被修正并记录最大偏差；
 * 无生效版本时返回零结果。数据库 CHECK 约束验证留 /ly-test。</p>
 */
@ExtendWith(MockitoExtension.class)
class BudgetBalanceRepositoryImplTest {

    private static final String LINE_ID = "11111111111111111111111111111111";
    private static final String BALANCE_ID = "22222222222222222222222222222222";
    private static final String VERSION_ID = "33333333333333333333333333333333";
    private static final String PROJECT_ID = "8d7c6b5a4e3f4a2b9c1d0e2f3a4b5c6d";

    @Mock
    private BudgetBalanceMapper budgetBalanceMapper;

    @Mock
    private BudgetVersionRepository budgetVersionRepository;

    @Mock
    private BudgetLineRepository budgetLineRepository;

    @Mock
    private FundEventRepository fundEventRepository;

    @Captor
    private ArgumentCaptor<LambdaQueryWrapper<BudgetBalanceEntity>> wrapperCaptor;

    private BudgetBalanceRepositoryImpl repository;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, BudgetBalanceEntity.class);
    }

    @BeforeEach
    void setUp() {
        repository = new BudgetBalanceRepositoryImpl(budgetBalanceMapper, budgetVersionRepository,
                budgetLineRepository, fundEventRepository);
    }

    @Test
    void insertPersistsFourAmountColumns() {
        BudgetBalanceEntity balance = new BudgetBalanceEntity();
        balance.setId(BALANCE_ID);
        balance.setBudgetLineId(LINE_ID);
        balance.setApprovedAmount(new BigDecimal("1200000.00"));
        balance.setReservedAmount(new BigDecimal("80000.00"));
        balance.setApprovedUnpaidAmount(new BigDecimal("120000.00"));
        balance.setPaidAmount(new BigDecimal("300000.00"));

        repository.insert(balance);

        verify(budgetBalanceMapper).insert(balance);
        assertThat(balance.getApprovedAmount()).isEqualByComparingTo("1200000.00");
        assertThat(balance.getReservedAmount()).isEqualByComparingTo("80000.00");
        assertThat(balance.getApprovedUnpaidAmount()).isEqualByComparingTo("120000.00");
        assertThat(balance.getPaidAmount()).isEqualByComparingTo("300000.00");
        assertThat(balance.availableAmount()).isEqualByComparingTo("700000.00");
    }

    @Test
    void findByBudgetLineIdUsesUniqueLineLookup() {
        BudgetBalanceEntity balance = new BudgetBalanceEntity();
        balance.setId(BALANCE_ID);
        when(budgetBalanceMapper.selectOne(
                ArgumentMatchers.<Wrapper<BudgetBalanceEntity>>any())).thenReturn(balance);

        Optional<BudgetBalanceEntity> result = repository.findByBudgetLineId(LINE_ID);

        assertThat(result).containsSame(balance);
        verify(budgetBalanceMapper).selectOne(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment()).contains("budget_line_id =");
    }

    @Test
    void selectForUpdateDelegatesToLockingMapper() {
        BudgetBalanceEntity locked = new BudgetBalanceEntity();
        locked.setBudgetLineId(LINE_ID);
        when(budgetBalanceMapper.selectByBudgetLineIdForUpdate(LINE_ID)).thenReturn(locked);

        assertThat(repository.selectForUpdate(LINE_ID)).containsSame(locked);

        verify(budgetBalanceMapper).selectByBudgetLineIdForUpdate(LINE_ID);
    }

    @Test
    void findByBudgetLineIdsUsesSingleInQuery() {
        when(budgetBalanceMapper.selectList(
                ArgumentMatchers.<Wrapper<BudgetBalanceEntity>>any())).thenReturn(List.of());

        List<BudgetBalanceEntity> result = repository.findByBudgetLineIds(List.of(LINE_ID));

        assertThat(result).isEmpty();
        verify(budgetBalanceMapper).selectList(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment())
                .containsIgnoringCase("budget_line_id in");
    }

    @Test
    void rebuildReplaysMixedEventsWithZeroError() {
        stubRebuildScope();
        BudgetBalanceEntity online = onlineBalance("100000.00", "0.00", "25000.00", "15000.00");
        when(budgetBalanceMapper.selectList(
                ArgumentMatchers.<Wrapper<BudgetBalanceEntity>>any())).thenReturn(List.of(online));
        when(fundEventRepository.findByProjectId(PROJECT_ID)).thenReturn(List.of(
                event("e1", FundEventEntity.TYPE_ADJUST, "100000.00", null),
                event("e2", FundEventEntity.TYPE_RESERVE, "40000.00", null),
                event("e3", FundEventEntity.TYPE_APPROVE, "40000.00", null),
                event("e4", FundEventEntity.TYPE_PARTIAL_PAY, "15000.00", null)));

        BudgetRebuildResult result = repository.rebuild(PROJECT_ID);

        assertThat(result.lineCount()).isEqualTo(1);
        assertThat(result.correctedLineCount()).isZero();
        assertThat(result.maxDifference()).isEqualByComparingTo("0.00");
        assertThat(result.consistent()).isTrue();
        verify(budgetBalanceMapper, never()).updateById(online);
    }

    @Test
    void rebuildCorrectsDriftedOnlineProjection() {
        stubRebuildScope();
        BudgetBalanceEntity online = onlineBalance("100000.00", "0.00", "25000.00", "10000.00");
        when(budgetBalanceMapper.selectList(
                ArgumentMatchers.<Wrapper<BudgetBalanceEntity>>any())).thenReturn(List.of(online));
        when(fundEventRepository.findByProjectId(PROJECT_ID)).thenReturn(List.of(
                event("e1", FundEventEntity.TYPE_ADJUST, "100000.00", null),
                event("e2", FundEventEntity.TYPE_RESERVE, "40000.00", null),
                event("e3", FundEventEntity.TYPE_APPROVE, "40000.00", null),
                event("e4", FundEventEntity.TYPE_PARTIAL_PAY, "15000.00", null)));
        when(budgetBalanceMapper.updateById(online)).thenReturn(1);

        BudgetRebuildResult result = repository.rebuild(PROJECT_ID);

        assertThat(result.correctedLineCount()).isEqualTo(1);
        assertThat(result.maxDifference()).isEqualByComparingTo("5000.00");
        assertThat(result.consistent()).isFalse();
        assertThat(online.getPaidAmount()).isEqualByComparingTo("15000.00");
        verify(budgetBalanceMapper).updateById(online);
    }

    @Test
    void rebuildReturnsZeroWhenNoEffectiveVersion() {
        when(budgetVersionRepository.findEffectiveByProjectId(PROJECT_ID)).thenReturn(Optional.empty());

        BudgetRebuildResult result = repository.rebuild(PROJECT_ID);

        assertThat(result.lineCount()).isZero();
        assertThat(result.correctedLineCount()).isZero();
        assertThat(result.consistent()).isTrue();
        verify(fundEventRepository, never()).findByProjectId(any());
        verify(budgetBalanceMapper, never()).selectList(
                ArgumentMatchers.<Wrapper<BudgetBalanceEntity>>any());
    }

    private void stubRebuildScope() {
        BudgetVersionEntity effective = new BudgetVersionEntity();
        effective.setId(VERSION_ID);
        effective.setProjectId(PROJECT_ID);
        effective.setStatus(BudgetVersionEntity.STATUS_EFFECTIVE);
        when(budgetVersionRepository.findEffectiveByProjectId(PROJECT_ID)).thenReturn(Optional.of(effective));

        BudgetLineEntity line = new BudgetLineEntity();
        line.setId(LINE_ID);
        line.setBudgetVersionId(VERSION_ID);
        line.setAmount(new BigDecimal("100000.00"));
        when(budgetLineRepository.findByBudgetVersionId(VERSION_ID)).thenReturn(List.of(line));
    }

    private static BudgetBalanceEntity onlineBalance(String approved, String reserved,
                                                     String unpaid, String paid) {
        BudgetBalanceEntity balance = new BudgetBalanceEntity();
        balance.setId(BALANCE_ID);
        balance.setBudgetLineId(LINE_ID);
        balance.setApprovedAmount(new BigDecimal(approved));
        balance.setReservedAmount(new BigDecimal(reserved));
        balance.setApprovedUnpaidAmount(new BigDecimal(unpaid));
        balance.setPaidAmount(new BigDecimal(paid));
        return balance;
    }

    private static FundEventEntity event(String id, String type, String amount, String reversalOf) {
        FundEventEntity event = new FundEventEntity();
        event.setId(id);
        event.setEventType(type);
        event.setAmount(new BigDecimal(amount));
        event.setBudgetLineId(LINE_ID);
        event.setReversalOfEventId(reversalOf);
        return event;
    }
}
