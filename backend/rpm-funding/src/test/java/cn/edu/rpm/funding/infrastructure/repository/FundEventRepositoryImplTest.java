package cn.edu.rpm.funding.infrastructure.repository;

import cn.edu.rpm.funding.infrastructure.entity.FundEventEntity;
import cn.edu.rpm.funding.infrastructure.mapper.FundEventMapper;
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
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link FundEventRepositoryImpl} 纯单元测试（T-FUND-006）。
 *
 * <p>覆盖：追加事件保存九类事件类型/金额/冲正关联；幂等键冲突向上抛出；
 * 按项目+类型查询按发生时间升序；接口不暴露更新/删除（事件不可变）。</p>
 */
@ExtendWith(MockitoExtension.class)
class FundEventRepositoryImplTest {

    private static final String PROJECT_ID = "8d7c6b5a4e3f4a2b9c1d0e2f3a4b5c6d";
    private static final String EVENT_ID = "66666666666666666666666666666666";
    private static final String EXPENSE_ID = "22222222222222222222222222222222";
    private static final String BUDGET_VERSION_ID = "33333333333333333333333333333333";
    private static final String BUDGET_LINE_ID = "44444444444444444444444444444444";
    private static final String REVERSED_EVENT_ID = "77777777777777777777777777777777";

    @Mock
    private FundEventMapper fundEventMapper;

    @Captor
    private ArgumentCaptor<LambdaQueryWrapper<FundEventEntity>> wrapperCaptor;

    private FundEventRepositoryImpl repository;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, FundEventEntity.class);
    }

    @BeforeEach
    void setUp() {
        repository = new FundEventRepositoryImpl(fundEventMapper);
    }

    @Test
    void insertPersistsEventTypeAmountAndReversalLink() {
        FundEventEntity event = reverseEvent();

        repository.insert(event);

        verify(fundEventMapper).insert(event);
        assertThat(event.getEventType()).isEqualTo(FundEventEntity.TYPE_REVERSE);
        assertThat(event.getAmount()).isEqualByComparingTo("12800.00");
        assertThat(event.getReversalOfEventId()).isEqualTo(REVERSED_EVENT_ID);
        assertThat(event.getIdempotencyKey()).isEqualTo("payment:PAY-2026-0009:REVERSE");
    }

    @Test
    void duplicateIdempotencyKeyPropagatesUniqueConstraintFailure() {
        FundEventEntity event = reverseEvent();
        when(fundEventMapper.insert(event))
                .thenThrow(new DuplicateKeyException("uk_fund_event_idempotency"));

        assertThatThrownBy(() -> repository.insert(event))
                .isInstanceOf(DuplicateKeyException.class)
                .hasMessageContaining("uk_fund_event_idempotency");
    }

    @Test
    void findByIdempotencyKeyReturnsExistingEvent() {
        FundEventEntity event = reverseEvent();
        when(fundEventMapper.selectOne(
                ArgumentMatchers.<Wrapper<FundEventEntity>>any())).thenReturn(event);

        Optional<FundEventEntity> result =
                repository.findByIdempotencyKey("payment:PAY-2026-0009:REVERSE");

        assertThat(result).containsSame(event);
        verify(fundEventMapper).selectOne(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment()).contains("idempotency_key =");
        assertThat(wrapperCaptor.getValue().getParamNameValuePairs().values())
                .contains("payment:PAY-2026-0009:REVERSE");
    }

    @Test
    void findByProjectIdAndEventTypeOrdersByOccurredAtAsc() {
        FundEventEntity first = event(FundEventEntity.TYPE_RESERVE, "2026-03-01T09:00:00+08:00");
        FundEventEntity second = event(FundEventEntity.TYPE_RESERVE, "2026-03-02T09:00:00+08:00");
        when(fundEventMapper.selectList(ArgumentMatchers.<Wrapper<FundEventEntity>>any()))
                .thenReturn(Arrays.asList(first, second));

        List<FundEventEntity> result = repository.findByProjectIdAndEventType(
                PROJECT_ID, FundEventEntity.TYPE_RESERVE);

        assertThat(result).extracting(FundEventEntity::getOccurredAt)
                .containsExactly(first.getOccurredAt(), second.getOccurredAt());
        verify(fundEventMapper).selectList(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment())
                .contains("project_id =", "event_type =")
                .containsIgnoringCase("order by occurred_at asc");
        assertThat(wrapperCaptor.getValue().getParamNameValuePairs().values())
                .contains(FundEventEntity.TYPE_RESERVE);
    }

    @Test
    void interfaceExposesNoUpdateOrDeleteMethods() {
        assertThat(FundEventRepository.class.getMethods())
                .extracting(java.lang.reflect.Method::getName)
                .doesNotContain("update", "delete", "deleteById");
    }

    private FundEventEntity reverseEvent() {
        FundEventEntity event = event(FundEventEntity.TYPE_REVERSE, "2026-03-05T10:00:00+08:00");
        event.setReversalOfEventId(REVERSED_EVENT_ID);
        event.setExternalNo("PAY-2026-0009");
        event.setIdempotencyKey("payment:PAY-2026-0009:REVERSE");
        return event;
    }

    private FundEventEntity event(String eventType, String occurredAt) {
        FundEventEntity event = new FundEventEntity();
        event.setId(EVENT_ID);
        event.setEventNo("FE-2026-000001");
        event.setProjectId(PROJECT_ID);
        event.setExpenseRequestId(EXPENSE_ID);
        event.setBudgetVersionId(BUDGET_VERSION_ID);
        event.setBudgetLineId(BUDGET_LINE_ID);
        event.setEventType(eventType);
        event.setAmount(new BigDecimal("12800.00"));
        event.setOccurredAt(OffsetDateTime.parse(occurredAt));
        event.setIdempotencyKey("expense:" + EXPENSE_ID + ":RESERVE");
        return event;
    }
}
