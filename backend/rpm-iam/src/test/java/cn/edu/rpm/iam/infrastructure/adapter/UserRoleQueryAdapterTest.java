package cn.edu.rpm.iam.infrastructure.adapter;

import cn.edu.rpm.iam.domain.model.RoleAssignmentView;
import cn.edu.rpm.iam.infrastructure.entity.IamUserRoleEntity;
import cn.edu.rpm.iam.infrastructure.repository.IamUserRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * {@link UserRoleQueryAdapter} 纯单元测试（Batch-4 / T-IAM-008、T-IAM-009）。
 *
 * <p>锚点：只做字段投影（角色 ID、授权部门 ID），不改变数据层有效期过滤口径。</p>
 */
@ExtendWith(MockitoExtension.class)
class UserRoleQueryAdapterTest {

    private static final String USER_ID = "0123456789abcdef0123456789abcdef";
    private static final String ROLE_ID = "11111111111111111111111111111111";
    private static final String ORG_ID = "d1111111111111111111111111111111";
    private static final OffsetDateTime AT = OffsetDateTime.parse("2026-11-17T10:00:00+08:00");

    @Mock
    private IamUserRoleRepository userRoleRepository;

    private UserRoleQueryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new UserRoleQueryAdapter(userRoleRepository);
    }

    @Test
    void mapsValidAssignmentsToDomainView() {
        IamUserRoleEntity entity = new IamUserRoleEntity();
        entity.setUserId(USER_ID);
        entity.setRoleId(ROLE_ID);
        entity.setOrgId(ORG_ID);
        entity.setStatus(IamUserRoleEntity.STATUS_ACTIVE);
        when(userRoleRepository.findValidByUserId(USER_ID, AT)).thenReturn(List.of(entity));

        List<RoleAssignmentView> assignments = adapter.findValidAssignments(USER_ID, AT);

        assertThat(assignments).hasSize(1);
        assertThat(assignments.get(0).roleId()).isEqualTo(ROLE_ID);
        assertThat(assignments.get(0).orgId()).isEqualTo(ORG_ID);
    }

    @Test
    void emptyResultMapsToEmptyList() {
        when(userRoleRepository.findValidByUserId(USER_ID, AT)).thenReturn(List.of());

        assertThat(adapter.findValidAssignments(USER_ID, AT)).isEmpty();
    }
}
