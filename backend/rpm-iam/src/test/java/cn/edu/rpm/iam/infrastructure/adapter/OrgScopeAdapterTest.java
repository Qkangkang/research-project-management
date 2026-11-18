package cn.edu.rpm.iam.infrastructure.adapter;

import cn.edu.rpm.organization.infrastructure.entity.OrgMembershipEntity;
import cn.edu.rpm.organization.infrastructure.entity.OrgUnitEntity;
import cn.edu.rpm.organization.infrastructure.repository.OrgMembershipRepository;
import cn.edu.rpm.organization.infrastructure.repository.OrgUnitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link OrgScopeAdapter} 纯单元测试（T-IAM-009 组织端口先行测试，技术文档 03 §2.2）。
 *
 * <p>锚点：主部门取 {@code org_membership.is_primary} 且状态 {@code ACTIVE}；
 * 部门树以 {@code org_unit.path} 批量前缀查询展开，只返回 {@code ACTIVE} 节点；
 * 空输入不触发任何数据库访问（RULE-DB2）。</p>
 */
@ExtendWith(MockitoExtension.class)
class OrgScopeAdapterTest {

    private static final UUID USER_ID = UUID.fromString("01234567-89ab-cdef-0123-456789abcdef");
    private static final String USER_ID_TEXT = "0123456789abcdef0123456789abcdef";
    private static final String DEPT_ROOT = "d1111111111111111111111111111111";
    private static final UUID DEPT_ROOT_UUID = UUID.fromString("d1111111-1111-1111-1111-111111111111");
    private static final String DEPT_CHILD = "d1111111111111111111111111111112";
    private static final UUID DEPT_CHILD_UUID = UUID.fromString("d1111111-1111-1111-1111-111111111112");
    private static final String DEPT_INACTIVE = "d1111111111111111111111111111113";
    private static final String ROOT_PATH = "/UNIV/CSE";

    @Mock
    private OrgMembershipRepository membershipRepository;

    @Mock
    private OrgUnitRepository orgUnitRepository;

    private OrgScopeAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new OrgScopeAdapter(membershipRepository, orgUnitRepository);
    }

    private static OrgMembershipEntity membership(String orgId, String status) {
        OrgMembershipEntity entity = new OrgMembershipEntity();
        entity.setUserId(USER_ID_TEXT);
        entity.setOrgId(orgId);
        entity.setIsPrimary(Boolean.TRUE);
        entity.setStatus(status);
        return entity;
    }

    private static OrgUnitEntity unit(String id, String path, String status) {
        OrgUnitEntity entity = new OrgUnitEntity();
        entity.setId(id);
        entity.setCode(id.substring(0, 4));
        entity.setName("组织-" + id.substring(0, 4));
        entity.setUnitType("DEPARTMENT");
        entity.setPath(path);
        entity.setStatus(status);
        return entity;
    }

    @Test
    void findsPrimaryDeptForUser() {
        when(membershipRepository.findPrimaryByUserId(USER_ID_TEXT))
                .thenReturn(Optional.of(membership(DEPT_ROOT, "ACTIVE")));

        assertThat(adapter.findPrimaryDeptIds(USER_ID)).containsExactly(DEPT_ROOT_UUID);
    }

    @Test
    void ignoresInactivePrimaryMembership() {
        when(membershipRepository.findPrimaryByUserId(USER_ID_TEXT))
                .thenReturn(Optional.of(membership(DEPT_ROOT, "INACTIVE")));

        assertThat(adapter.findPrimaryDeptIds(USER_ID)).isEmpty();
    }

    @Test
    void returnsEmptyWhenNoPrimaryMembership() {
        when(membershipRepository.findPrimaryByUserId(USER_ID_TEXT)).thenReturn(Optional.empty());

        assertThat(adapter.findPrimaryDeptIds(USER_ID)).isEmpty();
    }

    @Test
    void expandsSubtreeAndKeepsOnlyActiveNodes() {
        when(orgUnitRepository.findByIds(List.of(DEPT_ROOT)))
                .thenReturn(List.of(unit(DEPT_ROOT, ROOT_PATH, "ACTIVE")));
        when(orgUnitRepository.findSubtreeByPaths(List.of(ROOT_PATH)))
                .thenReturn(List.of(
                        unit(DEPT_ROOT, ROOT_PATH, "ACTIVE"),
                        unit(DEPT_CHILD, ROOT_PATH + "/CSE01", "ACTIVE"),
                        unit(DEPT_INACTIVE, ROOT_PATH + "/CSE02", "INACTIVE")));

        Set<UUID> depts = adapter.expandSubtrees(Set.of(DEPT_ROOT_UUID));

        assertThat(depts).containsExactlyInAnyOrder(DEPT_ROOT_UUID, DEPT_CHILD_UUID);
    }

    @Test
    void emptyInputDoesNotTouchDatabase() {
        assertThat(adapter.expandSubtrees(Set.of())).isEmpty();
        verifyNoInteractions(orgUnitRepository);
        verifyNoInteractions(membershipRepository);
    }

    @Test
    void unknownDeptProducesEmptyResultWithoutSubtreeQuery() {
        when(orgUnitRepository.findByIds(any())).thenReturn(List.of());

        assertThat(adapter.expandSubtrees(Set.of(DEPT_ROOT_UUID))).isEmpty();
        verify(orgUnitRepository, never()).findSubtreeByPaths(any());
    }
}
