package cn.edu.rpm.iam.infrastructure.repository;

import cn.edu.rpm.iam.infrastructure.entity.IamRoleEntity;
import cn.edu.rpm.iam.infrastructure.mapper.IamRoleMapper;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link IamRoleRepositoryImpl} 纯单元测试（T-IAM-002 先行测试）。
 *
 * <p>验收锚点：插入角色成功；按 {@code code} 查询（uk_iam_role_code 唯一）；
 * 乐观锁更新返回受影响行数。</p>
 */
@ExtendWith(MockitoExtension.class)
class IamRoleRepositoryImplTest {

    private static final String ROLE_ID = "11111111111111111111111111111111";
    private static final String ROLE_CODE = "RPI";
    private static final String ROLE_NAME = "项目负责人";

    @Mock
    private IamRoleMapper iamRoleMapper;

    @Captor
    private ArgumentCaptor<LambdaQueryWrapper<IamRoleEntity>> wrapperCaptor;

    private IamRoleRepositoryImpl repository;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, IamRoleEntity.class);
    }

    @BeforeEach
    void setUp() {
        repository = new IamRoleRepositoryImpl(iamRoleMapper);
    }

    private static IamRoleEntity newRole() {
        IamRoleEntity role = new IamRoleEntity();
        role.setCode(ROLE_CODE);
        role.setName(ROLE_NAME);
        role.setScopeType("PROJECT");
        role.setStatus("ACTIVE");
        return role;
    }

    @Test
    void insertDelegatesToMapper() {
        IamRoleEntity role = newRole();
        when(iamRoleMapper.insert(same(role))).thenReturn(1);

        repository.insert(role);

        verify(iamRoleMapper).insert(same(role));
    }

    @Test
    void findByIdReturnsRole() {
        IamRoleEntity role = newRole();
        role.setId(ROLE_ID);
        when(iamRoleMapper.selectById(ROLE_ID)).thenReturn(role);

        Optional<IamRoleEntity> result = repository.findById(ROLE_ID);

        assertThat(result).isPresent();
        assertThat(result.get().getCode()).isEqualTo(ROLE_CODE);
        assertThat(result.get().getName()).isEqualTo(ROLE_NAME);
        assertThat(result.get().getScopeType()).isEqualTo("PROJECT");
    }

    @Test
    void findByIdReturnsEmptyWhenRoleMissing() {
        when(iamRoleMapper.selectById("no-such-id")).thenReturn(null);

        assertThat(repository.findById("no-such-id")).isEmpty();
    }

    @Test
    void findByCodeFiltersCodeColumn() {
        when(iamRoleMapper.selectOne(ArgumentMatchers.<Wrapper<IamRoleEntity>>any()))
                .thenReturn(newRole());

        Optional<IamRoleEntity> result = repository.findByCode(ROLE_CODE);

        assertThat(result).isPresent();
        assertThat(result.get().getCode()).isEqualTo(ROLE_CODE);
        verify(iamRoleMapper).selectOne(wrapperCaptor.capture());
        LambdaQueryWrapper<IamRoleEntity> wrapper = wrapperCaptor.getValue();
        assertThat(wrapper.getSqlSegment()).contains("code =");
        assertThat(wrapper.getParamNameValuePairs().values()).contains(ROLE_CODE);
    }

    @Test
    void findByCodeReturnsEmptyWhenNotFound() {
        when(iamRoleMapper.selectOne(ArgumentMatchers.<Wrapper<IamRoleEntity>>any())).thenReturn(null);

        assertThat(repository.findByCode("ghost-role")).isEmpty();
    }

    @Test
    void findByIdsUsesBatchQueryAndReturnsRoles() {
        IamRoleEntity role = newRole();
        role.setId(ROLE_ID);
        when(iamRoleMapper.selectBatchIds(List.of(ROLE_ID))).thenReturn(List.of(role));

        List<IamRoleEntity> roles = repository.findByIds(List.of(ROLE_ID));

        assertThat(roles).hasSize(1);
        assertThat(roles.get(0).getCode()).isEqualTo(ROLE_CODE);
        assertThat(roles.get(0).getScopeType()).isEqualTo("PROJECT");
        verify(iamRoleMapper).selectBatchIds(List.of(ROLE_ID));
    }

    @Test
    void findByIdsReturnsEmptyWithoutQueryForEmptyInput() {
        assertThat(repository.findByIds(List.of())).isEmpty();
        verifyNoInteractions(iamRoleMapper);
    }

    @Test
    void updateReturnsAffectedRows() {
        IamRoleEntity role = newRole();
        role.setId(ROLE_ID);
        role.setVersion(1);
        when(iamRoleMapper.updateById(same(role))).thenReturn(1);

        assertThat(repository.update(role)).isEqualTo(1);
    }

    @Test
    void updateReturnsZeroOnOptimisticLockConflict() {
        IamRoleEntity role = newRole();
        role.setId(ROLE_ID);
        role.setVersion(1);
        when(iamRoleMapper.updateById(same(role))).thenReturn(0);

        assertThat(repository.update(role)).isZero();
    }
}
