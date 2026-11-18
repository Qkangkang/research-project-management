package cn.edu.rpm.funding.domain.service;

import cn.edu.rpm.common.domain.service.NumberGenerator;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.funding.domain.model.FundEventCommand;
import cn.edu.rpm.funding.infrastructure.entity.BudgetBalanceEntity;
import cn.edu.rpm.funding.infrastructure.entity.FundEventEntity;
import cn.edu.rpm.funding.infrastructure.repository.BudgetBalanceRepository;
import cn.edu.rpm.funding.infrastructure.repository.FundEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link FundEventAppender} 纯单元测试（T-FUND-016；TC-FUND-033、TC-FUND-034、
 * TC-FUND-110、TC-FUND-111）。
 *
 * <p>覆盖九类事件投影矩阵逐项断言、幂等键返回原事件与载荷不一致拒绝、
 * 投影不为负、非法冲正拒绝。事件编号由 {@link NumberGenerator} 提供（测试桩）。</p>
 */
@ExtendWith(MockitoExtension.class)
class FundEventAppenderTest {

    private static final String PROJECT_ID = "8d7c6b5a4e3f4a2b9c1d0e2f3a4b5c6d";
    private static final String LINE_ID = "44444444444444444444444444444444";
    private static final String VERSION_ID = "33333333333333333333333333333333";
    private static final String EXPENSE_ID = "22222222222222222222222222222222";
    private static final String EVENT_NO = "FE-2026-000001";
    private static final String KEY = "expense:2222:RESERVE";
    private static final String ORIGINAL_EVENT_ID = "77777777777777777777777777777777";
    private static final Instant NOW = Instant.parse("2026-03-02T02:00:00Z");
    private static final UUID ACTOR_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Mock
    private FundEventRepository fundEventRepository;

    @Mock
    private BudgetBalanceRepository budgetBalanceRepository;

    @Mock
    private NumberGenerator numberGenerator;

    private FundEventAppender appender;

    private BudgetBalanceEntity balance;

    @BeforeEach
    void setUp() {
        appender = new FundEventAppender(fundEventRepository, budgetBalanceRepository,
                numberGenerator, Clock.fixed(NOW, ZoneId.of("Asia/Shanghai")));
        balance = new BudgetBalanceEntity();
        balance.setId("12121212121212121212121212121212");
        balance.setBudgetLineId(LINE_ID);
        balance.setApprovedAmount(new BigDecimal("100000.00"));
    }

    @Test
    void reserveIncreasesReservedAndPersistsEvent() {
        stubFreshAppend();
        ArgumentCaptor<FundEventEntity> eventCaptor = ArgumentCaptor.forClass(FundEventEntity.class);

        FundEventEntity result = appender.append(command(FundEventEntity.TYPE_RESERVE, "10000.00"));

        assertThat(balance.getReservedAmount()).isEqualByComparingTo("10000.00");
        assertThat(balance.availableAmount()).isEqualByComparingTo("90000.00");
        verify(fundEventRepository).insert(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo(FundEventEntity.TYPE_RESERVE);
        assertThat(eventCaptor.getValue().getAmount()).isEqualByComparingTo("10000.00");
        assertThat(eventCaptor.getValue().getEventNo()).isEqualTo(EVENT_NO);
        assertThat(eventCaptor.getValue().getOccurredAt().toInstant()).isEqualTo(NOW);
        assertThat(result.getIdempotencyKey()).isEqualTo(KEY);
    }

    @Test
    void releaseDecreasesReserved() {
        balance.setReservedAmount(new BigDecimal("30000.00"));
        stubFreshAppend();

        appender.append(command(FundEventEntity.TYPE_RELEASE, "10000.00"));

        assertThat(balance.getReservedAmount()).isEqualByComparingTo("20000.00");
    }

    @Test
    void approveMovesReservedToApprovedUnpaid() {
        balance.setReservedAmount(new BigDecimal("30000.00"));
        stubFreshAppend();

        appender.append(command(FundEventEntity.TYPE_APPROVE, "30000.00"));

        assertThat(balance.getReservedAmount()).isEqualByComparingTo("0.00");
        assertThat(balance.getApprovedUnpaidAmount()).isEqualByComparingTo("30000.00");
    }

    @Test
    void payMovesApprovedUnpaidToPaid() {
        balance.setApprovedUnpaidAmount(new BigDecimal("30000.00"));
        stubFreshAppend();

        appender.append(command(FundEventEntity.TYPE_PAY, "30000.00"));

        assertThat(balance.getApprovedUnpaidAmount()).isEqualByComparingTo("0.00");
        assertThat(balance.getPaidAmount()).isEqualByComparingTo("30000.00");
    }

    @Test
    void partialPayMovesOnlyEventAmount() {
        balance.setApprovedUnpaidAmount(new BigDecimal("30000.00"));
        stubFreshAppend();

        appender.append(command(FundEventEntity.TYPE_PARTIAL_PAY, "12000.00"));

        assertThat(balance.getApprovedUnpaidAmount()).isEqualByComparingTo("18000.00");
        assertThat(balance.getPaidAmount()).isEqualByComparingTo("12000.00");
    }

    @Test
    void cancelDecreasesApprovedUnpaid() {
        balance.setApprovedUnpaidAmount(new BigDecimal("30000.00"));
        stubFreshAppend();

        appender.append(command(FundEventEntity.TYPE_CANCEL, "20000.00"));

        assertThat(balance.getApprovedUnpaidAmount()).isEqualByComparingTo("10000.00");
        assertThat(balance.getPaidAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void refundDecreasesPaid() {
        balance.setPaidAmount(new BigDecimal("30000.00"));
        stubFreshAppend();

        appender.append(command(FundEventEntity.TYPE_REFUND, "5000.00"));

        assertThat(balance.getPaidAmount()).isEqualByComparingTo("25000.00");
    }

    @Test
    void reverseInvertsOriginalPaymentEvent() {
        balance.setPaidAmount(new BigDecimal("30000.00"));
        stubFreshAppend("payment:PAY-2026-0009:REVERSE");
        FundEventEntity original = event(ORIGINAL_EVENT_ID, FundEventEntity.TYPE_PAY, "30000.00");
        when(fundEventRepository.findById(ORIGINAL_EVENT_ID)).thenReturn(Optional.of(original));

        FundEventCommand reverse = new FundEventCommand(FundEventEntity.TYPE_REVERSE, PROJECT_ID,
                EXPENSE_ID, VERSION_ID, LINE_ID, new BigDecimal("30000.00"), "PAY-2026-0009",
                "payment:PAY-2026-0009:REVERSE", ORIGINAL_EVENT_ID, "{}", ACTOR_ID);
        appender.append(reverse);

        assertThat(balance.getPaidAmount()).isEqualByComparingTo("0.00");
        assertThat(balance.getApprovedUnpaidAmount()).isEqualByComparingTo("30000.00");
    }

    @Test
    void adjustIncreasesApprovedAmount() {
        stubFreshAppend();

        appender.append(command(FundEventEntity.TYPE_ADJUST, "50000.00"));

        assertThat(balance.getApprovedAmount()).isEqualByComparingTo("150000.00");
        assertThat(balance.availableAmount()).isEqualByComparingTo("150000.00");
    }

    @Test
    void adjustDecreasesApprovedAmountUsingSignedAmount() {
        stubFreshAppend();
        ArgumentCaptor<FundEventEntity> eventCaptor = ArgumentCaptor.forClass(FundEventEntity.class);

        appender.append(command(FundEventEntity.TYPE_ADJUST, "-20000.00"));

        assertThat(balance.getApprovedAmount()).isEqualByComparingTo("80000.00");
        verify(fundEventRepository).insert(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getAmount()).isEqualByComparingTo("20000.00");
    }

    @Test
    void duplicateIdempotencyKeyReturnsExistingEventWithoutProjectionChange() {
        FundEventEntity existing = event(ORIGINAL_EVENT_ID, FundEventEntity.TYPE_RESERVE, "10000.00");
        when(fundEventRepository.findByIdempotencyKey(KEY)).thenReturn(Optional.of(existing));

        FundEventEntity result = appender.append(command(FundEventEntity.TYPE_RESERVE, "10000.00"));

        assertThat(result).isSameAs(existing);
        assertThat(balance.getReservedAmount()).isEqualByComparingTo("0.00");
        verify(fundEventRepository, never()).insert(any(FundEventEntity.class));
        verify(budgetBalanceRepository, never()).selectForUpdate(LINE_ID);
    }

    @Test
    void duplicateIdempotencyKeyWithDifferentAmountRejected() {
        FundEventEntity existing = event(ORIGINAL_EVENT_ID, FundEventEntity.TYPE_RESERVE, "9000.00");
        when(fundEventRepository.findByIdempotencyKey(KEY)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> appender.append(command(FundEventEntity.TYPE_RESERVE, "10000.00")))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FUND_003);
        verify(fundEventRepository, never()).insert(any(FundEventEntity.class));
    }

    @Test
    void projectionNeverGoesNegative() {
        balance.setReservedAmount(new BigDecimal("5000.00"));
        when(fundEventRepository.findByIdempotencyKey(KEY)).thenReturn(Optional.empty());
        when(budgetBalanceRepository.selectForUpdate(LINE_ID)).thenReturn(Optional.of(balance));

        assertThatThrownBy(() -> appender.append(command(FundEventEntity.TYPE_RELEASE, "10000.00")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("占用金额不足");
        verify(fundEventRepository, never()).insert(any(FundEventEntity.class));
        verify(budgetBalanceRepository, never()).update(balance);
    }

    @Test
    void reverseOfReverseEventRejected() {
        when(fundEventRepository.findByIdempotencyKey(KEY)).thenReturn(Optional.empty());
        FundEventEntity original = event(ORIGINAL_EVENT_ID, FundEventEntity.TYPE_REVERSE, "1000.00");
        when(fundEventRepository.findById(ORIGINAL_EVENT_ID)).thenReturn(Optional.of(original));

        FundEventCommand reverse = new FundEventCommand(FundEventEntity.TYPE_REVERSE, PROJECT_ID,
                EXPENSE_ID, VERSION_ID, LINE_ID, new BigDecimal("1000.00"), null,
                KEY, ORIGINAL_EVENT_ID, "{}", ACTOR_ID);

        assertThatThrownBy(() -> appender.append(reverse))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不可再次冲正");
    }

    @Test
    void unknownEventTypeRejectedBeforeAnyQuery() {
        FundEventCommand bogus = new FundEventCommand("BOGUS", PROJECT_ID, EXPENSE_ID,
                VERSION_ID, LINE_ID, new BigDecimal("1.00"), null, KEY, null, "{}", ACTOR_ID);

        assertThatThrownBy(() -> appender.append(bogus))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.COMMON_400);
        verify(fundEventRepository, never()).findByIdempotencyKey(KEY);
    }

    private void stubFreshAppend() {
        stubFreshAppend(KEY);
    }

    private void stubFreshAppend(String idempotencyKey) {
        when(fundEventRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(budgetBalanceRepository.selectForUpdate(LINE_ID)).thenReturn(Optional.of(balance));
        when(budgetBalanceRepository.update(balance)).thenReturn(1);
        lenient().when(numberGenerator.next(FundEventAppender.RULE_FUND_EVENT_NO)).thenReturn(EVENT_NO);
    }

    private static FundEventCommand command(String eventType, String amount) {
        return new FundEventCommand(eventType, PROJECT_ID, EXPENSE_ID, VERSION_ID, LINE_ID,
                new BigDecimal(amount), null, KEY, null, "{}", ACTOR_ID);
    }

    private static FundEventEntity event(String id, String type, String amount) {
        FundEventEntity event = new FundEventEntity();
        event.setId(id);
        event.setEventType(type);
        event.setAmount(new BigDecimal(amount));
        event.setBudgetLineId(LINE_ID);
        return event;
    }
}
