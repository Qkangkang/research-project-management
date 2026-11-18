package cn.edu.rpm.funding.infrastructure.repository;

import cn.edu.rpm.funding.infrastructure.entity.ExpenseSpecialApprovalEntity;
import cn.edu.rpm.funding.infrastructure.mapper.ExpenseSpecialApprovalMapper;
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
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ExpenseSpecialApprovalRepositoryImpl} 纯单元测试（T-FUND-011）。
 *
 * <p>覆盖：插入特批保存 {@code special_type}、{@code over_amount}、{@code valid_until}、
 * {@code adjustment_version_id}；按 {@code expense_request_id + status} 查询未决特批；
 * 按审批实例查询；更新返回受影响行数。</p>
 */
@ExtendWith(MockitoExtension.class)
class ExpenseSpecialApprovalRepositoryImplTest {

    private static final String APPROVAL_ID = "12121212121212121212121212121212";
    private static final String EXPENSE_ID = "22222222222222222222222222222222";
    private static final String EXPENSE_VERSION_ID = "55555555555555555555555555555555";
    private static final String BUDGET_VERSION_ID = "33333333333333333333333333333333";
    private static final String ADJUSTMENT_VERSION_ID = "13131313131313131313131313131313";
    private static final String APPLICANT_ID = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String APPROVER_ID = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final String WF_INSTANCE_ID = "14141414141414141414141414141414";

    @Mock
    private ExpenseSpecialApprovalMapper expenseSpecialApprovalMapper;

    @Captor
    private ArgumentCaptor<LambdaQueryWrapper<ExpenseSpecialApprovalEntity>> wrapperCaptor;

    private ExpenseSpecialApprovalRepositoryImpl repository;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ExpenseSpecialApprovalEntity.class);
    }

    @BeforeEach
    void setUp() {
        repository = new ExpenseSpecialApprovalRepositoryImpl(expenseSpecialApprovalMapper);
    }

    @Test
    void insertPersistsSpecialTypeOverAmountValidityAndAdjustmentVersion() {
        ExpenseSpecialApprovalEntity approval = approvedOverBudget();

        repository.insert(approval);

        verify(expenseSpecialApprovalMapper).insert(approval);
        assertThat(approval.getSpecialType()).isEqualTo(ExpenseSpecialApprovalEntity.TYPE_OVER_BUDGET);
        assertThat(approval.getOverAmount()).isEqualByComparingTo("3500.00");
        assertThat(approval.getValidUntil()).isEqualTo(LocalDate.of(2026, 6, 30));
        assertThat(approval.getAdjustmentVersionId()).isEqualTo(ADJUSTMENT_VERSION_ID);
        assertThat(approval.getStatus()).isEqualTo(ExpenseSpecialApprovalEntity.STATUS_APPROVED);
    }

    @Test
    void findByExpenseRequestIdAndStatusQueriesPendingApprovals() {
        ExpenseSpecialApprovalEntity pending = pendingOverBudget();
        when(expenseSpecialApprovalMapper.selectList(
                ArgumentMatchers.<Wrapper<ExpenseSpecialApprovalEntity>>any()))
                .thenReturn(List.of(pending));

        List<ExpenseSpecialApprovalEntity> result = repository.findByExpenseRequestIdAndStatus(
                EXPENSE_ID, ExpenseSpecialApprovalEntity.STATUS_PENDING);

        assertThat(result).containsExactly(pending);
        verify(expenseSpecialApprovalMapper).selectList(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment())
                .contains("expense_request_id =", "status =")
                .containsIgnoringCase("order by created_at desc");
        assertThat(wrapperCaptor.getValue().getParamNameValuePairs().values())
                .contains(EXPENSE_ID, ExpenseSpecialApprovalEntity.STATUS_PENDING);
    }

    @Test
    void findByWfInstanceIdQueriesWorkflowInstance() {
        ExpenseSpecialApprovalEntity pending = pendingOverBudget();
        when(expenseSpecialApprovalMapper.selectOne(
                ArgumentMatchers.<Wrapper<ExpenseSpecialApprovalEntity>>any())).thenReturn(pending);

        Optional<ExpenseSpecialApprovalEntity> result = repository.findByWfInstanceId(WF_INSTANCE_ID);

        assertThat(result).containsSame(pending);
        verify(expenseSpecialApprovalMapper).selectOne(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment()).contains("wf_instance_id =");
        assertThat(wrapperCaptor.getValue().getParamNameValuePairs().values())
                .contains(WF_INSTANCE_ID);
    }

    @Test
    void selectForUpdateDelegatesToLockingMapperMethod() {
        ExpenseSpecialApprovalEntity pending = pendingOverBudget();
        when(expenseSpecialApprovalMapper.selectByIdForUpdate(APPROVAL_ID)).thenReturn(pending);

        Optional<ExpenseSpecialApprovalEntity> result = repository.selectForUpdate(APPROVAL_ID);

        assertThat(result).containsSame(pending);
        verify(expenseSpecialApprovalMapper).selectByIdForUpdate(APPROVAL_ID);
    }

    @Test
    void findByExpenseRequestIdQueriesAllApprovalsOrderedByCreatedAt() {
        ExpenseSpecialApprovalEntity pending = pendingOverBudget();
        when(expenseSpecialApprovalMapper.selectList(
                ArgumentMatchers.<Wrapper<ExpenseSpecialApprovalEntity>>any()))
                .thenReturn(List.of(pending));

        List<ExpenseSpecialApprovalEntity> result = repository.findByExpenseRequestId(EXPENSE_ID);

        assertThat(result).containsExactly(pending);
        verify(expenseSpecialApprovalMapper).selectList(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment())
                .contains("expense_request_id =")
                .containsIgnoringCase("order by created_at desc");
        assertThat(wrapperCaptor.getValue().getParamNameValuePairs().values()).contains(EXPENSE_ID);
    }

    @Test
    void findApprovedExpiringBeforeFiltersStatusAndValidity() {
        ExpenseSpecialApprovalEntity approved = approvedOverBudget();
        when(expenseSpecialApprovalMapper.selectList(
                ArgumentMatchers.<Wrapper<ExpenseSpecialApprovalEntity>>any()))
                .thenReturn(List.of(approved));
        LocalDate today = LocalDate.of(2026, 7, 1);

        List<ExpenseSpecialApprovalEntity> result = repository.findApprovedExpiringBefore(today);

        assertThat(result).containsExactly(approved);
        verify(expenseSpecialApprovalMapper).selectList(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment())
                .contains("status =", "valid_until <")
                .containsIgnoringCase("order by valid_until asc");
        assertThat(wrapperCaptor.getValue().getParamNameValuePairs().values())
                .contains(ExpenseSpecialApprovalEntity.STATUS_APPROVED, today);
    }

    @Test
    void updateReturnsAffectedRowsZeroOnVersionConflict() {
        ExpenseSpecialApprovalEntity approval = approvedOverBudget();
        when(expenseSpecialApprovalMapper.updateById(approval)).thenReturn(0);

        int affected = repository.update(approval);

        assertThat(affected).isZero();
        verify(expenseSpecialApprovalMapper).updateById(approval);
    }

    private ExpenseSpecialApprovalEntity pendingOverBudget() {
        ExpenseSpecialApprovalEntity approval = overBudgetBase();
        approval.setStatus(ExpenseSpecialApprovalEntity.STATUS_PENDING);
        return approval;
    }

    private ExpenseSpecialApprovalEntity approvedOverBudget() {
        ExpenseSpecialApprovalEntity approval = overBudgetBase();
        approval.setStatus(ExpenseSpecialApprovalEntity.STATUS_APPROVED);
        approval.setApproverId(APPROVER_ID);
        approval.setApprovedAt(OffsetDateTime.parse("2026-04-03T10:00:00+08:00"));
        approval.setValidUntil(LocalDate.of(2026, 6, 30));
        approval.setAdjustmentVersionId(ADJUSTMENT_VERSION_ID);
        return approval;
    }

    private ExpenseSpecialApprovalEntity overBudgetBase() {
        ExpenseSpecialApprovalEntity approval = new ExpenseSpecialApprovalEntity();
        approval.setId(APPROVAL_ID);
        approval.setExpenseRequestId(EXPENSE_ID);
        approval.setExpenseVersionId(EXPENSE_VERSION_ID);
        approval.setSpecialType(ExpenseSpecialApprovalEntity.TYPE_OVER_BUDGET);
        approval.setBudgetVersionId(BUDGET_VERSION_ID);
        approval.setBudgetLineId("44444444444444444444444444444444");
        approval.setOverAmount(new BigDecimal("3500.00"));
        approval.setReason("设备紧急采购超出预算额度");
        approval.setApplicantId(APPLICANT_ID);
        approval.setApplicantName("张三");
        approval.setEvidenceFileIds("[]");
        approval.setWfInstanceId(WF_INSTANCE_ID);
        return approval;
    }
}
