package cn.edu.rpm.iam.infrastructure.adapter;

import cn.edu.rpm.iam.infrastructure.entity.IamPermissionEntity;
import cn.edu.rpm.iam.infrastructure.repository.IamPermissionRepository;
import cn.edu.rpm.iam.infrastructure.repository.IamRolePermissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link RolePermissionQueryAdapter} 纯单元测试（Batch-4 / T-IAM-008）。
 *
 * <p>锚点：两步装配（角色-权限点 → 权限码），未绑定权限时不再查权限字典（RULE-DB2）。</p>
 */
@ExtendWith(MockitoExtension.class)
class RolePermissionQueryAdapterTest {

    private static final String ROLE_ID = "11111111111111111111111111111111";
    private static final String PERMISSION_VIEW = "22222222222222222222222222222221";
    private static final String PERMISSION_SUBMIT = "22222222222222222222222222222222";

    @Mock
    private IamRolePermissionRepository rolePermissionRepository;

    @Mock
    private IamPermissionRepository permissionRepository;

    private RolePermissionQueryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new RolePermissionQueryAdapter(rolePermissionRepository, permissionRepository);
    }

    @Test
    void assemblesPermissionCodesFromBindingsAndDictionary() {
        when(rolePermissionRepository.findPermissionIdsByRoleId(ROLE_ID))
                .thenReturn(List.of(PERMISSION_VIEW, PERMISSION_SUBMIT));
        IamPermissionEntity view = permission(PERMISSION_VIEW, "project:view");
        IamPermissionEntity submit = permission(PERMISSION_SUBMIT, "fund:expense:submit");
        when(permissionRepository.findByIds(List.of(PERMISSION_VIEW, PERMISSION_SUBMIT)))
                .thenReturn(List.of(view, submit));

        List<String> codes = adapter.findPermissionCodesByRoleId(ROLE_ID);

        assertThat(codes).containsExactlyInAnyOrder("project:view", "fund:expense:submit");
        verify(permissionRepository).findByIds(List.of(PERMISSION_VIEW, PERMISSION_SUBMIT));
    }

    @Test
    void returnsEmptyWithoutPermissionLookupWhenNoBinding() {
        when(rolePermissionRepository.findPermissionIdsByRoleId(ROLE_ID)).thenReturn(List.of());

        assertThat(adapter.findPermissionCodesByRoleId(ROLE_ID)).isEmpty();
        verify(permissionRepository, never()).findByIds(any());
    }

    private static IamPermissionEntity permission(String id, String code) {
        IamPermissionEntity entity = new IamPermissionEntity();
        entity.setId(id);
        entity.setCode(code);
        entity.setModule("project");
        return entity;
    }
}
