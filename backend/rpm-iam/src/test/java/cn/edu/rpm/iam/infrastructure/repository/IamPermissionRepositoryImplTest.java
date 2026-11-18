package cn.edu.rpm.iam.infrastructure.repository;

import cn.edu.rpm.iam.infrastructure.entity.IamPermissionEntity;
import cn.edu.rpm.iam.infrastructure.mapper.IamPermissionMapper;
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
 * {@link IamPermissionRepositoryImpl} 纯单元测试（T-IAM-003 先行测试）。
 *
 * <p>验收锚点：插入权限点成功；按 {@code code} 查询（uk_iam_permission_code 唯一）；
 * 按模块筛选权限点。注意 iam_permission 为字典表，仅插入、无更新语义。</p>
 */
@ExtendWith(MockitoExtension.class)
class IamPermissionRepositoryImplTest {

    private static final String PERMISSION_ID = "22222222222222222222222222222222";
    private static final String PERMISSION_CODE = "project:view";
    private static final String MODULE = "project";

    @Mock
    private IamPermissionMapper iamPermissionMapper;

    @Captor
    private ArgumentCaptor<LambdaQueryWrapper<IamPermissionEntity>> wrapperCaptor;

    private IamPermissionRepositoryImpl repository;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, IamPermissionEntity.class);
    }

    @BeforeEach
    void setUp() {
        repository = new IamPermissionRepositoryImpl(iamPermissionMapper);
    }

    private static IamPermissionEntity newPermission() {
        IamPermissionEntity permission = new IamPermissionEntity();
        permission.setCode(PERMISSION_CODE);
        permission.setName("项目查看");
        permission.setModule(MODULE);
        return permission;
    }

    @Test
    void insertDelegatesToMapper() {
        IamPermissionEntity permission = newPermission();
        when(iamPermissionMapper.insert(same(permission))).thenReturn(1);

        repository.insert(permission);

        verify(iamPermissionMapper).insert(same(permission));
    }

    @Test
    void findByCodeFiltersCodeColumn() {
        IamPermissionEntity permission = newPermission();
        permission.setId(PERMISSION_ID);
        when(iamPermissionMapper.selectOne(ArgumentMatchers.<Wrapper<IamPermissionEntity>>any()))
                .thenReturn(permission);

        Optional<IamPermissionEntity> result = repository.findByCode(PERMISSION_CODE);

        assertThat(result).isPresent();
        assertThat(result.get().getCode()).isEqualTo(PERMISSION_CODE);
        assertThat(result.get().getModule()).isEqualTo(MODULE);
        verify(iamPermissionMapper).selectOne(wrapperCaptor.capture());
        LambdaQueryWrapper<IamPermissionEntity> wrapper = wrapperCaptor.getValue();
        assertThat(wrapper.getSqlSegment()).contains("code =");
        assertThat(wrapper.getParamNameValuePairs().values()).contains(PERMISSION_CODE);
    }

    @Test
    void findByCodeReturnsEmptyWhenNotFound() {
        when(iamPermissionMapper.selectOne(ArgumentMatchers.<Wrapper<IamPermissionEntity>>any()))
                .thenReturn(null);

        assertThat(repository.findByCode("ghost:perm")).isEmpty();
    }

    @Test
    void findByModuleFiltersModuleColumn() {
        IamPermissionEntity permission = newPermission();
        permission.setId(PERMISSION_ID);
        List<IamPermissionEntity> permissions = List.of(permission);
        when(iamPermissionMapper.selectList(ArgumentMatchers.<Wrapper<IamPermissionEntity>>any()))
                .thenReturn(permissions);

        List<IamPermissionEntity> result = repository.findByModule(MODULE);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCode()).isEqualTo(PERMISSION_CODE);
        assertThat(result.get(0).getModule()).isEqualTo(MODULE);
        verify(iamPermissionMapper).selectList(wrapperCaptor.capture());
        LambdaQueryWrapper<IamPermissionEntity> wrapper = wrapperCaptor.getValue();
        assertThat(wrapper.getSqlSegment()).contains("module =");
        assertThat(wrapper.getParamNameValuePairs().values()).contains(MODULE);
    }

    @Test
    void findByModuleReturnsEmptyListWhenNoPermissionInModule() {
        when(iamPermissionMapper.selectList(ArgumentMatchers.<Wrapper<IamPermissionEntity>>any()))
                .thenReturn(List.of());

        assertThat(repository.findByModule("unknown-module")).isEmpty();
    }

    @Test
    void findByIdsUsesBatchQueryAndReturnsPermissions() {
        IamPermissionEntity permission = newPermission();
        permission.setId(PERMISSION_ID);
        when(iamPermissionMapper.selectBatchIds(List.of(PERMISSION_ID))).thenReturn(List.of(permission));

        List<IamPermissionEntity> permissions = repository.findByIds(List.of(PERMISSION_ID));

        assertThat(permissions).hasSize(1);
        assertThat(permissions.get(0).getCode()).isEqualTo(PERMISSION_CODE);
        assertThat(permissions.get(0).getModule()).isEqualTo(MODULE);
        verify(iamPermissionMapper).selectBatchIds(List.of(PERMISSION_ID));
    }

    @Test
    void findByIdsReturnsEmptyWithoutQueryForEmptyInput() {
        assertThat(repository.findByIds(List.of())).isEmpty();
        verifyNoInteractions(iamPermissionMapper);
    }
}
