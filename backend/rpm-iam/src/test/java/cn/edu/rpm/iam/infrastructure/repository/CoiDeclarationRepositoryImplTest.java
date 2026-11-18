package cn.edu.rpm.iam.infrastructure.repository;

import cn.edu.rpm.iam.infrastructure.entity.CoiDeclarationEntity;
import cn.edu.rpm.iam.infrastructure.mapper.CoiDeclarationMapper;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CoiDeclarationRepositoryImpl} 纯单元测试（T-IAM-006 先行测试）。
 *
 * <p>验收锚点：插入利益冲突声明成功；按业务对象 + 专家查询（idx_coi_biz_expert 三列）；
 * 按专家 ID 查询全部声明。注意 uk_coi_biz_expert 唯一键含 related_user_id 四列，
 * 故按三列查询返回列表。</p>
 */
@ExtendWith(MockitoExtension.class)
class CoiDeclarationRepositoryImplTest {

    private static final String BIZ_TYPE = "REVIEW";
    private static final String BIZ_ID = "55555555555555555555555555555555";
    private static final String EXPERT_ID = "0123456789abcdef0123456789abcdef";
    private static final String RELATED_USER_ID = "66666666666666666666666666666666";

    @Mock
    private CoiDeclarationMapper coiDeclarationMapper;

    @Captor
    private ArgumentCaptor<LambdaQueryWrapper<CoiDeclarationEntity>> wrapperCaptor;

    private CoiDeclarationRepositoryImpl repository;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, CoiDeclarationEntity.class);
    }

    @BeforeEach
    void setUp() {
        repository = new CoiDeclarationRepositoryImpl(coiDeclarationMapper);
    }

    private static CoiDeclarationEntity newDeclaration() {
        CoiDeclarationEntity entity = new CoiDeclarationEntity();
        entity.setBizType(BIZ_TYPE);
        entity.setBizId(BIZ_ID);
        entity.setExpertId(EXPERT_ID);
        entity.setRelatedUserId(RELATED_USER_ID);
        entity.setRelationType("DEPT");
        entity.setStatus("DECLARED");
        return entity;
    }

    @Test
    void insertDelegatesToMapper() {
        CoiDeclarationEntity entity = newDeclaration();
        when(coiDeclarationMapper.insert(same(entity))).thenReturn(1);

        repository.insert(entity);

        verify(coiDeclarationMapper).insert(same(entity));
    }

    @Test
    void findByBizAndExpertFiltersBizTypeBizIdAndExpertColumns() {
        when(coiDeclarationMapper.selectList(ArgumentMatchers.<Wrapper<CoiDeclarationEntity>>any()))
                .thenReturn(List.of(newDeclaration()));

        List<CoiDeclarationEntity> result = repository.findByBizAndExpert(BIZ_TYPE, BIZ_ID, EXPERT_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRelatedUserId()).isEqualTo(RELATED_USER_ID);
        assertThat(result.get(0).getRelationType()).isEqualTo("DEPT");
        verify(coiDeclarationMapper).selectList(wrapperCaptor.capture());
        LambdaQueryWrapper<CoiDeclarationEntity> wrapper = wrapperCaptor.getValue();
        String sql = wrapper.getSqlSegment();
        assertThat(sql).contains("biz_type =");
        assertThat(sql).contains("biz_id =");
        assertThat(sql).contains("expert_id =");
        assertThat(wrapper.getParamNameValuePairs().values()).contains(BIZ_TYPE, BIZ_ID, EXPERT_ID);
    }

    @Test
    void findByBizAndExpertReturnsEmptyListWhenNothingDeclared() {
        when(coiDeclarationMapper.selectList(ArgumentMatchers.<Wrapper<CoiDeclarationEntity>>any()))
                .thenReturn(List.of());

        assertThat(repository.findByBizAndExpert("REVIEW", "no-such-biz", EXPERT_ID)).isEmpty();
    }

    @Test
    void findByExpertIdFiltersExpertColumn() {
        when(coiDeclarationMapper.selectList(ArgumentMatchers.<Wrapper<CoiDeclarationEntity>>any()))
                .thenReturn(List.of(newDeclaration()));

        List<CoiDeclarationEntity> result = repository.findByExpertId(EXPERT_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getExpertId()).isEqualTo(EXPERT_ID);
        assertThat(result.get(0).getBizType()).isEqualTo(BIZ_TYPE);
        verify(coiDeclarationMapper).selectList(wrapperCaptor.capture());
        LambdaQueryWrapper<CoiDeclarationEntity> wrapper = wrapperCaptor.getValue();
        assertThat(wrapper.getSqlSegment()).contains("expert_id =");
        assertThat(wrapper.getParamNameValuePairs().values()).contains(EXPERT_ID);
    }
}
