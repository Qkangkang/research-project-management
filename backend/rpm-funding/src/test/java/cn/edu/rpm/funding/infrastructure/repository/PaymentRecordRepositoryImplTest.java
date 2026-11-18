package cn.edu.rpm.funding.infrastructure.repository;

import cn.edu.rpm.funding.infrastructure.entity.PaymentRecordEntity;
import cn.edu.rpm.funding.infrastructure.mapper.PaymentRecordMapper;
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
 * {@link PaymentRecordRepositoryImpl} 纯单元测试（T-FUND-007）。
 *
 * <p>覆盖：插入支付记录保存外部单号/金额/币种；{@code uk_payment_external_no} 冲突；
 * 按外部单号查询；按支出查询按支付时间升序；更新返回受影响行数。</p>
 */
@ExtendWith(MockitoExtension.class)
class PaymentRecordRepositoryImplTest {

    private static final String PROJECT_ID = "8d7c6b5a4e3f4a2b9c1d0e2f3a4b5c6d";
    private static final String PAYMENT_ID = "88888888888888888888888888888888";
    private static final String EXPENSE_ID = "22222222222222222222222222222222";

    @Mock
    private PaymentRecordMapper paymentRecordMapper;

    @Captor
    private ArgumentCaptor<LambdaQueryWrapper<PaymentRecordEntity>> wrapperCaptor;

    private PaymentRecordRepositoryImpl repository;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, PaymentRecordEntity.class);
    }

    @BeforeEach
    void setUp() {
        repository = new PaymentRecordRepositoryImpl(paymentRecordMapper);
    }

    @Test
    void insertPersistsExternalPaymentNoAmountAndCurrency() {
        PaymentRecordEntity payment = receivedPayment();

        repository.insert(payment);

        verify(paymentRecordMapper).insert(payment);
        assertThat(payment.getExternalPaymentNo()).isEqualTo("PAY-2026-0009");
        assertThat(payment.getAmount()).isEqualByComparingTo("12800.00");
        assertThat(payment.getCurrency()).isEqualTo("CNY");
        assertThat(payment.getStatus()).isEqualTo(PaymentRecordEntity.STATUS_RECEIVED);
    }

    @Test
    void duplicateExternalPaymentNoPropagatesUniqueConstraintFailure() {
        PaymentRecordEntity payment = receivedPayment();
        when(paymentRecordMapper.insert(payment))
                .thenThrow(new DuplicateKeyException("uk_payment_external_no"));

        assertThatThrownBy(() -> repository.insert(payment))
                .isInstanceOf(DuplicateKeyException.class)
                .hasMessageContaining("uk_payment_external_no");
    }

    @Test
    void findByExternalPaymentNoQueriesUniqueColumn() {
        PaymentRecordEntity payment = receivedPayment();
        when(paymentRecordMapper.selectOne(
                ArgumentMatchers.<Wrapper<PaymentRecordEntity>>any())).thenReturn(payment);

        Optional<PaymentRecordEntity> result = repository.findByExternalPaymentNo("PAY-2026-0009");

        assertThat(result).containsSame(payment);
        verify(paymentRecordMapper).selectOne(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment()).contains("external_payment_no =");
        assertThat(wrapperCaptor.getValue().getParamNameValuePairs().values())
                .contains("PAY-2026-0009");
    }

    @Test
    void findByExpenseRequestIdOrdersByPaidAtAsc() {
        PaymentRecordEntity first = receivedPayment();
        PaymentRecordEntity second = receivedPayment();
        first.setPaidAt(OffsetDateTime.parse("2026-03-01T09:00:00+08:00"));
        second.setPaidAt(OffsetDateTime.parse("2026-03-03T09:00:00+08:00"));
        when(paymentRecordMapper.selectList(ArgumentMatchers.<Wrapper<PaymentRecordEntity>>any()))
                .thenReturn(Arrays.asList(first, second));

        List<PaymentRecordEntity> result = repository.findByExpenseRequestId(EXPENSE_ID);

        assertThat(result).extracting(PaymentRecordEntity::getPaidAt)
                .containsExactly(first.getPaidAt(), second.getPaidAt());
        verify(paymentRecordMapper).selectList(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment())
                .contains("expense_request_id =")
                .containsIgnoringCase("order by paid_at asc");
    }

    @Test
    void updateReturnsAffectedRowsZeroOnVersionConflict() {
        PaymentRecordEntity payment = receivedPayment();
        payment.setStatus(PaymentRecordEntity.STATUS_APPLIED);
        when(paymentRecordMapper.updateById(payment)).thenReturn(0);

        int affected = repository.update(payment);

        assertThat(affected).isZero();
        verify(paymentRecordMapper).updateById(payment);
    }

    private PaymentRecordEntity receivedPayment() {
        PaymentRecordEntity payment = new PaymentRecordEntity();
        payment.setId(PAYMENT_ID);
        payment.setProjectId(PROJECT_ID);
        payment.setExpenseRequestId(EXPENSE_ID);
        payment.setExternalPaymentNo("PAY-2026-0009");
        payment.setAmount(new BigDecimal("12800.00"));
        payment.setCurrency("CNY");
        payment.setPaidAt(OffsetDateTime.parse("2026-03-02T10:00:00+08:00"));
        payment.setStatus(PaymentRecordEntity.STATUS_RECEIVED);
        payment.setRawPayload("{}");
        return payment;
    }
}
