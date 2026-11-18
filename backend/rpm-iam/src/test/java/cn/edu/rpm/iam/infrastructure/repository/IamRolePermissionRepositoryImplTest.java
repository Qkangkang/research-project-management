package cn.edu.rpm.iam.infrastructure.repository;

import cn.edu.rpm.iam.infrastructure.entity.IamRolePermissionEntity;
import cn.edu.rpm.iam.infrastructure.mapper.IamRolePermissionMapper;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link IamRolePermissionRepositoryImpl} 纯单元测试（T-IAM-008 数据依赖先行测试）。
 *
 * <p>锚点：按 {@code role_id} 查询 {@code iam_role_permission} 并返回权限点 ID 列表；
 * 无绑定返回空列表。查询列与主键前缀 {@code iam_role_permission_pkey(role_id, permission_id)} 对齐。</p>
 */
@ExtendWith(MockitoExtension.class)
class IamRolePermissionRepositoryImplTest {

    private static final String ROLE_ID = "11111111111111111111111111111111";
    private static final String PERMISSION_VIEW = "22222222222222222222222222222221";

    @Mock
    private IamRolePermissionMapper iamRolePermissionMapper;

    @Captor
    private ArgumentCaptor<LambdaQueryWrapper<IamRolePermissionEntity>> wrapperCaptor;

    private IamRolePermissionRepositoryImpl repository;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, IamRolePermissionEntity.class);
    }

    @BeforeEach
    void setUp() {
        repository = new IamRolePermissionRepositoryImpl(iamRolePermissionMapper);
    }

    private static IamRolePermissionEntity binding(String roleId, String permissionId) {
        IamRolePermissionEntity entity = new IamRolePermissionEntity();
        entity.setRoleId(roleId);
        entity.setPermissionId(permissionId);
        return entity;
    }

    @Test
    void findPermissionIdsByRoleIdReturnsBoundPermissionIds() {
        when(iamRolePermissionMapper.selectList(ArgumentMatchers.<Wrapper<IamRolePermissionEntity>>any()))
                .thenReturn(List.of(binding(ROLE_ID, PERMISSION_VIEW)));

        List<String> permissionIds = repository.findPermissionIdsByRoleId(ROLE_ID);

        assertThat(permissionIds).containsExactly(PERMISSION_VIEW);
        verify(iamRolePermissionMapper).selectList(wrapperCaptor.capture());
        LambdaQueryWrapper<IamRolePermissionEntity> wrapper = wrapperCaptor.getValue();
        assertThat(wrapper.getSqlSegment()).contains("role_id =");
        assertThat(wrapper.getParamNameValuePairs().values()).contains(ROLE_ID);
    }

    @Test
    void findPermissionIdsByRoleIdReturnsEmptyWhenNoBinding() {
        when(iamRolePermissionMapper.selectList(ArgumentMatchers.<Wrapper<IamRolePermissionEntity>>any()))
                .thenReturn(List.of());

        assertThat(repository.findPermissionIdsByRoleId("no-such-role")).isEmpty();
    }
}
