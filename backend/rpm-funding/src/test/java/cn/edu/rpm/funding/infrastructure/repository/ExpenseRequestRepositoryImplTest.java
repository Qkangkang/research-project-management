package cn.edu.rpm.funding.infrastructure.repository;

import cn.edu.rpm.funding.infrastructure.entity.ExpenseRequestEntity;
import cn.edu.rpm.funding.infrastructure.mapper.ExpenseRequestMapper;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ExpenseRequestRepositoryImpl} 纯单元测试（T-FUND-004）。
 *
 * <p>覆盖：插入保存单号/金额/幂等键；{@code uk_expense_request_no} 与
 * {@code uk_expense_idempotency} 冲突向上抛出；按单号、幂等键查询；
 * 提交入口使用行级排他锁查询。</p>
 */
@ExtendWith(MockitoExtension.class)
class ExpenseRequestRepositoryImplTest {

    private static final String PROJECT_ID = "8d7c6b5a4e3f4a2b9c1d0e2f3a4b5c6d";
    private static final String EXPENSE_ID = "22222222222222222222222222222222";
    private static final String BUDGET_VERSION_ID = "33333333333333333333333333333333";
    private static final String BUDGET_LINE_ID = "44444444444444444444444444444444";

    @Mock
    private ExpenseRequestMapper expenseRequestMapper;

    @Captor
    private ArgumentCaptor<LambdaQueryWrapper<ExpenseRequestEntity>> wrapperCaptor;

    private ExpenseRequestRepositoryImpl repository;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ExpenseRequestEntity.class);
    }

    @BeforeEach
    void setUp() {
        repository = new ExpenseRequestRepositoryImpl(expenseRequestMapper);
    }

    @Test
    void insertPersistsRequestNoAmountAndIdempotencyKey() {
        ExpenseRequestEntity expense = draftExpense();

        repository.insert(expense);

        verify(expenseRequestMapper).insert(expense);
        assertThat(expense.getRequestNo()).isEqualTo("EXP-2026-0001");
        assertThat(expense.getAmount()).isEqualByComparingTo("12800.00");
        assertThat(expense.getIdempotencyKey()).isEqualTo("expense:create:EXP-2026-0001");
        assertThat(expense.getStatus()).isEqualTo(ExpenseRequestEntity.STATUS_DRAFT);
        assertThat(expense.getPayeeBankAccountMasked()).isEqualTo("**** **** **** 6789");
    }

    @Test
    void findByProjectIdQueriesProjectOrderedByCreatedAtDesc() {
        ExpenseRequestEntity expense = draftExpense();
        when(expenseRequestMapper.selectList(
                ArgumentMatchers.<Wrapper<ExpenseRequestEntity>>any()))
                .thenReturn(List.of(expense));

        List<ExpenseRequestEntity> result = repository.findByProjectId(PROJECT_ID);

        assertThat(result).containsExactly(expense);
        verify(expenseRequestMapper).selectList(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment())
                .contains("project_id =")
                .containsIgnoringCase("order by created_at desc");
        assertThat(wrapperCaptor.getValue().getParamNameValuePairs().values())
                .contains(PROJECT_ID);
    }

    @Test
    void duplicateRequestNoPropagatesUniqueConstraintFailure() {
        ExpenseRequestEntity expense = draftExpense();
        when(expenseRequestMapper.insert(expense))
                .thenThrow(new DuplicateKeyException("uk_expense_request_no"));

        assertThatThrownBy(() -> repository.insert(expense))
                .isInstanceOf(DuplicateKeyException.class)
                .hasMessageContaining("uk_expense_request_no");
    }

    @Test
    void duplicateIdempotencyKeyPropagatesUniqueConstraintFailure() {
        ExpenseRequestEntity expense = draftExpense();
        when(expenseRequestMapper.insert(expense))
                .thenThrow(new DuplicateKeyException("uk_expense_idempotency"));

        assertThatThrownBy(() -> repository.insert(expense))
                .isInstanceOf(DuplicateKeyException.class)
                .hasMessageContaining("uk_expense_idempotency");
    }

    @Test
    void findByRequestNoQueriesRequestNoColumn() {
        ExpenseRequestEntity expense = draftExpense();
        when(expenseRequestMapper.selectOne(
                ArgumentMatchers.<Wrapper<ExpenseRequestEntity>>any())).thenReturn(expense);

        Optional<ExpenseRequestEntity> result = repository.findByRequestNo("EXP-2026-0001");

        assertThat(result).containsSame(expense);
        verify(expenseRequestMapper).selectOne(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment()).contains("request_no =");
        assertThat(wrapperCaptor.getValue().getParamNameValuePairs().values())
                .contains("EXP-2026-0001");
    }

    @Test
    void findByIdempotencyKeyQueriesIdempotencyKeyColumn() {
        when(expenseRequestMapper.selectOne(
                ArgumentMatchers.<Wrapper<ExpenseRequestEntity>>any())).thenReturn(null);

        Optional<ExpenseRequestEntity> result = repository.findByIdempotencyKey("expense:create:EXP-2026-0001");

        assertThat(result).isEmpty();
        verify(expenseRequestMapper).selectOne(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment()).contains("creation_idempotency_key =");
        assertThat(wrapperCaptor.getValue().getParamNameValuePairs().values())
                .contains("expense:create:EXP-2026-0001");
    }

    @Test
    void selectForUpdateUsesLockingMapperMethod() {
        ExpenseRequestEntity expense = draftExpense();
        when(expenseRequestMapper.selectByIdForUpdate(EXPENSE_ID)).thenReturn(expense);

        Optional<ExpenseRequestEntity> result = repository.selectForUpdate(EXPENSE_ID);

        assertThat(result).containsSame(expense);
        verify(expenseRequestMapper).selectByIdForUpdate(EXPENSE_ID);
    }

    @Test
    void findByProjectIdAndStatusOrdersByCreatedAtDesc() {
        when(expenseRequestMapper.selectList(
                ArgumentMatchers.<Wrapper<ExpenseRequestEntity>>any())).thenReturn(List.of());

        List<ExpenseRequestEntity> result = repository.findByProjectIdAndStatus(
                PROJECT_ID, ExpenseRequestEntity.STATUS_IN_REVIEW);

        assertThat(result).isEmpty();
        verify(expenseRequestMapper).selectList(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment())
                .contains("project_id =", "status =")
                .containsIgnoringCase("order by created_at desc");
    }

    private ExpenseRequestEntity draftExpense() {
        ExpenseRequestEntity expense = new ExpenseRequestEntity();
        expense.setId(EXPENSE_ID);
        expense.setRequestNo("EXP-2026-0001");
        expense.setProjectId(PROJECT_ID);
        expense.setBudgetVersionId(BUDGET_VERSION_ID);
        expense.setBudgetLineId(BUDGET_LINE_ID);
        expense.setAmount(new BigDecimal("12800.00"));
        expense.setPurpose("差旅费");
        expense.setPayeeName("测试收款人");
        expense.setPayeeBankAccountMasked("**** **** **** 6789");
        expense.setStatus(ExpenseRequestEntity.STATUS_DRAFT);
        expense.setIdempotencyKey("expense:create:EXP-2026-0001");
        return expense;
    }
}
