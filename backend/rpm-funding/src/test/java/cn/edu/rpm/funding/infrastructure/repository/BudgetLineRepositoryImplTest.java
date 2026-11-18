package cn.edu.rpm.funding.infrastructure.repository;

import cn.edu.rpm.funding.infrastructure.entity.BudgetLineEntity;
import cn.edu.rpm.funding.infrastructure.mapper.BudgetLineMapper;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link BudgetLineRepositoryImpl} 纯单元测试（T-FUND-002）。
 *
 * <p>覆盖：插入科目成功且 {@link BigDecimal} 精度原样保存；
 * {@code uk_budget_line_subject} 同版本同科目冲突向上抛出。</p>
 */
@ExtendWith(MockitoExtension.class)
class BudgetLineRepositoryImplTest {

    private static final String VERSION_ID = "11111111111111111111111111111111";

    @Mock
    private BudgetLineMapper budgetLineMapper;

    @Captor
    private ArgumentCaptor<LambdaQueryWrapper<BudgetLineEntity>> wrapperCaptor;

    private BudgetLineRepositoryImpl repository;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, BudgetLineEntity.class);
    }

    @BeforeEach
    void setUp() {
        repository = new BudgetLineRepositoryImpl(budgetLineMapper);
    }

    @Test
    void insertKeepsBigDecimalAmountScale() {
        BudgetLineEntity line = new BudgetLineEntity();
        line.setBudgetVersionId(VERSION_ID);
        line.setSubjectCode("EQUIPMENT");
        line.setSubjectName("设备费");
        line.setAmount(new BigDecimal("480000.00"));
        line.setControlLevel(BudgetLineEntity.CONTROL_BLOCK);

        repository.insert(line);

        verify(budgetLineMapper).insert(line);
        assertThat(line.getAmount()).isEqualByComparingTo("480000.00");
        assertThat(line.getAmount().scale()).isEqualTo(2);
    }

    @Test
    void duplicateSubjectPropagatesUniqueConstraintFailure() {
        BudgetLineEntity line = new BudgetLineEntity();
        line.setBudgetVersionId(VERSION_ID);
        line.setSubjectCode("EQUIPMENT");
        when(budgetLineMapper.insert(line))
                .thenThrow(new DuplicateKeyException("uk_budget_line_subject"));

        assertThatThrownBy(() -> repository.insert(line))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void findByBudgetVersionIdOrdersBySubjectCode() {
        when(budgetLineMapper.selectList(
                ArgumentMatchers.<Wrapper<BudgetLineEntity>>any())).thenReturn(List.of());

        repository.findByBudgetVersionId(VERSION_ID);

        verify(budgetLineMapper).selectList(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment())
                .contains("budget_version_id =")
                .containsIgnoringCase("order by subject_code asc");
    }
}
