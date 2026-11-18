package cn.edu.rpm.funding.infrastructure.repository;

import cn.edu.rpm.funding.infrastructure.entity.BudgetVersionEntity;
import cn.edu.rpm.funding.infrastructure.mapper.BudgetVersionMapper;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link BudgetVersionRepositoryImpl} 纯单元测试（T-FUND-001）。
 *
 * <p>覆盖：插入预算版本保存来源/控制级别/生效区间；{@code uk_budget_version_no}
 * 冲突异常向上抛出；按项目查询生效版本。</p>
 */
@ExtendWith(MockitoExtension.class)
class BudgetVersionRepositoryImplTest {

    private static final String PROJECT_ID = "8d7c6b5a4e3f4a2b9c1d0e2f3a4b5c6d";
    private static final String VERSION_ID = "11111111111111111111111111111111";

    @Mock
    private BudgetVersionMapper budgetVersionMapper;

    @Captor
    private ArgumentCaptor<LambdaQueryWrapper<BudgetVersionEntity>> wrapperCaptor;

    private BudgetVersionRepositoryImpl repository;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, BudgetVersionEntity.class);
    }

    @BeforeEach
    void setUp() {
        repository = new BudgetVersionRepositoryImpl(budgetVersionMapper);
    }

    @Test
    void insertPersistsSourceControlLevelAndEffectivePeriod() {
        BudgetVersionEntity version = new BudgetVersionEntity();
        version.setId(VERSION_ID);
        version.setProjectId(PROJECT_ID);
        version.setVersionNo(1);
        version.setTotalAmount(new BigDecimal("1200000.00"));
        version.setSourceType(BudgetVersionEntity.SOURCE_GRANT);
        version.setControlLevel(BudgetVersionEntity.CONTROL_BLOCK);
        version.setStatus(BudgetVersionEntity.STATUS_DRAFT);
        version.setEffectiveFrom(LocalDate.of(2026, 1, 1));
        version.setEffectiveTo(LocalDate.of(2028, 12, 31));

        repository.insert(version);

        verify(budgetVersionMapper).insert(version);
        assertThat(version.getTotalAmount()).isEqualByComparingTo("1200000.00");
        assertThat(version.getSourceType()).isEqualTo("GRANT");
        assertThat(version.getControlLevel()).isEqualTo("BLOCK");
        assertThat(version.getEffectiveTo()).isEqualTo(LocalDate.of(2028, 12, 31));
    }

    @Test
    void duplicateVersionNoPropagatesUniqueConstraintFailure() {
        BudgetVersionEntity version = new BudgetVersionEntity();
        version.setId(VERSION_ID);
        version.setProjectId(PROJECT_ID);
        version.setVersionNo(1);
        when(budgetVersionMapper.insert(version))
                .thenThrow(new DuplicateKeyException("uk_budget_version_no"));

        assertThatThrownBy(() -> repository.insert(version))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void findEffectiveByProjectIdQueriesOnlyEffectiveStatus() {
        BudgetVersionEntity effective = new BudgetVersionEntity();
        effective.setId(VERSION_ID);
        effective.setStatus(BudgetVersionEntity.STATUS_EFFECTIVE);
        when(budgetVersionMapper.selectOne(
                ArgumentMatchers.<Wrapper<BudgetVersionEntity>>any())).thenReturn(effective);

        Optional<BudgetVersionEntity> result = repository.findEffectiveByProjectId(PROJECT_ID);

        assertThat(result).containsSame(effective);
        verify(budgetVersionMapper).selectOne(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment())
                .contains("project_id =", "status =");
        assertThat(wrapperCaptor.getValue().getParamNameValuePairs().values())
                .contains(BudgetVersionEntity.STATUS_EFFECTIVE);
    }

    @Test
    void findByProjectIdAndStatusOrdersByVersionNoDesc() {
        when(budgetVersionMapper.selectList(
                ArgumentMatchers.<Wrapper<BudgetVersionEntity>>any())).thenReturn(List.of());

        repository.findByProjectIdAndStatus(PROJECT_ID, BudgetVersionEntity.STATUS_DRAFT);

        verify(budgetVersionMapper).selectList(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment())
                .contains("project_id =", "status =")
                .containsIgnoringCase("order by version_no desc");
    }
}
