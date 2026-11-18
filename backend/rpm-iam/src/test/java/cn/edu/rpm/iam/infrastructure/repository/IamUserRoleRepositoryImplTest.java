package cn.edu.rpm.iam.infrastructure.repository;

import cn.edu.rpm.iam.infrastructure.entity.IamUserRoleEntity;
import cn.edu.rpm.iam.infrastructure.mapper.IamUserRoleMapper;
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

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link IamUserRoleRepositoryImpl} 纯单元测试（T-IAM-004 先行测试）。
 *
 * <p>验收锚点：插入用户-角色关联成功；按 {@code user_id} 查询返回关联列表；
 * 有效期过滤正确（valid_from &lt;= 查询时点 &lt; valid_to，valid_to 为空视为长期有效，
 * 且状态为 ACTIVE）。</p>
 */
@ExtendWith(MockitoExtension.class)
class IamUserRoleRepositoryImplTest {

    private static final String USER_ID = "0123456789abcdef0123456789abcdef";
    private static final String ROLE_PI = "11111111111111111111111111111111";
    private static final String ROLE_SECRETARY = "22222222222222222222222222222222";
    private static final String ORG_ID = "d1111111111111111111111111111111";
    private static final OffsetDateTime AT =
            OffsetDateTime.parse("2026-11-17T10:00:00+08:00");

    @Mock
    private IamUserRoleMapper iamUserRoleMapper;

    @Captor
    private ArgumentCaptor<LambdaQueryWrapper<IamUserRoleEntity>> wrapperCaptor;

    private IamUserRoleRepositoryImpl repository;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, IamUserRoleEntity.class);
    }

    @BeforeEach
    void setUp() {
        repository = new IamUserRoleRepositoryImpl(iamUserRoleMapper);
    }

    private static IamUserRoleEntity newAssociation(String roleId) {
        IamUserRoleEntity entity = new IamUserRoleEntity();
        entity.setUserId(USER_ID);
        entity.setRoleId(roleId);
        entity.setOrgId(ORG_ID);
        entity.setValidFrom(OffsetDateTime.parse("2026-01-01T00:00:00+08:00"));
        entity.setStatus(IamUserRoleEntity.STATUS_ACTIVE);
        return entity;
    }

    @Test
    void insertDelegatesToMapper() {
        IamUserRoleEntity entity = newAssociation(ROLE_PI);
        when(iamUserRoleMapper.insert(same(entity))).thenReturn(1);

        repository.insert(entity);

        verify(iamUserRoleMapper).insert(same(entity));
    }

    @Test
    void findByUserIdReturnsAllAssociations() {
        when(iamUserRoleMapper.selectList(ArgumentMatchers.<Wrapper<IamUserRoleEntity>>any()))
                .thenReturn(List.of(newAssociation(ROLE_PI), newAssociation(ROLE_SECRETARY)));

        List<IamUserRoleEntity> result = repository.findByUserId(USER_ID);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getRoleId()).isEqualTo(ROLE_PI);
        assertThat(result.get(1).getRoleId()).isEqualTo(ROLE_SECRETARY);
        assertThat(result.get(0).getOrgId()).isEqualTo(ORG_ID);
        verify(iamUserRoleMapper).selectList(wrapperCaptor.capture());
        LambdaQueryWrapper<IamUserRoleEntity> wrapper = wrapperCaptor.getValue();
        assertThat(wrapper.getSqlSegment()).contains("user_id =");
        assertThat(wrapper.getParamNameValuePairs().values()).contains(USER_ID);
    }

    @Test
    void findByUserIdReturnsEmptyListWhenNoAssociation() {
        when(iamUserRoleMapper.selectList(ArgumentMatchers.<Wrapper<IamUserRoleEntity>>any()))
                .thenReturn(List.of());

        assertThat(repository.findByUserId("ghost-user")).isEmpty();
    }

    @Test
    void findValidByUserIdFiltersStatusAndValidityWindow() {
        when(iamUserRoleMapper.selectList(ArgumentMatchers.<Wrapper<IamUserRoleEntity>>any()))
                .thenReturn(List.of(newAssociation(ROLE_PI)));

        List<IamUserRoleEntity> result = repository.findValidByUserId(USER_ID, AT);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRoleId()).isEqualTo(ROLE_PI);
        verify(iamUserRoleMapper).selectList(wrapperCaptor.capture());
        LambdaQueryWrapper<IamUserRoleEntity> wrapper = wrapperCaptor.getValue();
        String sql = wrapper.getSqlSegment();
        assertThat(sql).contains("user_id =");
        assertThat(sql).contains("status =");
        assertThat(sql).contains("valid_from <=");
        assertThat(sql).contains("valid_to IS NULL");
        assertThat(sql).contains("valid_to >");
        assertThat(wrapper.getParamNameValuePairs().values())
                .contains(USER_ID, IamUserRoleEntity.STATUS_ACTIVE, AT);
    }

    @Test
    void findValidByUserIdReturnsEmptyListWhenNothingValidAtTime() {
        when(iamUserRoleMapper.selectList(ArgumentMatchers.<Wrapper<IamUserRoleEntity>>any()))
                .thenReturn(List.of());

        assertThat(repository.findValidByUserId(USER_ID,
                OffsetDateTime.parse("2030-01-01T00:00:00+08:00"))).isEmpty();
    }
}
