package cn.edu.rpm.iam.infrastructure.adapter;

import cn.edu.rpm.iam.domain.model.RoleView;
import cn.edu.rpm.iam.infrastructure.entity.IamRoleEntity;
import cn.edu.rpm.iam.infrastructure.repository.IamRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link RoleQueryAdapter} 纯单元测试（Batch-4 / T-IAM-009）。
 *
 * <p>锚点：一次批量查询投影角色码与授权范围类型；空入参不触库（RULE-DB2）。</p>
 */
@ExtendWith(MockitoExtension.class)
class RoleQueryAdapterTest {

    private static final String ROLE_ID = "11111111111111111111111111111111";

    @Mock
    private IamRoleRepository roleRepository;

    private RoleQueryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new RoleQueryAdapter(roleRepository);
    }

    @Test
    void mapsRolesToDomainViews() {
        IamRoleEntity entity = new IamRoleEntity();
        entity.setId(ROLE_ID);
        entity.setCode("DEPT_SECRETARY");
        entity.setScopeType("DEPT_TREE");
        when(roleRepository.findByIds(List.of(ROLE_ID))).thenReturn(List.of(entity));

        List<RoleView> roles = adapter.findByIds(List.of(ROLE_ID));

        assertThat(roles).hasSize(1);
        assertThat(roles.get(0).id()).isEqualTo(ROLE_ID);
        assertThat(roles.get(0).code()).isEqualTo("DEPT_SECRETARY");
        assertThat(roles.get(0).scopeType()).isEqualTo("DEPT_TREE");
    }

    @Test
    void emptyInputDoesNotTouchRepository() {
        assertThat(adapter.findByIds(List.of())).isEmpty();
        verifyNoInteractions(roleRepository);
    }
}
