package cn.edu.rpm.funding.infrastructure.repository;

import cn.edu.rpm.funding.infrastructure.entity.ExpenseRequestVersionEntity;
import cn.edu.rpm.funding.infrastructure.mapper.ExpenseRequestVersionMapper;
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

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ExpenseRequestVersionRepositoryImpl} 纯单元测试（T-FUND-005）。
 *
 * <p>覆盖：追加版本保存版本号/载荷/内容哈希；{@code uk_expense_version_no} 冲突；
 * 按支出查询版本号降序；接口不暴露更新/删除方法（版本不可变）。</p>
 */
@ExtendWith(MockitoExtension.class)
class ExpenseRequestVersionRepositoryImplTest {

    private static final String EXPENSE_ID = "22222222222222222222222222222222";
    private static final String VERSION_ID = "55555555555555555555555555555555";
    private static final String CONTENT_HASH =
            "9f2c1a7e3b4d5c6e7f8091a2b3c4d5e6f708192a3b4c5d6e7f8091a2b3c4d5e6";

    @Mock
    private ExpenseRequestVersionMapper expenseRequestVersionMapper;

    @Captor
    private ArgumentCaptor<LambdaQueryWrapper<ExpenseRequestVersionEntity>> wrapperCaptor;

    private ExpenseRequestVersionRepositoryImpl repository;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ExpenseRequestVersionEntity.class);
    }

    @BeforeEach
    void setUp() {
        repository = new ExpenseRequestVersionRepositoryImpl(expenseRequestVersionMapper);
    }

    @Test
    void insertPersistsVersionNoPayloadAndContentHash() {
        ExpenseRequestVersionEntity version = version(1);

        repository.insert(version);

        verify(expenseRequestVersionMapper).insert(version);
        assertThat(version.getVersionNo()).isEqualTo(1);
        assertThat(version.getContentHash()).isEqualTo(CONTENT_HASH);
        assertThat(version.getPayload()).contains("\"amount\":12800.00");
    }

    @Test
    void duplicateVersionNoPropagatesUniqueConstraintFailure() {
        ExpenseRequestVersionEntity version = version(1);
        when(expenseRequestVersionMapper.insert(version))
                .thenThrow(new DuplicateKeyException("uk_expense_version_no"));

        assertThatThrownBy(() -> repository.insert(version))
                .isInstanceOf(DuplicateKeyException.class)
                .hasMessageContaining("uk_expense_version_no");
    }

    @Test
    void findByExpenseRequestIdAndVersionNoQueriesCompositeKey() {
        ExpenseRequestVersionEntity version = version(2);
        when(expenseRequestVersionMapper.selectOne(
                ArgumentMatchers.<Wrapper<ExpenseRequestVersionEntity>>any())).thenReturn(version);

        Optional<ExpenseRequestVersionEntity> result =
                repository.findByExpenseRequestIdAndVersionNo(EXPENSE_ID, 2);

        assertThat(result).containsSame(version);
        verify(expenseRequestVersionMapper).selectOne(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment())
                .contains("expense_request_id =", "version_no =");
        assertThat(wrapperCaptor.getValue().getParamNameValuePairs().values())
                .contains(EXPENSE_ID, 2);
    }

    @Test
    void findByExpenseRequestIdOrdersByVersionNoDesc() {
        ExpenseRequestVersionEntity newer = version(3);
        ExpenseRequestVersionEntity older = version(2);
        when(expenseRequestVersionMapper.selectList(
                ArgumentMatchers.<Wrapper<ExpenseRequestVersionEntity>>any()))
                .thenReturn(Arrays.asList(newer, older));

        List<ExpenseRequestVersionEntity> result = repository.findByExpenseRequestId(EXPENSE_ID);

        assertThat(result).extracting(ExpenseRequestVersionEntity::getVersionNo).containsExactly(3, 2);
        verify(expenseRequestVersionMapper).selectList(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment())
                .contains("expense_request_id =")
                .containsIgnoringCase("order by version_no desc");
    }

    @Test
    void interfaceExposesNoUpdateOrDeleteMethods() {
        assertThat(ExpenseRequestVersionRepository.class.getMethods())
                .extracting(java.lang.reflect.Method::getName)
                .doesNotContain("update", "delete", "deleteById");
    }

    private ExpenseRequestVersionEntity version(int versionNo) {
        ExpenseRequestVersionEntity version = new ExpenseRequestVersionEntity();
        version.setId(VERSION_ID);
        version.setExpenseRequestId(EXPENSE_ID);
        version.setVersionNo(versionNo);
        version.setPayload("{\"amount\":12800.00,\"purpose\":\"差旅费\"}");
        version.setContentHash(CONTENT_HASH);
        version.setSubmittedBy("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        return version;
    }
}
